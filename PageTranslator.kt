package eu.kanade.tachiyomi.ui.reader.translate

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class TranslatedBlock(
    val box: Rect,
    val original: String,
    val translated: String,
)

/**
 * On-device manga page translation: OCR (ML Kit) -> translation (ML Kit).
 * Language models are downloaded once, then everything works offline.
 */
class PageTranslator(
    sourceLang: String = TranslateLanguage.ENGLISH,
    targetLang: String = TranslateLanguage.ARABIC,
) {
    private val recognizer: TextRecognizer = when (sourceLang) {
        TranslateLanguage.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        TranslateLanguage.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build(),
    )

    suspend fun translate(page: Bitmap): List<TranslatedBlock> {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        val result = recognizer.process(InputImage.fromBitmap(page, 0)).await()
        return result.textBlocks.mapNotNull { block ->
            val box = block.boundingBox ?: return@mapNotNull null
            val text = block.text.replace("\n", " ").trim()
            if (text.isBlank()) return@mapNotNull null
            TranslatedBlock(box, text, translator.translate(text).await())
        }
    }

    fun close() {
        recognizer.close()
        translator.close()
    }
}
