/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 TapFeet Contributors
 */

package org.fcitx.fcitx5.android.input.vmode

import java.util.Calendar
import java.util.Locale

/**
 * Sogou-style `v` shortcuts for pinyin: date, Chinese numerals, and a tiny calculator.
 *
 * Only the latin panel preedit is considered. Activate when it is `v` plus optional digits /
 * `+ - * / ( ) .` — never when the rest looks like pinyin (`nv` / `lv` / `van`).
 */
data class VModeSuggestion(val text: String, val comment: String)

object VMode {

    private val MATH_CHARS = "+-*/().".toSet()
    private val WEEKDAYS = arrayOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")

    fun isActive(panelPreedit: String): Boolean {
        if (!panelPreedit.startsWith("v")) return false
        val rest = panelPreedit.substring(1)
        if (rest.isEmpty()) return true
        return rest.all { it.isDigit() || it in MATH_CHARS }
    }

    fun suggestions(panelPreedit: String, now: Calendar = Calendar.getInstance()): List<VModeSuggestion> {
        if (!isActive(panelPreedit)) return emptyList()
        val rest = panelPreedit.substring(1)
        if (rest.isEmpty()) return dateSuggestions(now)
        if (rest.any { it in "+-*/()" }) {
            val value = evalMath(rest) ?: return emptyList()
            val rendered = formatNumber(value)
            return listOf(
                VModeSuggestion(rendered, "计算"),
                VModeSuggestion(toChinese(rendered.filter { it.isDigit() }.ifEmpty { "0" }), "小写"),
            )
        }
        if (rest.all { it.isDigit() }) {
            val out = mutableListOf<VModeSuggestion>()
            if (rest.length == 8) parseYmd(rest)?.let { out += dateSuggestions(it) }
            out += VModeSuggestion(toChinese(rest), "小写")
            out += VModeSuggestion(toChineseFinancial(rest), "大写")
            return out.distinctBy { it.text }
        }
        return emptyList()
    }

    internal fun toChinese(digits: String): String {
        if (digits == "0" || digits.all { it == '0' }) return "零"
        val n = digits.trimStart('0').ifEmpty { "0" }
        if (n.length > 12) return digits
        return intToChinese(n.toLong(), financial = false)
    }

    internal fun toChineseFinancial(digits: String): String {
        if (digits == "0" || digits.all { it == '0' }) return "零"
        val n = digits.trimStart('0').ifEmpty { "0" }
        if (n.length > 12) return digits
        return intToChinese(n.toLong(), financial = true)
    }

    internal fun evalMath(expr: String): Double? {
        val p = MathParser(expr.filterNot { it.isWhitespace() })
        return try {
            val v = p.parse()
            if (p.done()) v else null
        } catch (_: Exception) {
            null
        }
    }

    private fun dateSuggestions(cal: Calendar): List<VModeSuggestion> {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val week = WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val cn = "${y}年${m}月${d}日"
        return listOf(
            VModeSuggestion(cn, "日期"),
            VModeSuggestion("%d-%02d-%02d".format(Locale.US, y, m, d), "ISO"),
            VModeSuggestion(week, "星期"),
        )
    }

    private fun parseYmd(s: String): Calendar? {
        if (s.length != 8) return null
        val y = s.substring(0, 4).toInt()
        val m = s.substring(4, 6).toInt()
        val d = s.substring(6, 8).toInt()
        if (m !in 1..12 || d !in 1..31) return null
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m - 1)
            set(Calendar.DAY_OF_MONTH, d)
        }
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return value.toString()
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) asLong.toString() else {
            val s = "%.8f".format(Locale.US, value).trimEnd('0').trimEnd('.')
            s
        }
    }

    private val SMALL = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")
    private val FIN = arrayOf("零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖")
    private val UNITS = arrayOf("", "十", "百", "千")
    private val FIN_UNITS = arrayOf("", "拾", "佰", "仟")
    private val GROUPS = arrayOf("", "万", "亿")

    private fun intToChinese(n: Long, financial: Boolean): String {
        if (n == 0L) return if (financial) "零" else "零"
        val digits = if (financial) FIN else SMALL
        val units = if (financial) FIN_UNITS else UNITS
        val sb = StringBuilder()
        var rest = n
        var group = 0
        var needZero = false
        while (rest > 0) {
            val section = (rest % 10000).toInt()
            rest /= 10000
            if (section == 0) {
                needZero = sb.isNotEmpty()
            } else {
                val piece = sectionToChinese(section, digits, units, financial)
                if (needZero) sb.insert(0, digits[0])
                sb.insert(0, GROUPS[group])
                sb.insert(0, piece)
                needZero = false
            }
            group++
        }
        var out = sb.toString()
        if (!financial && out.startsWith("一十")) out = out.substring(1)
        return out
    }

    private fun sectionToChinese(
        section: Int,
        digits: Array<String>,
        units: Array<String>,
        financial: Boolean,
    ): String {
        val sb = StringBuilder()
        var n = section
        var unit = 0
        var zeroPending = false
        while (n > 0) {
            val d = n % 10
            if (d == 0) {
                if (sb.isNotEmpty()) zeroPending = true
            } else {
                if (zeroPending) sb.insert(0, digits[0])
                sb.insert(0, units[unit])
                sb.insert(0, digits[d])
                zeroPending = false
            }
            n /= 10
            unit++
        }
        if (!financial && section in 10..19 && sb.startsWith(digits[1] + units[1])) {
            return sb.substring(digits[1].length)
        }
        return sb.toString()
    }

    private class MathParser(private val s: String) {
        private var i = 0
        fun done() = i >= s.length
        fun parse(): Double = parseAdd()
        private fun peek(): Char? = s.getOrNull(i)
        private fun parseAdd(): Double {
            var v = parseMul()
            while (true) {
                when (peek()) {
                    '+' -> { i++; v += parseMul() }
                    '-' -> { i++; v -= parseMul() }
                    else -> return v
                }
            }
        }
        private fun parseMul(): Double {
            var v = parseUnary()
            while (true) {
                when (peek()) {
                    '*' -> { i++; v *= parseUnary() }
                    '/' -> {
                        i++
                        val d = parseUnary()
                        if (d == 0.0) throw ArithmeticException("div0")
                        v /= d
                    }
                    else -> return v
                }
            }
        }
        private fun parseUnary(): Double = when (peek()) {
            '+' -> { i++; parseUnary() }
            '-' -> { i++; -parseUnary() }
            else -> parsePrimary()
        }
        private fun parsePrimary(): Double {
            if (peek() == '(') {
                i++
                val v = parseAdd()
                if (peek() != ')') throw IllegalArgumentException("paren")
                i++
                return v
            }
            val start = i
            if (peek()?.isDigit() != true && peek() != '.') throw IllegalArgumentException("num")
            while (peek()?.isDigit() == true) i++
            if (peek() == '.') {
                i++
                while (peek()?.isDigit() == true) i++
            }
            return s.substring(start, i).toDouble()
        }
    }
}
