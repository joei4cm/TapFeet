/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.data.translation

import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.fcitx.fcitx5.android.R
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 联网整句翻译（可选）。优先 Google gtx（质量更好），失败再试 MyMemory。
 * 目标语种由设置里的多选决定；结果带进程内 LRU 缓存；调用方负责防抖。
 *
 * 机器翻译无法保证句句完美，这里用引擎优先级 + 结果合理性校验尽量提高可用质量。
 */
object OnlineTranslator {

    enum class Lang(
        val code: String,
        /** 芯片上的短标签 */
        val label: String,
        @StringRes val nameRes: Int,
    ) {
        Zh("zh-CN", "中", R.string.translate_lang_zh),
        En("en", "英", R.string.translate_lang_en),
        Ja("ja", "日", R.string.translate_lang_ja),
        Ko("ko", "韩", R.string.translate_lang_ko),
        Fr("fr", "法", R.string.translate_lang_fr),
        De("de", "德", R.string.translate_lang_de),
        Es("es", "西", R.string.translate_lang_es),
        Ru("ru", "俄", R.string.translate_lang_ru),
        Pt("pt", "葡", R.string.translate_lang_pt),
        It("it", "意", R.string.translate_lang_it),
        Vi("vi", "越", R.string.translate_lang_vi),
        Th("th", "泰", R.string.translate_lang_th),
        Ar("ar", "阿", R.string.translate_lang_ar),
        Hi("hi", "印", R.string.translate_lang_hi),
        Id("id", "印尼", R.string.translate_lang_id),
        Tr("tr", "土", R.string.translate_lang_tr),
        Nl("nl", "荷", R.string.translate_lang_nl),
        Pl("pl", "波", R.string.translate_lang_pl),
        Uk("uk", "乌", R.string.translate_lang_uk),
        Ms("ms", "马", R.string.translate_lang_ms);

        companion object {
            fun fromCode(code: String): Lang? {
                val c = code.trim()
                if (c.isEmpty()) return null
                return entries.firstOrNull {
                    it.code.equals(c, ignoreCase = true) ||
                        it.name.equals(c, ignoreCase = true) ||
                        (c.equals("zh", ignoreCase = true) && it == Zh)
                }
            }
        }
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

    private const val CACHE_MAX = 96
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
        if (text.any { it in '\uac00'..'\ud7af' }) return Lang.Ko
        if (text.any { it in '\u0600'..'\u06ff' }) return Lang.Ar
        if (text.any { it in '\u0e00'..'\u0e7f' }) return Lang.Th
        if (text.any { it in '\u0400'..'\u04ff' }) return Lang.Ru
        if (text.any { it in '\u4e00'..'\u9fff' }) return Lang.Zh
        if (text.isNotEmpty() && text.all { it.code < 128 }) return Lang.En
        return Lang.Zh
    }

    /**
     * 用户勾选的目标语种减去源语言。空选时回落英/日（与历史默认一致）。
     */
    fun targetsFor(source: Lang, selected: Collection<Lang>): List<Lang> {
        val wanted = selected.ifEmpty { listOf(Lang.En, Lang.Ja) }
        return wanted.filter { it != source }.distinct()
    }

    /** @deprecated 保留给旧测试：源语言之外的默认英/日。 */
    fun targetsFor(source: Lang): List<Lang> = targetsFor(source, listOf(Lang.En, Lang.Ja))

    /**
     * 把 [text] 译成 [selectedTargets]（默认读设置）。空串 / 过长 / 失败时跳过对应目标。
     */
    suspend fun translateOthers(
        text: String,
        selectedTargets: Collection<Lang> = OnlineTranslateTargets.selected(),
    ): List<Hit> {
        val src = text.trim()
        if (src.isEmpty() || src.length > 200) return emptyList()
        val from = detectLang(src)
        val out = ArrayList<Hit>(selectedTargets.size.coerceAtMost(8))
        for (to in targetsFor(from, selectedTargets)) {
            val translated = translate(src, from, to) ?: continue
            val t = translated.trim()
            if (!isPlausible(src, from, to, t)) continue
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
                // Google 质量通常优于 MyMemory 免费档；失败再回落。
                translateGoogle(text, from, to) ?: translateMyMemory(text, from, to)
            }
        }
        if (!result.isNullOrBlank() && isPlausible(text, from, to, result.trim())) {
            synchronized(cache) { cache[key] = result.trim() }
            return result.trim()
        }
        return null
    }

    /**
     * 拒绝明显坏结果：空串、配额警告、与原文相同、脚本明显不对。
     */
    internal fun isPlausible(source: String, from: Lang, to: Lang, result: String): Boolean {
        val t = result.trim()
        if (t.isEmpty()) return false
        if (t.startsWith("MYMEMORY WARNING", ignoreCase = true)) return false
        if (t.equals(source.trim(), ignoreCase = true)) return false
        // 译入拉丁系却几乎没有拉丁字母 → 多半失败
        if (to in LATINISH && t.count { it in 'A'..'Z' || it in 'a'..'z' } < 1 &&
            t.none { it in '\u00c0'..'\u024f' }
        ) {
            // 允许纯数字/符号的极短结果
            if (t.any { it.isLetter() }) return false
        }
        // 译入中文却没有汉字
        if (to == Lang.Zh && t.none { it in '\u4e00'..'\u9fff' } && t.any { it.isLetter() }) {
            return false
        }
        // 译入日语却没有假名/汉字
        if (to == Lang.Ja &&
            t.none { it in '\u3040'..'\u30ff' || it in '\u4e00'..'\u9fff' } &&
            t.any { it.isLetter() }
        ) {
            return false
        }
        // 译入韩语却没有谚文
        if (to == Lang.Ko && t.none { it in '\uac00'..'\ud7af' } && t.any { it.isLetter() }) {
            return false
        }
        // 源已是目标脚本、结果仍几乎全是源语言字符且长度接近 → 可疑（交给 equals 已覆盖多数）
        if (from == Lang.En && to != Lang.En && t.all { it.code < 128 } &&
            t.split(Regex("\\s+")).size >= 3 &&
            source.trim().split(Regex("\\s+")).size >= 3
        ) {
            // 英→其他却仍是英文长句：Google 偶发原样返回
            val overlap = t.lowercase().split(Regex("[^a-z]+")).toSet()
                .intersect(source.lowercase().split(Regex("[^a-z]+")).toSet())
            if (overlap.size >= 3 && overlap.size * 2 >= t.split(Regex("\\s+")).size) return false
        }
        return true
    }

    private val LATINISH = setOf(
        Lang.En, Lang.Fr, Lang.De, Lang.Es, Lang.Pt, Lang.It, Lang.Vi, Lang.Id,
        Lang.Tr, Lang.Nl, Lang.Pl, Lang.Ms,
    )

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
