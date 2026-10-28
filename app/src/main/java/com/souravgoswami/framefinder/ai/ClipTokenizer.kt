package com.souravgoswami.framefinder.ai

import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/** Byte-level BPE tokenizer matching the shared tokenizer shipped with the MobileCLIP family. */
class ClipTokenizer(tokenizerJson: String) {
    private val vocabulary: Map<String, Int>
    private val mergeRanks: Map<Pair<String, String>, Int>
    private val byteEncoder = buildByteEncoder()
    private val cache = ConcurrentHashMap<String, List<String>>()
    private val tokenPattern = Pattern.compile(
        "'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+"
    )

    init {
        val root = JSONObject(tokenizerJson)
        val model = root.getJSONObject("model")
        val vocabJson = model.getJSONObject("vocab")
        vocabulary = buildMap(vocabJson.length()) {
            val keys = vocabJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, vocabJson.getInt(key))
            }
        }
        val merges = model.getJSONArray("merges")
        mergeRanks = buildMap(merges.length()) {
            for (index in 0 until merges.length()) {
                val pieces = merges.getString(index).split(' ', limit = 2)
                if (pieces.size == 2) put(pieces[0] to pieces[1], index)
            }
        }
    }

    fun encode(text: String, contextLength: Int = 77): LongArray {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
        val ids = ArrayList<Long>(contextLength)
        ids += START_TOKEN.toLong()
        val matcher = tokenPattern.matcher(normalized)
        while (matcher.find() && ids.size < contextLength - 1) {
            val bytes = matcher.group().toByteArray(Charsets.UTF_8)
            val encoded = buildString {
                bytes.forEach { append(byteEncoder[it.toInt() and 0xff]) }
            }
            for (piece in bpe(encoded)) {
                if (ids.size >= contextLength - 1) break
                ids += (vocabulary[piece] ?: END_TOKEN).toLong()
            }
        }
        ids += END_TOKEN.toLong()
        return LongArray(contextLength) { ids.getOrElse(it) { PAD_TOKEN.toLong() } }
    }

    private fun bpe(token: String): List<String> = cache.getOrPut(token) {
        if (token.isEmpty()) return@getOrPut emptyList()
        val symbols = token.codePoints().toArray().map { Character.toString(it) }.toMutableList()
        symbols[symbols.lastIndex] = symbols.last() + "</w>"

        while (symbols.size > 1) {
            var bestIndex = -1
            var bestRank = Int.MAX_VALUE
            for (index in 0 until symbols.lastIndex) {
                val rank = mergeRanks[symbols[index] to symbols[index + 1]] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestIndex = index
                }
            }
            if (bestIndex < 0) break
            val first = symbols[bestIndex]
            val second = symbols[bestIndex + 1]
            val merged = ArrayList<String>(symbols.size - 1)
            var index = 0
            while (index < symbols.size) {
                if (index < symbols.lastIndex && symbols[index] == first && symbols[index + 1] == second) {
                    merged += first + second
                    index += 2
                } else {
                    merged += symbols[index++]
                }
            }
            symbols.clear()
            symbols.addAll(merged)
        }
        symbols.toList()
    }

    private fun buildByteEncoder(): Map<Int, String> {
        val bytes = mutableListOf<Int>()
        bytes += (33..126)
        bytes += (161..172)
        bytes += (174..255)
        val codePoints = bytes.toMutableList()
        var extra = 0
        for (value in 0..255) {
            if (value !in bytes) {
                bytes += value
                codePoints += 256 + extra++
            }
        }
        return bytes.indices.associate { bytes[it] to Character.toString(codePoints[it]) }
    }

    private companion object {
        const val PAD_TOKEN = 0
        const val START_TOKEN = 49406
        const val END_TOKEN = 49407
    }
}
