/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

/**
 * Soft 26-key post-hit recovery (WowType-inspired).
 *
 * libime `Correction=Qwerty` only substitutes **same-row** neighbors inside syllables.
 * This layer considers **adjacent transpose** and **extra repeated key**, scores variants
 * by syllable coverage + a small word lexicon, and returns the best latin string.
 *
 * Exact input that already segments cleanly always wins (typo paths pay a penalty).
 */
object SoftPinyinTypo {

    private const val SYLLABLE_TEXT =
        "a ai an ang ao ba bai ban bang bao bei ben beng bi bian biang biao bie bin bing bo bong bu " +
            "ca cai can cang cao ce cen ceng cha chai chan chang chao che chen cheng chi chong chou chu " +
            "chua chuai chuan chuang chui chun chuo ci cong cou cu cuan cui cun cuo da dai dan dang dao " +
            "de dei den deng di dia dian diao die din ding diu dong dou du duan dui dun duo e ei en eng " +
            "er fa fan fang fei fen feng fiao fo fou fu ga gai gan gang gao ge gei gen geng gong gou gu " +
            "gua guai guan guang gui gun guo ha hai han hang hao he hei hen heng hong hou hu hua huai " +
            "huan huang hui hun huo ji jia jian jiang jiao jie jin jing jiong jiu ju juan jue jun ka " +
            "kai kan kang kao ke kei ken keng kong kou ku kua kuai kuan kuang kui kun kuo la lai lan " +
            "lang lao le lei leng li lia lian liang liao lie lin ling liu lo long lou lu luan lun luo " +
            "lv lve m ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu n na " +
            "nai nan nang nao ne nei nen neng ng ni nia nian niang niao nie nin ning niu nong nou nu " +
            "nuan nun nuo nv nve o ou pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou " +
            "pu qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun r ran rang rao re ren " +
            "reng ri rong rou ru rua ruan rui run ruo sa sai san sang sao se sen seng sha shai shan " +
            "shang shao she shei shen sheng shi shou shu shua shuai shuan shuang shui shun shuo si song " +
            "sou su suan sui sun suo ta tai tan tang tao te tei teng ti tian tiao tie ting tong tou tu " +
            "tuan tui tun tuo wa wai wan wang wei wen weng wo wong wu xi xia xian xiang xiao xie xin " +
            "xing xiong xiu xu xuan xue xun ya yan yang yao ye yi yin ying yo yong you yu yuan yue yun " +
            "za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei zhen zheng zhi zhong " +
            "zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo"

    private const val WORDS =
        "de shi ni wo ta le yi ge hao bu you zai " +
            "nihao weixin zhongguo women tamen shenme xianzai keyi xihuan pengyou " +
            "ganjue ganxie ganga hanguo gaoxing haokan haochi haoxiang " +
            "woaini xiexie meiyou yijing gongzuo xuexiao " +
            "jintian mingtian zuotian chifan heshui shuijiao " +
            "beijing shanghai xianggang"

    private const val SEG_PENALTY = 40
    private const val INCOMPLETE_PENALTY = 250
    private const val UNKNOWN_TAIL = 800
    private const val TRANSPOSE_PENALTY = 320
    /** Extra key drop; kept below word bonus so haoo→hao can win. */
    private const val DROP_PENALTY = 260
    private const val ADJACENT_PENALTY = 400
    private const val WORD_BONUS = 1200
    private const val PREFIX_BONUS = 500
    private const val PREFIX_MIN = 4
    /** Typo path must beat exact by this much before we rewrite (keep clean input stable). */
    private const val REWRITE_MARGIN = 120

    /** QWERTY neighbors including vertical (soft mistypes often drift rows). */
    private val NEIGHBORS: Map<Char, String> = mapOf(
        'q' to "wa",
        'w' to "qeas",
        'e' to "wrds",
        'r' to "etdf",
        't' to "ryfg",
        'y' to "tugh",
        'u' to "yihj",
        'i' to "uojk",
        'o' to "iplk",
        'p' to "ol",
        'a' to "qwsz",
        's' to "awedxz",
        'd' to "serfcx",
        'f' to "drtgvc",
        'g' to "ftyhbv",
        'h' to "gyujnb",
        'j' to "huiknm",
        'k' to "jiolm",
        'l' to "kop",
        'z' to "asx",
        'x' to "zsdc",
        'c' to "xdfv",
        'v' to "cfgb",
        'b' to "vghn",
        'n' to "bhjm",
        'm' to "njk",
    )

    private val syllables: Set<String> by lazy {
        SYLLABLE_TEXT.split(' ').filter { it.isNotEmpty() }.toSet()
    }

    private val wordRank: Map<String, Int> by lazy {
        val map = LinkedHashMap<String, Int>()
        WORDS.split(' ').filter { it.isNotEmpty() }.forEachIndexed { i, w ->
            map.putIfAbsent(w, i)
        }
        map
    }

    private val wordList: List<String> by lazy { wordRank.keys.toList() }

    fun neighbors(c: Char): List<Char> =
        NEIGHBORS[c.lowercaseChar()]?.map { it } ?: emptyList()

    private data class Variant(val text: String, val penalty: Int)

    private fun variants(latin: String): List<Variant> {
        val out = LinkedHashMap<String, Int>()
        fun add(text: String, penalty: Int) {
            if (text.isEmpty()) return
            val old = out[text]
            if (old == null || penalty < old) out[text] = penalty
        }
        add(latin, 0)
        // Adjacent transpose (one pair).
        for (i in 0 until latin.length - 1) {
            if (latin[i] == latin[i + 1]) continue
            val swapped = buildString(latin.length) {
                append(latin)
                setCharAt(i, latin[i + 1])
                setCharAt(i + 1, latin[i])
            }
            add(swapped, TRANSPOSE_PENALTY)
        }
        // Drop one extra key (prefer dropping a repeated letter).
        if (latin.length >= 3) {
            for (i in latin.indices) {
                val drop = latin.removeRange(i, i + 1)
                val pref = if (i > 0 && latin[i] == latin[i - 1]) DROP_PENALTY - 40 else DROP_PENALTY
                add(drop, pref)
            }
        }
        // Single adjacent-key substitution (incl. vertical); complements same-row engine Correction.
        if (latin.length in 2..12) {
            for (i in latin.indices) {
                for (n in neighbors(latin[i])) {
                    if (n == latin[i]) continue
                    val replaced = buildString(latin.length) {
                        append(latin)
                        setCharAt(i, n)
                    }
                    add(replaced, ADJACENT_PENALTY)
                }
            }
        }
        return out.map { (t, p) -> Variant(t, p) }
    }

    /** Lower is better. Incomplete tail allowed; uncovered middle is expensive. */
    fun segmentCost(latin: String): Int {
        if (latin.isEmpty()) return 0
        val n = latin.length
        val best = IntArray(n + 1) { Int.MAX_VALUE / 4 }
        best[0] = 0
        for (i in 0 until n) {
            if (best[i] >= Int.MAX_VALUE / 8) continue
            val maxLen = minOf(6, n - i)
            var matched = false
            for (len in maxLen downTo 1) {
                val slice = latin.substring(i, i + len)
                val atEnd = i + len == n
                when {
                    slice in syllables -> {
                        best[i + len] = minOf(best[i + len], best[i] + SEG_PENALTY)
                        matched = true
                    }
                    atEnd -> {
                        // Prefix of a longer syllable (半成品).
                        val prefixOk = syllables.any { it.startsWith(slice) && it.length > slice.length }
                        if (prefixOk) {
                            best[n] = minOf(best[n], best[i] + SEG_PENALTY + INCOMPLETE_PENALTY)
                            matched = true
                        }
                    }
                }
            }
            if (!matched && i == n - 1) {
                best[n] = minOf(best[n], best[i] + UNKNOWN_TAIL)
            }
        }
        return best[n]
    }

    private fun lexiconBonus(text: String): Int {
        wordRank[text]?.let { return WORD_BONUS - it }
        if (text.length < PREFIX_MIN) return 0
        val idx = wordList.indexOfFirst { it.startsWith(text) && it.length > text.length }
        return if (idx >= 0) PREFIX_BONUS - idx else 0
    }

    private fun effectiveCost(v: Variant): Int =
        segmentCost(v.text) + v.penalty - lexiconBonus(v.text)

    /**
     * Best recovery of [latin]. Returns [latin] unchanged when already clean or no better path.
     */
    fun recover(latin: String): String {
        val clean = latin.lowercase().filter { it in 'a'..'z' }
        if (clean.length < 2) return clean
        val exact = Variant(clean, 0)
        val exactCost = effectiveCost(exact)
        val best = variants(clean).minWith(
            compareBy<Variant> { effectiveCost(it) }
                .thenBy { it.penalty }
                .thenBy { it.text },
        )
        if (best.penalty == 0) return clean
        return if (effectiveCost(best) + REWRITE_MARGIN < exactCost) best.text else clean
    }

    /** Eval / diagnostics: top recovered paths. */
    fun recoverTop(latin: String, limit: Int = 5): List<String> {
        val clean = latin.lowercase().filter { it in 'a'..'z' }
        if (clean.isEmpty()) return emptyList()
        return variants(clean)
            .sortedWith(
                compareBy<Variant> { effectiveCost(it) }
                    .thenBy { it.penalty }
                    .thenBy { it.text },
            )
            .map { it.text }
            .distinct()
            .take(limit)
    }
}
