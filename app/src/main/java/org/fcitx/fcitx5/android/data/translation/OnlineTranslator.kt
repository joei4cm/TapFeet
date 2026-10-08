/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 联网整句翻译（可选）。优先 MyMemory（免 key、国内可达性较好），失败再试 Google gtx。
 * 结果带进程内 LRU 缓存；调用方负责防抖。
 */
object OnlineTranslator {

    enum class Lang(val code: String, val label: String) {
        Zh("zh-CN", "中"),
        En("en", "英"),
        Ja("ja", "日"),
    }

    data class Hit(val lang: Lang, val text: String)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private const val CACHE_MAX = 64
    private val cache = object : LinkedHashMap<String, String>(CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > CACHE_MAX
    }

    /** 供单测注入解析结果，不发网络。 */
    @Volatile
    internal var fetchOverride: (suspend (String, Lang) -> String?)? = null

    fun clearCacheForTest() {
        synchronized(cache) { cache.clear() }
    }

    fun detectLang(text: String): Lang {
        if (text.any { it in '\u3040'..'\u30ff' }) return Lang.Ja
        if (text.any { it in '\u4e00'..'\u9fff' }) return Lang.Zh
        if (text.isNotEmpty() && text.all { it.code < 128 }) return Lang.En
        return Lang.Zh
    }

    fun targetsFor(source: Lang): List<Lang> = when (source) {
        Lang.Zh -> listOf(Lang.En, Lang.Ja)
        Lang.Ja -> listOf(Lang.En, Lang.Zh)
        Lang.En -> listOf(Lang.Zh, Lang.Ja)
    }

    /**
     * 把 [text] 译成另外两种语言。空串 / 过长 / 失败时跳过对应目标。
     */
    suspend fun translateOthers(text: String): List<Hit> {
        val src = text.trim()
        if (src.isEmpty() || src.length > 200) return emptyList()
        val from = detectLang(src)
        val out = ArrayList<Hit>(2)
        for (to in targetsFor(from)) {
            val translated = translate(src, from, to) ?: continue
            val t = translated.trim()
            if (t.isEmpty() || t.equals(src, ignoreCase = true)) continue
            out += Hit(to, t)
        }
        return out
    }

    suspend fun translate(text: String, from: Lang, to: Lang): String? {
        if (from == to) return text
        val key = "${from.code}|${to.code}|$text"
        synchronized(cache) { cache[key] }?.let { return it }
        val override = fetchOverride
        val result = if (override != null) {
            override(text, to)
        } else {
            withContext(Dispatchers.IO) {
                translateMyMemory(text, from, to) ?: translateGoogle(text, from, to)
            }
        }
        if (!result.isNullOrBlank()) {
            synchronized(cache) { cache[key] = result }
        }
        return result
    }

    private fun translateMyMemory(text: String, from: Lang, to: Lang): String? {
        val q = URLEncoder.encode(text.take(400), Charsets.UTF_8.name())
        val pair = URLEncoder.encode("${from.code}|${to.code}", Charsets.UTF_8.name())
        val url =
            "https://api.mymemory.translated.net/get?q=$q&langpair=$pair&de=tapfeet-ime%40users.noreply.github.com"
        return runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                parseMyMemory(body)
            }
        }.onFailure { Timber.w(it, "MyMemory translate failed") }.getOrNull()
    }

    private fun translateGoogle(text: String, from: Lang, to: Lang): String? {
        val q = URLEncoder.encode(text, Charsets.UTF_8.name())
        val url =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=${from.code}&tl=${to.code}&dt=t&q=$q"
        return runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                parseGoogle(resp.body?.string().orEmpty())
            }
        }.onFailure { Timber.w(it, "Google gtx translate failed") }.getOrNull()
    }

    internal fun parseMyMemory(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val status = root["responseStatus"]?.jsonPrimitive?.intOrNull ?: return null
        if (status != 200) return null
        val text = root["responseData"]?.jsonObject
            ?.get("translatedText")
            ?.jsonPrimitive
            ?.content
            ?.trim()
            .orEmpty()
        // MyMemory 偶发把原文原样塞回，或返回 WARNING 串
        if (text.isEmpty() || text.startsWith("MYMEMORY WARNING", ignoreCase = true)) return null
        return text
    }

    internal fun parseGoogle(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return null
        val sentences = root.getOrNull(0)?.jsonArray ?: return null
        val out = buildString {
            for (s in sentences) {
                val piece = s.jsonArray.getOrNull(0)?.jsonPrimitive?.content ?: continue
                append(piece)
            }
        }.trim()
        return out.ifEmpty { null }
    }
}
