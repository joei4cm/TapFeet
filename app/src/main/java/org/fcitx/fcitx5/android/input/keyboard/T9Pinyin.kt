/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

/**
 * 智能九键拼音：2–9 数字串 → 拼音字母，再交给现有引擎出候选。
 *
 * 解码不再 1-best 拍板（曾把「感觉」426583 写成 haojue）。流程接近 WowType：
 * 同码保留多个音节 → 按位束搜索 → 词表给整词/前缀加分 → 只把胜出的一条拉丁串写入引擎。
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

    /** 单音节先验：越靠前越优先。 */
    private const val COMMON =
        "de shi ni wo ta le zhe you bu yi ge hao ha he hen hai hei zai lai qu shuo neng hui yao " +
            "kan dao shang xia zhong guo ren men ma ne me a o e ai an ao ou en er ang " +
            "jiu hen duo shao da xiao wen ying yu wu ye yo " +
            "gan gao han gang guan " +
            "ba pa fa da na la ka za ca sa zha cha sha " +
            "ji qi xi zhi chi ri zi ci si " +
            "jue ju jin jing jian jiang"

    /**
     * 常用词拼音（空格分隔，靠前优先）。同码时给对应路径大额加分，
     * 避免音节先验把「感觉」等词锁死成 hao*。
     */
    private const val WORDS =
        "nihao zhongguo women tamen shenme zhege nage wode nide tade xianzai keyi yinwei suoyi " +
            "danshi ruguo yinwei xihuan xianzai pengyou shijian diannao shouji dianying yinyue " +
            "ganjue ganxie ganga ganqing ganmao ganbu ganjing gankuai ganren ganma " +
            "ganggang gangcai gangbi " +
            "hanguo ganhuo hanzi hanyu hanshu " +
            "gaoxing gaosu gaokao gaibian gaozhong " +
            "haode haokan haochi haoxiang haishi haohaode " +
            "xianzai xianggang shanghai beijing " +
            "woaini xiexie duibuqi meiyou yijing keqi " +
            "gongzuo xuexiao laoshi tongxue pengyou jiating " +
            "wenti " +
            "jintian mingtian zuotian xingqi " +
            "zongshi yizhi yiqie yihou yiqian " +
            "kaixin shengqi haipa nanguo " +
            "chifan heshui shuijiao shangban xiaban"

    private const val UNKNOWN_COST = 4000
    private const val SEG_PENALTY = 80
    private const val INCOMPLETE_PENALTY = 300
    /** 整词命中时从路径代价里减去（须大于 UNKNOWN 量级，才能翻盘 hao→gan）。 */
    private const val WORD_BONUS = 9000
    /** 词前缀引导（数字够长且拼音是某词的数字前缀）。 */
    private const val PREFIX_BONUS = 4500
    private const val PREFIX_MIN_DIGITS = 4
    private const val BEAM_SIZE = 8
    /** 每个数字切片最多展开几个同码音节。 */
    private const val SYLLABLE_ALTS = 4

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

    /** 词表：拼音 → 序（越小越好）；并保留 (码, 拼音) 列表做前缀引导。 */
    private val wordRank: Map<String, Int> by lazy {
        val map = LinkedHashMap<String, Int>()
        WORDS.split(' ').filter { it.isNotEmpty() }.forEachIndexed { i, py ->
            map.putIfAbsent(py, i)
        }
        map
    }

    private val wordList: List<Pair<String, String>> by lazy {
        wordRank.keys.map { py -> t9Code(py) to py }
    }

    private fun prefixMatches(digits: String): List<String> {
        if (digits.isEmpty()) return emptyList()
        return syllables.filter { t9Code(it).startsWith(digits) }
            .sortedWith(
                compareBy<String> { rank[it] ?: UNKNOWN_COST }
                    .thenBy { t9Code(it).length }
                    .thenBy { it },
            )
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

    private fun syllableCost(full: String, incomplete: Boolean): Int {
        val base = rank[full] ?: UNKNOWN_COST
        return base + SEG_PENALTY + if (incomplete) INCOMPLETE_PENALTY else 0
    }

    /**
     * 词表调整：整词或「词表里最靠前的一条」前缀命中时加分。
     * 同数字前缀可能对上多个词（ganj←感觉、haok←好看），只能奖一条，
     * 否则音节先验仍会把 hao* 压过 gan*。
     */
    private fun lexiconBonus(pinyin: String, digits: String): Int {
        if (pinyin.isEmpty()) return 0
        wordRank[pinyin]?.let { r -> return WORD_BONUS - r }
        if (digits.length < PREFIX_MIN_DIGITS) return 0
        val best = wordList.firstOrNull { (code, _) ->
            code.startsWith(digits)
        } ?: return 0
        return if (pinyinPrefixForDigits(best.second, digits) == pinyin) {
            PREFIX_BONUS - wordRank.getOrElse(best.second) { wordList.indexOf(best) }
        } else {
            0
        }
    }

    private fun effectiveCost(node: Node, digits: String): Int =
        node.cost - lexiconBonus(node.pinyin, digits)

    private fun prune(bucket: MutableList<Node>, digits: String) {
        if (bucket.size <= BEAM_SIZE) return
        bucket.sortWith(
            compareBy<Node> { effectiveCost(it, digits) }
                .thenByDescending { it.pinyin.length }
                .thenBy { it.pinyin },
        )
        while (bucket.size > BEAM_SIZE) bucket.removeAt(bucket.lastIndex)
    }

    private fun expandChoices(slice: String, atEnd: Boolean): List<Pair<String, Boolean>> {
        val complete = byT9[slice]
        if (!complete.isNullOrEmpty()) {
            return complete.take(SYLLABLE_ALTS).map { it to false }
        }
        if (!atEnd) return emptyList()
        return prefixMatches(slice).take(SYLLABLE_ALTS).map { it to true }
    }

    /**
     * 诊断用：返回按有效代价排序的 top 路径拼音（含词表加分后）。
     */
    fun decodeTop(digits: String, limit: Int = BEAM_SIZE): List<String> {
        if (digits.isEmpty()) return emptyList()
        val clean = digits.filter { it in '2'..'9' }
        if (clean.isEmpty()) return emptyList()
        return beam(clean)
            .sortedWith(
                compareBy<Node> { effectiveCost(it, clean) }
                    .thenByDescending { it.pinyin.length }
                    .thenBy { it.pinyin },
            )
            .map { it.pinyin }
            .distinct()
            .take(limit)
    }

    private fun beam(clean: String): List<Node> {
        val best = Array(clean.length + 1) { mutableListOf<Node>() }
        best[0].add(Node("", 0))
        for (i in 0 until clean.length) {
            val prevs = best[i]
            if (prevs.isEmpty()) continue
            val maxLen = minOf(6, clean.length - i)
            for (prev in prevs) {
                for (len in 1..maxLen) {
                    val slice = clean.substring(i, i + len)
                    val atEnd = i + len == clean.length
                    for ((chosenFull, incomplete) in expandChoices(slice, atEnd)) {
                        val piece = if (incomplete) {
                            pinyinPrefixForDigits(chosenFull, slice)
                        } else {
                            chosenFull
                        }
                        val nextIdx = i + len
                        best[nextIdx].add(
                            Node(prev.pinyin + piece, prev.cost + syllableCost(chosenFull, incomplete)),
                        )
                    }
                }
            }
            // 剪枝目标位：本轮写入的各个 next；为简单起见每层结束后剪所有非空桶
            for (j in (i + 1)..minOf(i + 6, clean.length)) {
                prune(best[j], clean.substring(0, j))
            }
        }
        return best[clean.length]
    }

    /**
     * 把 2–9 数字串解成拼音。束搜索 + 词表选路；解不出时退回每键首字母。
     */
    fun decode(digits: String): String {
        if (digits.isEmpty()) return ""
        val clean = digits.filter { it in '2'..'9' }
        if (clean.isEmpty()) return ""
        val paths = beam(clean)
        if (paths.isEmpty()) return fallbackFirstLetters(clean)
        return paths.minWith(
            compareBy<Node> { effectiveCost(it, clean) }
                .thenByDescending { it.pinyin.length }
                .thenBy { it.pinyin },
        ).pinyin
    }

    private fun fallbackFirstLetters(digits: String): String {
        return buildString {
            for (d in digits) {
                append(lettersByDigit[d]?.first() ?: continue)
            }
        }
    }
}
