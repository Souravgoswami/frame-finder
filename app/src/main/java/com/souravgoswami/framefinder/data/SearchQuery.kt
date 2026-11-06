package com.souravgoswami.framefinder.data

import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

val DEFAULT_IGNORED_NON_OCR_SEARCH_WORDS: Set<String> = setOf(
    "photo",
    "photos",
    "image",
    "images",
    "picture",
    "pictures",
    "pic",
    "pics",
    "photograph",
    "photographs"
)

private val searchWordPattern = Regex("[\\p{L}\\p{N}]+")

fun parseIgnoredSearchWords(value: String): Set<String> = searchWordPattern
    .findAll(value.lowercase(Locale.ROOT))
    .map(MatchResult::value)
    .toCollection(linkedSetOf())

fun formatIgnoredSearchWords(words: Set<String>): String = words
    .sortedWith(String.CASE_INSENSITIVE_ORDER)
    .joinToString(", ")

data class DateConstraint(val year: Int?, val month: Int?) {
    fun matches(epochMillis: Long): Boolean {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return (year == null || date.year == year) && (month == null || date.monthValue == month)
    }
}

data class ParsedSearchQuery(
    val normalized: String,
    val literalTerms: List<String>,
    val terms: List<String>,
    val date: DateConstraint?
) {
    fun withoutWords(words: Set<String>): ParsedSearchQuery {
        if (words.isEmpty()) return this
        val ignored = words.map { it.lowercase(Locale.ROOT) }.toSet()
        val filteredLiteralTerms = literalTerms.filterNot(ignored::contains)
        return copy(
            normalized = filteredLiteralTerms.joinToString(" "),
            literalTerms = filteredLiteralTerms,
            terms = terms.filterNot(ignored::contains)
        )
    }
}

object SearchQueryParser {
    private val yearPattern = Regex("\\b(19\\d{2}|20\\d{2}|21\\d{2})\\b")
    private val stopWords = setOf("a", "an", "the", "photo", "photos", "picture", "pictures", "image", "images", "taken", "from", "in", "on", "near", "of")
    private val months = Month.entries.associateBy { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase() } +
        Month.entries.associateBy { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase() }

    fun parse(query: String): ParsedSearchQuery {
        val normalized = query.lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
        val year = yearPattern.find(normalized)?.value?.toIntOrNull()
        val month = months.entries.firstOrNull { (name, _) -> Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(normalized) }?.value?.value
        val literalTerms = normalized.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter(String::isNotEmpty)
        val terms = literalTerms
            .filter { it.length > 1 && it !in stopWords && it.toIntOrNull() == null && it !in months.keys }
        return ParsedSearchQuery(
            normalized = normalized,
            literalTerms = literalTerms,
            terms = terms,
            date = if (year != null || month != null) DateConstraint(year, month) else null
        )
    }
}
