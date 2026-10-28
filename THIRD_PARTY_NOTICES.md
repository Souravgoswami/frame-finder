# Third-party notices

Frame Finder depends on AndroidX, Jetpack Compose, Coil, ONNX Runtime, and Google ML Kit libraries through Gradle. Their licence metadata is included in their published artifacts.

## Google ML Kit Text Recognition

Frame Finder bundles Google ML Kit's Latin text-recognition model so OCR is available offline without a separate download.

- Android integration: <https://developers.google.com/ml-kit/vision/text-recognition/v2/android>
- Supported scripts: <https://developers.google.com/ml-kit/vision/text-recognition/v2/languages>

## MobileCLIP

The S2 and B LT models bundled with Frame Finder originate from Apple's MobileCLIP project and are subject to the Apple Machine Learning Research Model License Agreement, not the Frame Finder source-code terms.

- Project: <https://github.com/apple-aiml-research/ml-mobileclip>
- Model licence: <https://github.com/apple-aiml-research/ml-mobileclip/blob/main/LICENSE_MODELS>
- ONNX conversions used by the app: <https://huggingface.co/Xenova/mobileclip_s2> and <https://huggingface.co/Xenova/mobileclip_blt>

Users should review the model licence before distributing an application or model bundle for commercial use.

## ONNX Runtime

ONNX Runtime is distributed by Microsoft under the MIT Licence.

- Project: <https://github.com/microsoft/onnxruntime>
- Licence: <https://github.com/microsoft/onnxruntime/blob/main/LICENSE>
