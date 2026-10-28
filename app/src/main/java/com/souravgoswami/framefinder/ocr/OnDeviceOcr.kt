package com.souravgoswami.framefinder.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class OnDeviceOcr : Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(OcrTextNormalizer.normalize(result.text))
            }
            .addOnFailureListener { exception ->
                if (continuation.isActive) continuation.resumeWithException(exception)
            }
            .addOnCanceledListener { continuation.cancel() }
    }

    override fun close() = recognizer.close()
}

object OcrTextNormalizer {
    private const val MAX_STORED_CHARACTERS = 32_000
    private val inlineWhitespace = Regex("[\\t\\u000B\\u000C\\r ]+")
    private val excessBlankLines = Regex("\\n{3,}")

    fun normalize(text: String): String = text
        .trim()
        .replace(inlineWhitespace, " ")
        .replace(excessBlankLines, "\n\n")
        .take(MAX_STORED_CHARACTERS)
}
