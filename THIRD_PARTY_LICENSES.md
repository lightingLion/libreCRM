# Third-party licenses

## LXGW WenKai Lite

- Project: https://github.com/lxgw/LxgwWenKai-Lite
- Bundled files: LXGW WenKai Lite v1.530 static Regular and Medium TTF files.
  The upstream Lite release does not ship a Bold file, so headings use
  the real Medium face rather than a synthetic or claimed Bold weight.
- License: SIL Open Font License 1.1, including the upstream additional
  permission for reserved names. The complete upstream license is copied to
  app/src/main/assets/licenses/OFL-LXGW-WenKai.txt.
- The font is embedded as an application asset, not sold as a standalone font.

## AndroidX, Jetpack Compose, Room, and DataStore

These Google AndroidX libraries are distributed under the Apache License 2.0.
Their notices are available from the corresponding artifacts and upstream
AndroidX repositories. No advertising or analytics SDK is included.

## PaddleOCR PP-OCRv6 small

- Android deployment SDK source: PaddleOCR, Apache License 2.0.
- Bundled detector: `PP-OCRv6_small_det_onnx`.
- Bundled recognizer and Chinese character dictionary: `PP-OCRv6_small_rec_onnx`.
- Model cards declare Apache-2.0. The full PaddleOCR Apache 2.0 license is in
  `app/src/main/assets/licenses/LICENSE-PaddleOCR-Apache-2.0.txt`.
- Both model files and the recognition configuration are packaged for local
  inference; first-use model download and image upload are not required.

## ONNX Runtime Android 1.21.1

- Artifact: `com.microsoft.onnxruntime:onnxruntime-android:1.21.1`.
- License: MIT; full license is in
  `app/src/main/assets/licenses/LICENSE-ONNX-Runtime-MIT.txt`.
- Bundled dependency notices are in
  `app/src/main/assets/licenses/THIRD-PARTY-NOTICES-ONNX-Runtime.txt`.
- NNAPI is attempted first. The NNAPI driver can select a supported accelerator
  on the device; unsupported or failing execution falls back to ONNX Runtime's
  CPU provider. This build does not bundle Qualcomm QNN SDK libraries.

## OpenCV Android 4.5.3

- Artifact: `com.quickbirdstudios:opencv:4.5.3`.
- License: Apache License 2.0; full license is in
  `app/src/main/assets/licenses/LICENSE-OpenCV-Apache-2.0.txt`.
- OpenCV is used for OCR preprocessing and table-rule extraction.

## HanLP portable 1.8.6

- Artifact: `com.hankcs:hanlp:portable-1.8.6` (about 8.0 MB before APK
  optimization).
- HanLP v1.8.6 provides Chinese person-name NER and is run locally on OCR text.
- Unused organization, place, pinyin, synonym, and traditional-conversion
  dictionaries are excluded from the APK; the core segmentation and person
  dictionaries remain bundled.
- License: Apache License 2.0; the complete license is in
  `app/src/main/assets/licenses/LICENSE-HanLP-Apache-2.0.txt`.
- The `ValidX` project also publishes a broad Chinese-name shape check
  (2–50 Han characters and middle-dot names); shape checks alone do not
  distinguish a customer from a property name, so they are only a fallback.

## PaddlePaddle SLANet_plus ONNX

- Model: `PaddlePaddle/SLANet_plus_onnx` from the official PaddlePaddle Hugging Face repository.
- Bundled file: `app/src/main/assets/ocr/models/table/inference.onnx` (7,782,138 bytes).
- License: Apache License 2.0, as declared by the model repository. The full
  PaddleOCR Apache 2.0 license is bundled at
  `app/src/main/assets/licenses/LICENSE-PaddleOCR-Apache-2.0.txt`.
- SHA-256: `7790C0C13CE064782C9D22EBEB16B4DA8216F83D3BA576DA962C106EF58386DA`.
- Used offline to identify table structure and map OCR text boxes to cells.
