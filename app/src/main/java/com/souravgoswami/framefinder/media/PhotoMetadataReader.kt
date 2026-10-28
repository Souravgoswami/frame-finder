package com.souravgoswami.framefinder.media

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.souravgoswami.framefinder.data.PhotoGridItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class PhotoMetadataEntry(
    val label: String,
    val value: String
)

data class PhotoFileMetadata(
    val entries: List<PhotoMetadataEntry>,
    val message: String? = null
)

/** Reads embedded camera metadata only. Location tags are deliberately not exposed. */
class PhotoMetadataReader(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun read(photo: PhotoGridItem): PhotoFileMetadata = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(Uri.parse(photo.contentUri))?.use { input ->
                val exif = ExifInterface(input)
                val entries = buildList {
                    val make = exif.text(ExifInterface.TAG_MAKE)
                    val model = exif.text(ExifInterface.TAG_MODEL)
                    cameraName(make, model)?.let { add(PhotoMetadataEntry("Camera", it)) }
                    exif.text(TAG_LENS_MODEL)
                        ?.let { add(PhotoMetadataEntry("Lens", it)) }
                    exif.text(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?.let { add(PhotoMetadataEntry("Captured", it)) }
                    exif.number(ExifInterface.TAG_F_NUMBER)
                        ?.let { add(PhotoMetadataEntry("Aperture", "f/${decimal(it)}")) }
                    exif.text(ExifInterface.TAG_EXPOSURE_TIME)
                        ?.let { add(PhotoMetadataEntry("Exposure time", exposureLabel(it))) }
                    exif.text(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                        ?.let { add(PhotoMetadataEntry("ISO", it)) }
                    exif.number(ExifInterface.TAG_FOCAL_LENGTH)
                        ?.let { add(PhotoMetadataEntry("Focal length", "${decimal(it)} mm")) }
                    flashLabel(exif.getAttributeInt(ExifInterface.TAG_FLASH, -1))
                        ?.let { add(PhotoMetadataEntry("Flash", it)) }
                    orientationLabel(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
                        ?.let { add(PhotoMetadataEntry("Orientation", it)) }
                    whiteBalanceLabel(exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1))
                        ?.let { add(PhotoMetadataEntry("White balance", it)) }
                    exif.text(ExifInterface.TAG_SOFTWARE)
                        ?.let { add(PhotoMetadataEntry("Software", it)) }
                    exif.text(ExifInterface.TAG_IMAGE_DESCRIPTION)
                        ?.let { add(PhotoMetadataEntry("Image description", it)) }
                    exif.text(ExifInterface.TAG_COPYRIGHT)
                        ?.let { add(PhotoMetadataEntry("Copyright", it)) }
                }
                if (entries.isEmpty()) {
                    PhotoFileMetadata(
                        entries = emptyList(),
                        message = "This photo does not contain readable camera metadata."
                    )
                } else {
                    PhotoFileMetadata(entries)
                }
            } ?: PhotoFileMetadata(
                entries = emptyList(),
                message = "The photo could not be opened."
            )
        }.getOrElse {
            PhotoFileMetadata(
                entries = emptyList(),
                message = "Camera metadata could not be read from this photo."
            )
        }
    }
}

private fun ExifInterface.text(tag: String): String? =
    getAttribute(tag)?.trim()?.takeIf(String::isNotEmpty)

private fun ExifInterface.number(tag: String): Double? =
    getAttributeDouble(tag, Double.NaN).takeUnless(Double::isNaN)

private fun cameraName(make: String?, model: String?): String? {
    if (make == null) return model
    if (model == null) return make
    return if (model.startsWith(make, ignoreCase = true)) model else "$make $model"
}

private fun decimal(value: Double): String = when {
    value == value.toLong().toDouble() -> value.toLong().toString()
    else -> String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

private fun exposureLabel(raw: String): String {
    val value = raw.toDoubleOrNull() ?: return raw
    if (value <= 0.0) return raw
    return if (value < 1.0) {
        val denominator = (1.0 / value).toInt().coerceAtLeast(1)
        "1/$denominator s"
    } else {
        "${decimal(value)} s"
    }
}

private fun flashLabel(value: Int): String? = when {
    value < 0 -> null
    value and 1 == 1 -> "Fired"
    else -> "Did not fire"
}

private fun orientationLabel(value: Int): String? = when (value) {
    ExifInterface.ORIENTATION_NORMAL -> "Normal"
    ExifInterface.ORIENTATION_ROTATE_90 -> "Rotated 90°"
    ExifInterface.ORIENTATION_ROTATE_180 -> "Rotated 180°"
    ExifInterface.ORIENTATION_ROTATE_270 -> "Rotated 270°"
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Flipped horizontally"
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Flipped vertically"
    ExifInterface.ORIENTATION_TRANSPOSE -> "Transposed"
    ExifInterface.ORIENTATION_TRANSVERSE -> "Transverse"
    else -> null
}

private fun whiteBalanceLabel(value: Int): String? = when (value) {
    0 -> "Automatic"
    1 -> "Manual"
    else -> null
}

private const val TAG_LENS_MODEL = "LensModel"
