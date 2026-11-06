package com.souravgoswami.framefinder.data

object ExcludedFolders {
    const val FRAME_FINDER_SAVE_DIRECTORY = "Frame Finder"
    const val FRAME_FINDER_SAVE_FOLDER = "Download/$FRAME_FINDER_SAVE_DIRECTORY"

    fun normalize(path: String?): String? = path
        ?.trim()
        ?.replace('\\', '/')
        ?.trimEnd('/')
        ?.takeIf(String::isNotBlank)

    fun contains(path: String?, excludedFolders: Set<String>): Boolean {
        val normalizedPath = normalize(path) ?: return false
        return excludedFolders.any { excluded ->
            val normalizedExcluded = normalize(excluded) ?: return@any false
            normalizedPath == normalizedExcluded || normalizedPath.startsWith("$normalizedExcluded/")
        }
    }

    fun isAlwaysExcluded(path: String?): Boolean {
        val normalizedPath = normalize(path) ?: return false
        return normalizedPath == FRAME_FINDER_SAVE_FOLDER ||
            normalizedPath.startsWith("$FRAME_FINDER_SAVE_FOLDER/") ||
            normalizedPath.endsWith("/$FRAME_FINDER_SAVE_FOLDER") ||
            normalizedPath.contains("/$FRAME_FINDER_SAVE_FOLDER/")
    }
}
