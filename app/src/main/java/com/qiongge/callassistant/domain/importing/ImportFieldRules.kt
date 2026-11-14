package com.qiongge.callassistant.domain.importing

import com.hankcs.hanlp.HanLP
import com.hankcs.hanlp.seg.Segment

/** Field inference for headerless contact tables. HanLP supplies the Chinese
 * person-name recognizer; local rules only validate OCR-shaped candidates and
 * exclude real-estate/project language that is common in these lists. */
object ImportFieldRules {
    private val chineseNamePattern = Regex("""^[\p{IsHan}]+(?:·[\p{IsHan}]+)*$""")
    private val chineseNameTokenPattern = Regex("""[\p{IsHan}·]{2,50}""")
    private val latinNamePattern = Regex("""^[A-Z][A-Za-z.'’-]*(?:[ \t]+[A-Z][A-Za-z.'’-]*){1,3}$""")
    private val latinNameTokenPattern = Regex("""[A-Z][A-Za-z.'’-]*(?:[ \t]+[A-Z][A-Za-z.'’-]*){1,3}""")

    private val propertyCuePattern = Regex(
        """(?:楼盘|项目|小区|房源|地产|置业|公寓|公馆|花园|家园|大厦|广场|国际|中心|山庄|半岛|别墅|庄园|社区|商铺|商场|豪庭|华庭|华府|雅苑|名苑|住宅|商业城|购物中心|写字楼|御景|御府|学府|湖畔|花城|新城|城邦|小镇|公园|天地|天街|名邸|雅居|新天地|缤纷城|万象城|海景|江景|湖景|水岸|山水|印象|文旅城|商务中心|生态园|生态城|国际城|商业街|商业中心|生活广场|生活城|名城|云山|度假村|酒店式公寓)""",
    )
    private val developerCuePattern = Regex(
        """(?:万科|华润|保利|碧桂园|中海|金地|龙光|龙湖|恒大|富力|佳兆业|星河|招商|融创|雅居乐|中信|卓越|深业|方直|德威|颐安|中洲|鸿荣源|奥园|合生|金科|旭辉|世茂|绿地|华侨城|新力)""",
    )
    private val projectEndingPattern = Regex(
        """(?:购物中心|商务中心|大厦|广场|花园|公寓|公馆|别墅|山庄|中心|小区|村|园|苑|阁|庄|寓|宅|庭|居|台|院|楼|舍|庐|邸|轩|亭|府|城|湾|里|湖|山|峰)$""",
    )
    private val noteCuePattern = Regex(
        """(?:备注|已联系|待联系|未接|已接|回访|跟进|意向|微信|成交|预约|来电|报备|转介|购房|买房|租房|看房|看楼|预算|户型|面积|渠道|来源|咨询)""",
    )
    private val leadingLabelPattern = Regex("""^(?:客户姓名|联系人|姓名|客户名称|客户)\s*[:：]?\s*""")
    private val phoneLabelPattern = Regex("""(?:手机号|手机号码|联系电话|电话号码|联系方式|电话)""")
    private val nameSuffixPattern = Regex("""(?:先生|女士|小姐|老师)$""")

    private data class NameCandidate(
        val value: String,
        val recognizedByHanlp: Boolean,
        val start: Int,
        val end: Int,
    )

    private val hanlpSegmenter: Segment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HanLP.newSegment().enableNameRecognize(true)
    }

    /** Finds a HanLP person entity first, then uses the broad format rule as fallback. */
    fun findPersonName(text: String): String? = findPersonCandidate(text)?.value

    /** Gives HanLP-recognized names more weight than format-only fallbacks. */
    fun nameColumnScore(value: String): Int {
        val candidate = findPersonCandidate(value) ?: return 0
        val hanCount = candidate.value.count(::isHan)
        val shapeScore = when {
            hanCount in 2..4 -> 4
            hanCount in 5..8 -> 2
            hanCount > 8 -> 1
            else -> 3 // Romanized names remain supported.
        }
        return shapeScore + if (candidate.recognizedByHanlp) 2 else 0
    }

    private fun findPersonCandidate(text: String): NameCandidate? {
        if (text.isBlank()) return null
        val searchable = leadingLabelPattern.replace(text, " ")
            .replace(phoneLabelPattern, " ")
            .replace(Regex("[0-9０-９]+"), " ")

        val recognized = synchronized(hanlpSegmenter) {
            hanlpSegmenter.seg(searchable)
                .asSequence()
                .filter { it.nature?.toString() == "nr" }
                .map { term ->
                    NameCandidate(
                        value = normalizeNameCandidate(term.word),
                        recognizedByHanlp = true,
                        start = term.offset,
                        end = term.offset + term.word.length,
                    )
                }
                .toList()
        }
        recognized.firstOrNull { candidate ->
            isValidPersonName(candidate.value) && !hasAdjacentPropertyCue(searchable, candidate)
        }?.let { return it }

        val regexCandidates = sequenceOf(chineseNameTokenPattern, latinNameTokenPattern)
            .flatMap { pattern -> pattern.findAll(searchable).map { match ->
                NameCandidate(
                    value = normalizeNameCandidate(match.value),
                    recognizedByHanlp = false,
                    start = match.range.first,
                    end = match.range.last + 1,
                )
            } }
        return regexCandidates.firstOrNull { candidate -> isValidPersonName(candidate.value) }
    }

    private fun hasAdjacentPropertyCue(text: String, candidate: NameCandidate): Boolean {
        val right = text.substring(candidate.end.coerceIn(0, text.length))
            .takeWhile { isHan(it) || it == '·' }
            .take(12)
        val combined = candidate.value + right
        val hanCount = candidate.value.count(::isHan)
        return propertyCuePattern.containsMatchIn(combined) ||
            developerCuePattern.containsMatchIn(candidate.value) ||
            noteCuePattern.containsMatchIn(candidate.value) ||
            (hanCount >= 3 && projectEndingPattern.containsMatchIn(combined))
    }

    private fun isValidPersonName(value: String): Boolean {
        val candidate = normalizeNameCandidate(value)
        if (candidate.isBlank()) return false
        val hanCount = candidate.count(::isHan)
        val chinese = chineseNamePattern.matches(candidate) && hanCount in 2..50
        val latin = latinNamePattern.matches(candidate)
        if (!chinese && !latin) return false
        if (propertyCuePattern.containsMatchIn(candidate) || developerCuePattern.containsMatchIn(candidate)) return false
        if (chinese && hanCount >= 3 && projectEndingPattern.containsMatchIn(candidate)) return false
        if (noteCuePattern.containsMatchIn(candidate)) return false
        return true
    }

    private fun normalizeNameCandidate(value: String): String {
        val trimmed = nameSuffixPattern.replace(
            value.trim().replace('•', '·').replace('・', '·'),
            "",
        )
        val compactChinese = trimmed.replace(Regex("\\s+"), "")
        return if (chineseNamePattern.matches(compactChinese)) compactChinese else trimmed.replace(Regex("\\s+"), " ").trim()
    }

    private fun isHan(value: Char): Boolean = value in '\u4e00'..'\u9fff'
}
