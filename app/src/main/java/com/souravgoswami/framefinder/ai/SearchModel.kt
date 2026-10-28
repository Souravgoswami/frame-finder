package com.souravgoswami.framefinder.ai

enum class SearchModel(
    val id: String,
    val displayName: String,
    val tier: String,
    val description: String,
    val assetDirectory: String,
    val imageSize: Int,
    val visionSizeBytes: Long,
    val visionSha256: String,
    val textSizeBytes: Long,
    val textSha256: String
) {
    MOBILECLIP_S2(
        id = "mobileclip-s2-fp32-v2",
        displayName = "MobileCLIP S2",
        tier = "Recommended",
        description = "Strong visual search with lower memory use and faster analysis.",
        assetDirectory = "framefinder_models/mobileclip-s2",
        imageSize = 256,
        visionSizeBytes = 71_710_175L,
        visionSha256 = "e011629d1490897f039fd8fbf1d6c677a03e0ffd1b5cea7cec5099bd8fea9476",
        textSizeBytes = 64_117_260L,
        textSha256 = "f8aaa97cf17fbadd78b0bd6d5f6a1c57f712402cacc3055ea92c1a3331357dfe"
    ),
    MOBILECLIP_B(
        id = "mobileclip-b-lt-fp16-v1",
        displayName = "MobileCLIP B LT",
        tier = "Highest quality",
        description = "The strongest bundled option. Uses more memory and takes longer to analyze photos.",
        assetDirectory = "framefinder_models/mobileclip-b",
        imageSize = 224,
        visionSizeBytes = 172_963_522L,
        visionSha256 = "e816d8733baa2fdcc7b9b1868fa7b2e926435b9e6b2fba521d06d2ed6ce13bb0",
        textSizeBytes = 64_130_078L,
        textSha256 = "5f99a403620fa0463091003af67e386f1adbb596c3789ae1970f6fe4304bd726"
    );

    val visionAssetPath: String get() = "$assetDirectory/vision_model_fp16.onnx"
    val textAssetPath: String get() = "$assetDirectory/text_model_quantized.onnx"
    val tokenizerAssetPath: String get() = TOKENIZER_ASSET_PATH
    val totalSizeBytes: Long get() = visionSizeBytes + textSizeBytes + TOKENIZER_SIZE_BYTES
    val sizeMegabytes: Int get() = ((totalSizeBytes + MEBIBYTE / 2) / MEBIBYTE).toInt()

    fun artifacts(): List<ModelArtifact> = listOf(
        ModelArtifact(visionAssetPath, visionSizeBytes, visionSha256),
        ModelArtifact(textAssetPath, textSizeBytes, textSha256),
        ModelArtifact(tokenizerAssetPath, TOKENIZER_SIZE_BYTES, TOKENIZER_SHA256)
    )

    companion object {
        fun fromId(id: String?): SearchModel = entries.firstOrNull { it.id == id } ?: when (id) {
            "mobileclip-s0", "mobileclip-s0-fp16-bundled-v3", "mobileclip-s0-fp32-v2",
            "mobileclip-s1", "mobileclip-s1-fp16-bundled-v3", "mobileclip-s1-fp32-v2" -> MOBILECLIP_S2
            "mobileclip-s2", "mobileclip-s2-fp16-bundled-v3" -> MOBILECLIP_S2
            "mobileclip-b", "mobileclip-blt", "mobileclip-b-lt" -> MOBILECLIP_B
            else -> MOBILECLIP_S2
        }

        private const val MEBIBYTE = 1024L * 1024L
        private const val TOKENIZER_ASSET_PATH = "framefinder_models/shared/tokenizer.json"
        private const val TOKENIZER_SIZE_BYTES = 2_224_081L
        private const val TOKENIZER_SHA256 = "72ed5c96db5729294468543e4bc75fce14ca63f58e37300290189ba1c1e52b85"
    }
}
