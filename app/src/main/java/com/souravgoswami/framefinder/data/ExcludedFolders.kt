package com.souravgoswami.framefinder.data

object ExcludedFolders {
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
}
