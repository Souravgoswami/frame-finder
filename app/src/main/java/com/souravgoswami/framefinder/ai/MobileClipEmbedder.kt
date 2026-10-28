package com.souravgoswami.framefinder.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.souravgoswami.framefinder.data.VectorCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.ByteBuffer
import kotlin.math.max

class MobileClipEmbedder(private val models: ModelManager) {
    private val environment = OrtEnvironment.getEnvironment()
    private val mutex = Mutex()
    private var visionSession: OrtSession? = null
    private var textSession: OrtSession? = null
    private var tokenizer: ClipTokenizer? = null
    private var activeModelId: String? = null
    private var visionModelBuffer: ByteBuffer? = null
    private var textModelBuffer: ByteBuffer? = null

    suspend fun encodeImage(bitmap: Bitmap, model: SearchModel = models.selectedModel): FloatArray = withContext(Dispatchers.Default) {
        mutex.withLock {
            check(models.isReady(model)) { "${model.displayName} is not installed" }
            activate(model)
            val imageSize = model.imageSize
            val input = preprocess(bitmap, imageSize)
            val tensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(input),
                longArrayOf(1, 3, imageSize.toLong(), imageSize.toLong())
            )
            tensor.use {
                imageSession().run(mapOf("pixel_values" to tensor)).use { result ->
                    VectorCodec.normalized(readEmbedding(result, "image_embeds"))
                }
            }
        }
    }

    suspend fun encodeText(text: String, model: SearchModel = models.selectedModel): FloatArray = withContext(Dispatchers.Default) {
        mutex.withLock {
            check(models.isReady(model)) { "${model.displayName} is not installed" }
            activate(model)
            val ids = tokenizer().encode("a photo of $text")
            val tensor = OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(ids),
                longArrayOf(1, ids.size.toLong())
            )
            tensor.use {
                textSession().run(mapOf("input_ids" to tensor)).use { result ->
                    VectorCodec.normalized(readEmbedding(result, "text_embeds"))
                }
            }
        }
    }

    suspend fun reset() = withContext(Dispatchers.Default) {
        mutex.withLock { closeActiveModel() }
    }

    private fun activate(model: SearchModel) {
        if (activeModelId == model.id) return
        closeActiveModel()
        activeModelId = model.id
    }

    private fun closeActiveModel() {
        visionSession?.close()
        textSession?.close()
        visionSession = null
        textSession = null
        tokenizer = null
        visionModelBuffer = null
        textModelBuffer = null
        activeModelId = null
    }

    private fun activeModel(): SearchModel = SearchModel.fromId(activeModelId)

    private fun imageSession(): OrtSession = visionSession ?: models.visionModel(activeModel()).let { modelBuffer ->
        visionModelBuffer = modelBuffer
        createSession(modelBuffer).also { visionSession = it }
    }

    private fun textSession(): OrtSession = textSession ?: models.textModel(activeModel()).let { modelBuffer ->
        textModelBuffer = modelBuffer
        createSession(modelBuffer).also { textSession = it }
    }

    private fun tokenizer(): ClipTokenizer = tokenizer ?: ClipTokenizer(models.tokenizerJson(activeModel())).also {
        tokenizer = it
    }

    private fun createSession(modelBuffer: ByteBuffer): OrtSession {
        val options = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(max(1, minOf(2, Runtime.getRuntime().availableProcessors())))
        }
        return environment.createSession(modelBuffer, options).also { options.close() }
    }

    private fun readEmbedding(result: OrtSession.Result, name: String): FloatArray {
        val value = result.get(name).orElseThrow { IllegalStateException("Missing model output: $name") }.value
        @Suppress("UNCHECKED_CAST")
        return when (value) {
            is Array<*> -> (value.firstOrNull() as? FloatArray)?.copyOf()
            is FloatArray -> value.copyOf()
            else -> null
        } ?: error("Unexpected $name output type")
    }

    private fun preprocess(source: Bitmap, imageSize: Int): FloatArray {
        val scale = max(imageSize / source.width.toFloat(), imageSize / source.height.toFloat())
        val width = source.width * scale
        val height = source.height * scale
        val left = (imageSize - width) / 2f
        val top = (imageSize - height) / 2f
        val resized = Bitmap.createBitmap(imageSize, imageSize, Bitmap.Config.ARGB_8888)
        Canvas(resized).drawBitmap(
            source,
            null,
            RectF(left, top, left + width, top + height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        val pixels = IntArray(imageSize * imageSize)
        resized.getPixels(pixels, 0, imageSize, 0, 0, imageSize, imageSize)
        resized.recycle()

        val plane = imageSize * imageSize
        val output = FloatArray(plane * 3)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            output[index] = ((pixel shr 16) and 0xff) / 255f
            output[plane + index] = ((pixel shr 8) and 0xff) / 255f
            output[plane * 2 + index] = (pixel and 0xff) / 255f
        }
        return output
    }

}
