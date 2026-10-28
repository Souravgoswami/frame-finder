package com.souravgoswami.framefinder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRelevanceScorerTest {
    @Test
    fun exactOcrPhraseProducesStrongMatch() {
        val score = TextRelevanceScorer.score(
            name = "IMG_001.jpg",
            description = "",
            ocrText = "store receipt\ntotal 1,250.00",
            query = SearchQueryParser.parse("store receipt")
        )

        assertTrue(score in 0.3f..0.4f)
    }

    @Test
    fun noOcrTextMeansNoTextMatch() {
        val score = TextRelevanceScorer.score(
            name = "IMG_001.jpg",
            description = "",
            ocrText = "",
            query = SearchQueryParser.parse("store receipt")
        )

        assertEquals(0f, score)
    }

    @Test
    fun disabledFilenameDoesNotAddSearchPoints() {
        val score = TextRelevanceScorer.score(
            name = "cat.jpg",
            description = "",
            ocrText = "",
            query = SearchQueryParser.parse("cat"),
            includeFilename = false
        )

        assertEquals(0f, score)
    }

    @Test
    fun descriptionCanBeEnabledIndependentlyFromFilename() {
        val query = SearchQueryParser.parse("oscilloscope")
        val enabled = TextRelevanceScorer.score(
            name = "cat.jpg",
            description = "bench oscilloscope",
            ocrText = "",
            query = query,
            includeFilename = false,
            includeDescription = true
        )
        val disabled = TextRelevanceScorer.score(
            name = "cat.jpg",
            description = "bench oscilloscope",
            ocrText = "",
            query = query,
            includeFilename = false,
            includeDescription = false
        )

        assertTrue(enabled > 0f)
        assertEquals(0f, disabled)
    }

    @Test
    fun evaluationReportsEachTextSourceThatMatched() {
        val evaluation = TextRelevanceScorer.evaluate(
            name = "cat_portrait.jpg",
            description = "a cat sleeping",
            ocrText = "cat adoption day",
            query = SearchQueryParser.parse("cat")
        )

        assertTrue(evaluation.score > 0f)
        assertEquals(
            setOf(
                SearchMatchReason.FILENAME,
                SearchMatchReason.DESCRIPTION,
                SearchMatchReason.OCR
            ),
            evaluation.reasons
        )
    }

    @Test
    fun evaluationOmitsDisabledTextSources() {
        val evaluation = TextRelevanceScorer.evaluate(
            name = "cat.jpg",
            description = "cat sleeping",
            ocrText = "cat",
            query = SearchQueryParser.parse("cat"),
            includeFilename = false,
            includeDescription = false,
            includeOcr = true
        )

        assertEquals(setOf(SearchMatchReason.OCR), evaluation.reasons)
    }

    @Test
    fun matchingDoesNotAllocateLowercaseCopiesToHandleCase() {
        val evaluation = TextRelevanceScorer.evaluate(
            name = "CAT_Portrait.JPG",
            description = "A Cat Sleeping",
            ocrText = "CAT ADOPTION DAY",
            query = SearchQueryParser.parse("cat")
        )

        assertTrue(evaluation.score > 0f)
        assertEquals(
            setOf(
                SearchMatchReason.FILENAME,
                SearchMatchReason.DESCRIPTION,
                SearchMatchReason.OCR
            ),
            evaluation.reasons
        )
    }

    @Test
    fun semanticCutoffRejectsWeakMatchesFarFromTheBestResult() {
        assertEquals(0.18f, semanticMatchCutoff(listOf(0.25f, 0.15f, 0.11f)), 0.0001f)
        assertEquals(0.12f, semanticMatchCutoff(listOf(0.15f, 0.10f)), 0.0001f)
    }

    @Test
    fun strictnessNarrowsTheAllowedDistanceFromTheBestMatch() {
        assertEquals(0.12f, semanticDistanceForStrictness(0), 0.0001f)
        assertEquals(0.055f, semanticDistanceForStrictness(DEFAULT_SEARCH_STRICTNESS), 0.0001f)
        assertEquals(0.02f, semanticDistanceForStrictness(100), 0.0001f)
    }

    @Test
    fun qualifyingVisualMatchOutranksExactOcrOnlyMatch() {
        val visual = combinedSearchRelevance(semantic = 0.20f, metadataScore = 0f, semanticCutoff = 0.18f)
        val ocrOnly = combinedSearchRelevance(semantic = 0.15f, metadataScore = 0.35f, semanticCutoff = 0.18f)

        assertTrue(visual > ocrOnly)
    }

    @Test
    fun disabledVisualSearchRejectsSemanticOnlyCandidates() {
        assertEquals(false, isVisualSearchMatch(enabled = false, semantic = 0.25f, cutoff = 0.18f))
        assertEquals(
            false,
            keepsSearchCandidate(
                keepForNonVisualSource = false,
                visualEnabled = false,
                semantic = 0.25f,
                semanticCutoff = 0.18f
            )
        )
        assertTrue(
            keepsSearchCandidate(
                keepForNonVisualSource = true,
                visualEnabled = false,
                semantic = 0.25f,
                semanticCutoff = 0.18f
            )
        )
    }
}
