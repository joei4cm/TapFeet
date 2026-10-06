/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

/**
 * 智能九键拼音：2–9 的数字串解码成拼音字母，再交给 fcitx 现有拼音引擎出候选。
 * 音节表来自 libime `PinyinFuzzyFlag::None` 条目；同码优先常用音节。
 */
object T9Pinyin {

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

    private const val COMMON =
        "de shi ni wo ta le zhe you bu yi ge hao ha he hen hai hei zai lai qu shuo neng hui yao " +
            "kan dao shang xia zhong guo ren men ma ne me a o e ai an ao ou en er ang " +
            "jiu hen duo shao da xiao wen ying yu wu ye yo " +
            "ba pa fa da na la ka za ca sa zha cha sha " +
            "ji qi xi zhi chi ri zi ci si"

    private const val UNKNOWN_COST = 4000
    private const val SEG_PENALTY = 80
    private const val INCOMPLETE_PENALTY = 300

    val lettersByDigit: Map<Char, String> = mapOf(
        '2' to "abc",
        '3' to "def",
        '4' to "ghi",
        '5' to "jkl",
        '6' to "mno",
        '7' to "pqrs",
        '8' to "tuv",
        '9' to "wxyz",
    )

    fun digitOf(c: Char): Char {
        return when (c.lowercaseChar()) {
            'a', 'b', 'c' -> '2'
            'd', 'e', 'f' -> '3'
            'g', 'h', 'i' -> '4'
            'j', 'k', 'l' -> '5'
            'm', 'n', 'o' -> '6'
            'p', 'q', 'r', 's' -> '7'
            't', 'u', 'v' -> '8'
            'w', 'x', 'y', 'z' -> '9'
            else -> '0'
        }
    }

    fun t9Code(pinyin: String): String {
        val out = StringBuilder(pinyin.length)
        for (c in pinyin) {
            val d = digitOf(c)
            if (d == '0') continue
            out.append(d)
        }
        return out.toString()
    }

    fun digitsOfLatin(text: String): String = t9Code(text)

    private val rank: Map<String, Int> by lazy {
        val map = HashMap<String, Int>()
        COMMON.split(' ').filter { it.isNotEmpty() }.forEachIndexed { i, s ->
            map.putIfAbsent(s, i)
        }
        map
    }

    private val syllables: List<String> by lazy {
        SYLLABLE_TEXT.split(' ').filter { it.isNotEmpty() }
    }

    private val byT9: Map<String, List<String>> by lazy {
        syllables.groupBy { t9Code(it) }.mapValues { (_, list) ->
            list.sortedWith(compareBy<String> { rank[it] ?: UNKNOWN_COST }.thenBy { it })
        }
    }

    private fun prefixMatches(digits: String): List<String> {
        if (digits.isEmpty()) return emptyList()
        return syllables.filter { t9Code(it).startsWith(digits) }
            .sortedWith(compareBy<String> { rank[it] ?: UNKNOWN_COST }.thenBy { t9Code(it).length }.thenBy { it })
    }

    private fun pinyinPrefixForDigits(syllable: String, digits: String): String {
        val code = StringBuilder()
        syllable.forEachIndexed { idx, c ->
            code.append(digitOf(c))
            if (code.toString() == digits) return syllable.substring(0, idx + 1)
        }
        return syllable.take(digits.length.coerceAtMost(syllable.length))
    }

    private data class Node(val pinyin: String, val cost: Int)

    private fun better(a: Node, b: Node): Boolean {
        if (a.cost != b.cost) return a.cost < b.cost
        return a.pinyin.length >= b.pinyin.length
    }

    private fun syllableCost(full: String, incomplete: Boolean): Int {
        val base = rank[full] ?: UNKNOWN_COST
        return base + SEG_PENALTY + if (incomplete) INCOMPLETE_PENALTY else 0
    }

    /**
     * 把 2–9 数字串解成拼音。常用音节优先，避免「我爱你」被收成更短的 wobing。
     * 解不出时退回每键第一个字母（2→a）。
     */
    fun decode(digits: String): String {
        if (digits.isEmpty()) return ""
        val clean = digits.filter { it in '2'..'9' }
        if (clean.isEmpty()) return ""
        val best = arrayOfNulls<Node>(clean.length + 1)
        best[0] = Node("", 0)
        for (i in 0 until clean.length) {
            val prev = best[i] ?: continue
            val maxLen = minOf(6, clean.length - i)
            for (len in 1..maxLen) {
                val slice = clean.substring(i, i + len)
                val atEnd = i + len == clean.length
                val complete = byT9[slice]
                val choices: List<String> = when {
                    !complete.isNullOrEmpty() -> complete
                    atEnd -> prefixMatches(slice)
                    else -> emptyList()
                }
                if (choices.isEmpty()) continue
                val incomplete = complete.isNullOrEmpty()
                val chosenFull = choices.first()
                val piece = if (incomplete) {
                    pinyinPrefixForDigits(chosenFull, slice)
                } else {
                    chosenFull
                }
                val nextIdx = i + len
                val cand = Node(prev.pinyin + piece, prev.cost + syllableCost(chosenFull, incomplete))
                val old = best[nextIdx]
                if (old == null || better(cand, old)) best[nextIdx] = cand
            }
        }
        return best[clean.length]?.pinyin ?: fallbackFirstLetters(clean)
    }

    private fun fallbackFirstLetters(digits: String): String {
        return buildString {
            for (d in digits) {
                append(lettersByDigit[d]?.first() ?: continue)
            }
        }
    }
}
