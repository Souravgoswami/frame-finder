package com.souravgoswami.framefinder.ai

import android.content.Context
import android.content.res.AssetManager
import com.souravgoswami.framefinder.data.FrameFinderPreferences
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

data class ModelArtifact(
    val assetPath: String,
    val sizeBytes: Long,
    val sha256: String
)

class ModelManager(
    context: Context,
    private val preferences: FrameFinderPreferences
) {
    private val assets: AssetManager = context.assets
    private val modelsDirectory = File(context.filesDir, "models")

    val selectedModel: SearchModel get() = preferences.read().searchModel

    /** True when the complete model pack is present inside this APK. */
    fun isReady(model: SearchModel = selectedModel, verifyHashes: Boolean = false): Boolean =
        model.artifacts().all { artifact ->
            runCatching {
                assets.open(artifact.assetPath, AssetManager.ACCESS_STREAMING).use { input ->
                    input.available().toLong() == artifact.sizeBytes &&
                        (!verifyHashes || sha256(input) == artifact.sha256)
                }
            }.getOrDefault(false)
        }

    fun visionModel(model: SearchModel = selectedModel): ByteBuffer {
        prepare(model)
        return mapFile(File(installationDirectory(model), model.visionAssetPath.substringAfterLast('/')))
    }

    fun textModel(model: SearchModel = selectedModel): ByteBuffer {
        prepare(model)
        return mapFile(File(installationDirectory(model), model.textAssetPath.substringAfterLast('/')))
    }

    fun tokenizerJson(model: SearchModel = selectedModel): String =
        assets.open(model.tokenizerAssetPath, AssetManager.ACCESS_STREAMING)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    /** Expands only the selected model from the compressed APK into app-private storage. */
    @Synchronized
    fun prepare(model: SearchModel = selectedModel) {
        check(isReady(model)) { "${model.displayName} is missing from this installation" }
        val directory = installationDirectory(model)
        check(directory.exists() || directory.mkdirs()) { "Could not prepare ${model.displayName}" }

        for (artifact in model.artifacts().take(2)) {
            val target = File(directory, artifact.assetPath.substringAfterLast('/'))
            if (target.isFile && target.length() == artifact.sizeBytes) continue

            val partial = File(directory, "${target.name}.part")
            partial.delete()
            var copied = 0L
            assets.open(artifact.assetPath, AssetManager.ACCESS_STREAMING).use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                    }
                    output.fd.sync()
                }
            }
            check(copied == artifact.sizeBytes) { "Incomplete bundled model: ${target.name}" }
            check(FileInputStream(partial).use(::sha256) == artifact.sha256) {
                "Bundled model check failed: ${target.name}"
            }
            target.delete()
            check(partial.renameTo(target)) { "Could not prepare ${target.name}" }
        }

    }

    /** Removes downloads and prepared model packs that are no longer supported. */
    fun deleteObsoleteDownloads() {
        val supportedDirectories = SearchModel.entries
            .map { installationDirectory(it).name }
            .toSet()
        modelsDirectory.listFiles()
            ?.filterNot { it.isDirectory && it.name in supportedDirectories }
            ?.forEach(File::deleteRecursively)
    }

    private fun installationDirectory(model: SearchModel): File =
        File(modelsDirectory, model.assetDirectory.substringAfterLast('/') + BUNDLED_DIRECTORY_SUFFIX)

    private fun mapFile(file: File): ByteBuffer = FileInputStream(file).channel.use { channel ->
        channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUNDLED_DIRECTORY_SUFFIX = "-bundled-v3"
    }
}
