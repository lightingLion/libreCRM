package com.qiongge.callassistant.domain.importing

object PhoneRecognition {
    // Require the mainland mobile shape instead of treating any long digit run
    // (for example, an ID number in a remark) as a phone candidate.
    private val candidatePattern = Regex(
        """(?<![0-9０-９])(?:(?:[+＋]?86|0086)[\s\-‐–—.()（）]*)?1[3-9](?:[\s\-‐–—.()（）]?[0-9０-９]){9}(?![0-9０-９])""",
    )
    private val mainlandMobilePattern = Regex("1[3-9][0-9]{9}")
    private val removableSeparators = setOf(' ', '\t', '\n', '\r', '-', '‐', '–', '—', '(', ')', '（', '）', '.', '·')

    /** Converts full-width digits, strips display separators, and removes an optional +86 / 0086 prefix. */
    fun normalizeForEditing(value: String): String {
        val normalized = value
        .map { character ->
            if (character in '０'..'９') {
                ('0'.code + character.code - '０'.code).toChar()
            } else {
                character
            }
        }
        .filterNot { it in removableSeparators }
        .joinToString("")
        .removePrefix("+")
        .removePrefix("＋")
        return when {
            normalized.startsWith("0086") && normalized.length >= 15 -> normalized.removePrefix("0086")
            normalized.startsWith("86") && normalized.length >= 13 -> normalized.removePrefix("86")
            else -> normalized
        }
    }

    fun isMainlandMobile(value: String): Boolean =
        mainlandMobilePattern.matches(normalizeForEditing(value))

    fun comparableDigits(value: String): String? = normalizeForEditing(value)
        .takeIf(mainlandMobilePattern::matches)

    private fun normalizedText(text: String): String =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)

    fun isPhoneCell(text: String): Boolean {
        val normalized = normalizedText(text)
        return candidatePattern.containsMatchIn(normalized) &&
            candidatePattern.replace(normalized, "").all { it.isWhitespace() || it in "/、,;|()（）" }
    }

    fun candidates(text: String): List<String> = candidatePattern.findAll(normalizedText(text))
        .map { normalizeForEditing(it.value.trim()) }
        .filter(::isMainlandMobile)
        .distinct()
        .toList()
}
