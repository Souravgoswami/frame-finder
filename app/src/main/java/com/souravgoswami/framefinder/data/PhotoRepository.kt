package com.souravgoswami.framefinder.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.insertSeparators
import androidx.paging.map
import com.souravgoswami.framefinder.ai.MobileClipEmbedder
import com.souravgoswami.framefinder.ai.ModelManager
import com.souravgoswami.framefinder.ai.SearchModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

enum class ResultSort { RELEVANCE, NEWEST, OLDEST, NAME, LARGEST, SMALLEST }

const val SEARCH_STRICTNESS_MIN = 0
const val SEARCH_STRICTNESS_MAX = 100
const val DEFAULT_SEARCH_STRICTNESS = 65

enum class SearchMatchReason(val label: String) {
    VISUAL("Visual"),
    OCR("OCR"),
    FILENAME("Filename"),
    DESCRIPTION("Description"),
    DATE("Date")
}

data class SearchResult(
    val photo: PhotoGridItem,
    val relevance: Float,
    val matchReasons: Set<SearchMatchReason>
)

data class IndexedPhotoMetadata(
    val description: String?,
    val recognizedText: String?,
    val textAnalyzedAt: Long?,
    val textAnalysisError: String?,
    val aiModel: IndexedAiModelMetadata
)

data class IndexedAiModelMetadata(
    val modelName: String,
    val analyzed: Boolean,
    val analyzedAt: Long?,
    val error: String?
)

data class PhotoIndexSyncResult(
    val added: Int,
    val updated: Int,
    val removed: Int
)

class PhotoRepository(
    private val dao: PhotoDao,
    private val embedder: MobileClipEmbedder,
    private val models: ModelManager
) {
    val indexedCount: Flow<Int> = dao.observeCount()
    val ocrCount: Flow<Int> = dao.observeOcrCount()
    val ocrErrorCount: Flow<Int> = dao.observeOcrErrorCount()

    fun embeddedCount(model: SearchModel): Flow<Int> = dao.observeEmbeddedCount(model.id)

    fun embeddingErrorCount(model: SearchModel): Flow<Int> = dao.observeEmbeddingErrorCount(model.id)

    fun missingEmbeddingCount(model: SearchModel, ignoreFailures: Boolean): Flow<Int> =
        dao.observeMissingEmbeddingCount(model.id, ignoreFailures)

    fun missingOcrCount(ignoreFailures: Boolean): Flow<Int> =
        dao.observeMissingOcrCount(ignoreFailures)

    suspend fun newestIndexedSortTime(): Long = dao.newestIndexedSortTime()

    /**
     * Reconciles lightweight MediaStore records while retaining completed OCR and AI analysis for
     * unchanged photos. This deliberately does not advance the analyzed-through timestamp.
     */
    suspend fun syncPhotoIndex(
        discovered: List<PhotoEntity>,
        scanToken: Long
    ): PhotoIndexSyncResult = withContext(Dispatchers.IO) {
        val previousById = dao.getPhotoSyncStates().associateBy(PhotoSyncState::mediaId)
        val newPhotos = ArrayList<PhotoEntity>()
        val metadataUpdates = ArrayList<PhotoMetadataUpdate>()
        val changedPhotoIds = ArrayList<Long>()

        discovered.forEach { photo ->
            currentCoroutineContext().ensureActive()
            val previous = previousById[photo.mediaId]
            if (previous == null) {
                newPhotos += photo
            } else {
                metadataUpdates += photo.toMetadataUpdate()
                if (
                    previous.dateModified != photo.dateModified ||
                    previous.sizeBytes != photo.sizeBytes
                ) {
                    changedPhotoIds += photo.mediaId
                }
            }
        }

        newPhotos.forEachBatch { dao.upsertPhotos(it) }
        metadataUpdates.forEachBatch { dao.updatePhotoMetadata(it) }
        changedPhotoIds.forEachBatch {
            dao.deleteEmbeddingsForPhotos(it)
            dao.clearOcrForPhotos(it)
        }
        val removed = dao.deleteNotSeen(scanToken)
        PhotoIndexSyncResult(
            added = newPhotos.size,
            updated = changedPhotoIds.size,
            removed = removed
        )
    }

    suspend fun indexedMetadata(
        mediaId: Long,
        selectedModel: SearchModel
    ): IndexedPhotoMetadata? = withContext(Dispatchers.IO) {
        val photo = dao.getIndexState(mediaId) ?: return@withContext null
        val result = dao.getEmbeddingState(mediaId, selectedModel.id)
        IndexedPhotoMetadata(
            description = photo.description,
            recognizedText = photo.ocrText,
            textAnalyzedAt = photo.ocrIndexedAt,
            textAnalysisError = photo.ocrError,
            aiModel = IndexedAiModelMetadata(
                modelName = selectedModel.displayName,
                analyzed = result?.embedding != null,
                analyzedAt = result?.indexedAt,
                error = result?.indexError
            )
        )
    }

    fun pagedPhotos(
        sort: ResultSort,
        grouping: GalleryGrouping
    ): Flow<PagingData<GalleryItem>> = Pager(
        config = PagingConfig(
            pageSize = GALLERY_PAGE_SIZE,
            initialLoadSize = GALLERY_INITIAL_SIZE,
            prefetchDistance = GALLERY_PREFETCH_DISTANCE,
            enablePlaceholders = true,
            maxSize = GALLERY_MAX_SIZE,
            jumpThreshold = GALLERY_MAX_SIZE
        ),
        pagingSourceFactory = { PhotoPagingSource(dao, sort, grouping) }
    ).flow.map { pagingData ->
        pagingData
            .map<PhotoGridItem, GalleryItem> { GalleryItem.Photo(it) }
            .insertSeparators { before, after ->
                val afterPhoto = (after as? GalleryItem.Photo)?.photo ?: return@insertSeparators null
                val afterGroup = galleryGroup(afterPhoto, grouping) ?: return@insertSeparators null
                val beforePhoto = (before as? GalleryItem.Photo)?.photo
                val beforeGroup = beforePhoto?.let { galleryGroup(it, grouping) }
                if (beforeGroup?.key == afterGroup.key) null
                else GalleryItem.Header(afterGroup.key, afterGroup.label)
            }
    }

    suspend fun search(
        query: String,
        sort: ResultSort,
        includeVisual: Boolean,
        includeFilename: Boolean,
        includeDescription: Boolean,
        includeOcr: Boolean,
        model: SearchModel,
        searchStrictness: Int,
        ignoredNonOcrWords: Set<String>
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()

        val parsed = SearchQueryParser.parse(query)
        val nonOcrQuery = parsed.withoutWords(ignoredNonOcrWords)
        val visualQuery = nonOcrQuery.normalized
        val queryVector = if (
            includeVisual && visualQuery.isNotBlank() && models.isReady(model) && dao.hasEmbedding(model.id)
        ) {
            try {
                embedder.encodeText(visualQuery, model)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } else null

        val candidates = ArrayList<ScoredCandidate>()
        var bestSemantic = -1f
        var afterMediaId = Long.MIN_VALUE
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = dao.getSearchPageAfter(model.id, afterMediaId, SEARCH_PAGE_SIZE)
            if (page.isEmpty()) break

            for (photo in page) {
                val timestamp = photo.dateTaken ?: photo.dateAdded
                if (parsed.date?.matches(timestamp) == false) continue

                val textMatch = TextRelevanceScorer.evaluate(
                    name = photo.displayName,
                    description = photo.description.orEmpty(),
                    ocrText = photo.ocrText.orEmpty(),
                    query = parsed,
                    includeFilename = includeFilename,
                    includeDescription = includeDescription,
                    includeOcr = includeOcr,
                    nonOcrQuery = nonOcrQuery
                )
                var metadataScore = textMatch.score
                if (parsed.date != null) metadataScore += 0.35f

                val semantic = if (
                    queryVector != null && photo.embedding != null && photo.embeddingModelId == model.id
                ) {
                    VectorCodec.cosineNormalized(queryVector, photo.embedding)
                } else -1f
                bestSemantic = maxOf(bestSemantic, semantic)
                val hasStructuredFilter = parsed.date != null
                val keepRegardlessOfSemantic = hasStructuredFilter || metadataScore > 0f
                if (keepsSearchCandidate(
                        keepRegardlessOfSemantic,
                        includeVisual,
                        semantic,
                        MIN_SEMANTIC_SCORE
                    )
                ) {
                    candidates += ScoredCandidate(
                        photo = photo.toGridItem(),
                        semantic = semantic,
                        metadataScore = metadataScore,
                        keepRegardlessOfSemantic = keepRegardlessOfSemantic,
                        matchReasons = textMatch.reasons + if (parsed.date != null) {
                            setOf(SearchMatchReason.DATE)
                        } else {
                            emptySet()
                        }
                    )
                }
            }

            afterMediaId = page.last().mediaId
            if (page.size < SEARCH_PAGE_SIZE) break
        }

        val semanticCutoff = maxOf(
            MIN_SEMANTIC_SCORE,
            bestSemantic - semanticDistanceForStrictness(searchStrictness)
        )
        val matches = candidates.asSequence()
            .filter {
                keepsSearchCandidate(
                    it.keepRegardlessOfSemantic,
                    includeVisual,
                    it.semantic,
                    semanticCutoff
                )
            }
            .map { candidate ->
                SearchResult(
                    photo = candidate.photo,
                    relevance = combinedSearchRelevance(
                        candidate.semantic,
                        candidate.metadataScore,
                        semanticCutoff
                    ),
                    matchReasons = candidate.matchReasons + if (
                        isVisualSearchMatch(includeVisual, candidate.semantic, semanticCutoff)
                    ) {
                        setOf(SearchMatchReason.VISUAL)
                    } else {
                        emptySet()
                    }
                )
            }

        when (sort) {
            ResultSort.RELEVANCE -> matches.sortedWith(
                compareByDescending<SearchResult> { it.relevance }.thenByDescending { it.photo.sortTime }
            )
            ResultSort.NEWEST -> matches.sortedByDescending { it.photo.sortTime }
            ResultSort.OLDEST -> matches.sortedBy { it.photo.sortTime }
            ResultSort.NAME -> matches.sortedWith(SEARCH_NAME_COMPARATOR)
            ResultSort.LARGEST -> matches.sortedWith(
                compareByDescending<SearchResult> { it.photo.sizeBytes }
                    .thenByDescending { it.photo.sortTime }
            )
            ResultSort.SMALLEST -> matches.sortedWith(
                compareBy<SearchResult> { it.photo.sizeBytes }
                    .thenByDescending { it.photo.sortTime }
            )
        }.toList()
    }

    suspend fun hasEmbedding(mediaId: Long, model: SearchModel): Boolean =
        withContext(Dispatchers.IO) {
            dao.getEmbeddingState(mediaId, model.id)?.embedding != null
        }

    suspend fun findSimilarPhotos(
        sourceMediaId: Long,
        sort: ResultSort,
        model: SearchModel,
        searchStrictness: Int
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val sourceEmbedding = dao.getEmbeddingState(sourceMediaId, model.id)?.embedding
            ?: return@withContext emptyList()
        val sourceVector = runCatching { VectorCodec.decode(sourceEmbedding) }
            .getOrElse { return@withContext emptyList() }
        val candidates = ArrayList<SimilarPhotoCandidate>()
        var bestSimilarity = -1f
        var afterMediaId = Long.MIN_VALUE

        while (true) {
            currentCoroutineContext().ensureActive()
            val page = dao.getVisualSearchPageAfter(
                modelId = model.id,
                sourceMediaId = sourceMediaId,
                afterMediaId = afterMediaId,
                limit = SEARCH_PAGE_SIZE
            )
            if (page.isEmpty()) break

            page.forEach { photo ->
                val embedding = photo.embedding ?: return@forEach
                val similarity = VectorCodec.cosineNormalized(sourceVector, embedding)
                bestSimilarity = maxOf(bestSimilarity, similarity)
                if (similarity >= MIN_IMAGE_SIMILARITY_SCORE) {
                    candidates += SimilarPhotoCandidate(photo.toGridItem(), similarity)
                }
            }

            afterMediaId = page.last().mediaId
            if (page.size < SEARCH_PAGE_SIZE) break
        }

        val cutoff = similarPhotoCutoff(bestSimilarity, searchStrictness)
        val matches = candidates.asSequence()
            .filter { it.similarity >= cutoff }
            .map {
                SearchResult(
                    photo = it.photo,
                    relevance = it.similarity,
                    matchReasons = setOf(SearchMatchReason.VISUAL)
                )
            }

        when (sort) {
            ResultSort.RELEVANCE -> matches.sortedWith(
                compareByDescending<SearchResult> { it.relevance }.thenByDescending { it.photo.sortTime }
            )
            ResultSort.NEWEST -> matches.sortedByDescending { it.photo.sortTime }
            ResultSort.OLDEST -> matches.sortedBy { it.photo.sortTime }
            ResultSort.NAME -> matches.sortedWith(SEARCH_NAME_COMPARATOR)
            ResultSort.LARGEST -> matches.sortedWith(
                compareByDescending<SearchResult> { it.photo.sizeBytes }
                    .thenByDescending { it.photo.sortTime }
            )
            ResultSort.SMALLEST -> matches.sortedWith(
                compareBy<SearchResult> { it.photo.sizeBytes }
                    .thenByDescending { it.photo.sortTime }
            )
        }.toList()
    }

    suspend fun clear() = dao.clear()

    suspend fun removeFromIndex(mediaId: Long) = dao.deleteByMediaId(mediaId)

    suspend fun removeFromIndex(mediaIds: List<Long>) = dao.deleteByMediaIds(mediaIds)

    suspend fun setTrashed(mediaIds: List<Long>, trashed: Boolean) = dao.setTrashed(mediaIds, trashed)

    suspend fun syncExcludedFolders(folders: Collection<String>) = dao.syncExcludedFolders(folders)

    suspend fun removeMissingFromIndex(mediaIds: List<Long>) {
        if (mediaIds.isNotEmpty()) dao.deleteByMediaIds(mediaIds)
    }

    private companion object {
        const val GALLERY_INITIAL_SIZE = 240
        const val GALLERY_PREFETCH_DISTANCE = 60
        const val GALLERY_MAX_SIZE = 600
        const val SEARCH_PAGE_SIZE = 512
        const val MIN_SEMANTIC_SCORE = 0.12f
    }
}

private fun PhotoEntity.toMetadataUpdate() = PhotoMetadataUpdate(
    mediaId = mediaId,
    contentUri = contentUri,
    displayName = displayName,
    mimeType = mimeType,
    dateTaken = dateTaken,
    dateAdded = dateAdded,
    dateModified = dateModified,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    storagePath = storagePath,
    description = description,
    isDotHidden = isDotHidden,
    isTrashed = isTrashed,
    isExcluded = isExcluded,
    sortTime = sortTime,
    lastSeenScan = lastSeenScan
)

private suspend fun <T> List<T>.forEachBatch(block: suspend (List<T>) -> Unit) {
    chunked(PHOTO_SYNC_DATABASE_BATCH_SIZE).forEach {
        currentCoroutineContext().ensureActive()
        block(it)
    }
}

private const val PHOTO_SYNC_DATABASE_BATCH_SIZE = 500
private const val GALLERY_PAGE_SIZE = 120
private const val MIN_IMAGE_SIMILARITY_SCORE = 0.45f

private class PhotoPagingSource(
    private val dao: PhotoDao,
    private val sort: ResultSort,
    private val grouping: GalleryGrouping
) : PagingSource<Int, PhotoGridItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PhotoGridItem> = try {
        val totalCount = dao.galleryPhotoCount()
        val requestedOffset = params.key ?: 0
        val offset = if (totalCount == 0) 0 else requestedOffset.coerceIn(0, totalCount - 1)
        val photos = when {
            grouping != GalleryGrouping.FOLDER && (sort == ResultSort.NEWEST || sort == ResultSort.RELEVANCE) ->
                dao.getGalleryPageNewest(params.loadSize, offset)
            grouping != GalleryGrouping.FOLDER && sort == ResultSort.OLDEST ->
                dao.getGalleryPageOldest(params.loadSize, offset)
            else -> dao.getGalleryPage(sort.name, grouping.name, params.loadSize, offset)
        }
        LoadResult.Page(
            data = photos,
            prevKey = if (offset == 0) null else maxOf(0, offset - GALLERY_PAGE_SIZE),
            nextKey = if (offset + photos.size >= totalCount) null else offset + photos.size,
            itemsBefore = offset,
            itemsAfter = maxOf(0, totalCount - offset - photos.size)
        )
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        LoadResult.Error(exception)
    }

    override fun getRefreshKey(state: PagingState<Int, PhotoGridItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val centeredOffset = maxOf(0, anchor - state.config.initialLoadSize / 2)
        return centeredOffset - centeredOffset % state.config.pageSize
    }

    override val jumpingSupported: Boolean = true
}

internal fun semanticMatchCutoff(
    scores: List<Float>,
    minimum: Float = 0.12f,
    maximumDistanceFromBest: Float = 0.07f
): Float = maxOf(minimum, (scores.maxOrNull() ?: -1f) - maximumDistanceFromBest)

internal fun semanticDistanceForStrictness(strictness: Int): Float {
    val normalized = strictness.coerceIn(SEARCH_STRICTNESS_MIN, SEARCH_STRICTNESS_MAX) / 100f
    return 0.12f - normalized * 0.10f
}

private data class ScoredCandidate(
    val photo: PhotoGridItem,
    val semantic: Float,
    val metadataScore: Float,
    val keepRegardlessOfSemantic: Boolean,
    val matchReasons: Set<SearchMatchReason>
)

private data class SimilarPhotoCandidate(
    val photo: PhotoGridItem,
    val similarity: Float
)

internal fun similarPhotoCutoff(bestSimilarity: Float, strictness: Int): Float = maxOf(
    MIN_IMAGE_SIMILARITY_SCORE,
    bestSimilarity - semanticDistanceForStrictness(strictness)
)

internal fun combinedSearchRelevance(semantic: Float, metadataScore: Float, semanticCutoff: Float): Float =
    if (semantic >= semanticCutoff) semantic.coerceAtLeast(0f) * 5f + metadataScore else metadataScore

internal fun isVisualSearchMatch(enabled: Boolean, semantic: Float, cutoff: Float): Boolean =
    enabled && semantic >= cutoff

internal fun keepsSearchCandidate(
    keepForNonVisualSource: Boolean,
    visualEnabled: Boolean,
    semantic: Float,
    semanticCutoff: Float
): Boolean = keepForNonVisualSource || isVisualSearchMatch(visualEnabled, semantic, semanticCutoff)

internal object TextRelevanceScorer {
    data class Evaluation(val score: Float, val reasons: Set<SearchMatchReason>)

    fun score(
        name: String,
        description: String,
        ocrText: String,
        query: ParsedSearchQuery,
        includeFilename: Boolean = true,
        includeDescription: Boolean = true,
        includeOcr: Boolean = true,
        nonOcrQuery: ParsedSearchQuery = query
    ): Float = evaluate(
        name,
        description,
        ocrText,
        query,
        includeFilename,
        includeDescription,
        includeOcr,
        nonOcrQuery
    ).score

    fun evaluate(
        name: String,
        description: String,
        ocrText: String,
        query: ParsedSearchQuery,
        includeFilename: Boolean = true,
        includeDescription: Boolean = true,
        includeOcr: Boolean = true,
        nonOcrQuery: ParsedSearchQuery = query
    ): Evaluation {
        val searchableName = if (includeFilename) name else ""
        val searchableDescription = if (includeDescription) description else ""
        val searchableOcr = if (includeOcr) ocrText else ""
        val metadataRequiresCompletePhrase = nonOcrQuery.literalTerms.size > 1
        val ocrRequiresCompletePhrase = query.literalTerms.size > 1
        val exactName = searchableName.containsSearchQuery(nonOcrQuery, metadataRequiresCompletePhrase)
        val exactDescription = searchableDescription.containsSearchQuery(nonOcrQuery, metadataRequiresCompletePhrase)
        val exactOcr = searchableOcr.containsSearchQuery(query, ocrRequiresCompletePhrase)
        var score = 0f
        if (exactName || exactDescription) {
            score += if (metadataRequiresCompletePhrase) 0.85f else 0.55f
        }
        if (exactOcr) {
            score += if (ocrRequiresCompletePhrase) 0.35f else 0.20f
        }
        var nameTermMatched = false
        var descriptionTermMatched = false
        var ocrTermMatched = false
        if (!metadataRequiresCompletePhrase && nonOcrQuery.terms.isNotEmpty()) {
            var metadataHits = 0
            nonOcrQuery.terms.forEach { term ->
                val nameHit = searchableName.containsSearch(term)
                val descriptionHit = searchableDescription.containsSearch(term)
                if (nameHit || descriptionHit) metadataHits++
                nameTermMatched = nameTermMatched || nameHit
                descriptionTermMatched = descriptionTermMatched || descriptionHit
            }
            score += metadataHits.toFloat() / nonOcrQuery.terms.size * 0.30f
        }
        if (!ocrRequiresCompletePhrase && query.terms.isNotEmpty()) {
            var ocrHits = 0
            query.terms.forEach { term ->
                val ocrHit = searchableOcr.containsSearch(term)
                if (ocrHit) ocrHits++
                ocrTermMatched = ocrTermMatched || ocrHit
            }
            score += ocrHits.toFloat() / query.terms.size * 0.15f
        }
        val reasons = buildSet {
            if (exactName || nameTermMatched) add(SearchMatchReason.FILENAME)
            if (exactDescription || descriptionTermMatched) add(SearchMatchReason.DESCRIPTION)
            if (exactOcr || ocrTermMatched) add(SearchMatchReason.OCR)
        }
        return Evaluation(score, reasons)
    }

    private fun String.containsSearch(value: String): Boolean =
        isNotEmpty() && value.isNotEmpty() && contains(value, ignoreCase = true)

    private fun String.containsSearchQuery(
        query: ParsedSearchQuery,
        requiresCompletePhrase: Boolean
    ): Boolean = if (requiresCompletePhrase) {
        containsTokenPhrase(query.literalTerms)
    } else {
        containsSearch(query.normalized)
    }

    private fun String.containsTokenPhrase(tokens: List<String>): Boolean {
        if (isEmpty() || tokens.isEmpty()) return false
        var sourceIndex = 0
        var matchedTokens = 0
        while (sourceIndex < length) {
            while (sourceIndex < length && !this[sourceIndex].isLetterOrDigit()) sourceIndex++
            val tokenStart = sourceIndex
            while (sourceIndex < length && this[sourceIndex].isLetterOrDigit()) sourceIndex++
            val tokenLength = sourceIndex - tokenStart
            if (tokenLength == 0) break

            val expected = tokens[matchedTokens]
            val matchesExpected = tokenLength == expected.length && regionMatches(
                thisOffset = tokenStart,
                other = expected,
                otherOffset = 0,
                length = tokenLength,
                ignoreCase = true
            )
            if (matchesExpected) {
                matchedTokens++
                if (matchedTokens == tokens.size) return true
            } else {
                val first = tokens.first()
                matchedTokens = if (
                    tokenLength == first.length && regionMatches(
                        thisOffset = tokenStart,
                        other = first,
                        otherOffset = 0,
                        length = tokenLength,
                        ignoreCase = true
                    )
                ) {
                    1
                } else {
                    0
                }
            }
        }
        return false
    }
}

private val SEARCH_NAME_COMPARATOR =
    Comparator<SearchResult> { left, right ->
        left.photo.displayName.compareTo(right.photo.displayName, ignoreCase = true)
    }.thenByDescending { it.photo.sortTime }
