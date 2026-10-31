package app.duinch.adbagent.features.pairing.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ScreenshotPairingInfo(
    val code: String,
    val filePath: String?,
    val uri: Uri
)

class ScreenshotPairingWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCodeExtracted: (ScreenshotPairingInfo) -> Unit
) {
    private val contentResolver = context.contentResolver
    private var observer: ContentObserver? = null
    private var lastHandledUri: Uri? = null
    private var lastHandledTimestamp: Long = 0L
    private var debounceJob: Job? = null

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun start() {
        if (observer != null) return
        val handler = Handler(Looper.getMainLooper())
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                handleStorageChange(uri)
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer!!
        )
    }

    fun stop() {
        debounceJob?.cancel()
        observer?.let {
            contentResolver.unregisterContentObserver(it)
            observer = null
        }
    }

    private fun handleStorageChange(changedUri: Uri?) {
        val now = System.currentTimeMillis()
        if (now - lastHandledTimestamp < 1200L && changedUri == lastHandledUri) return

        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.IO) {
            delay(400L) // Wait briefly for file write completion
            inspectLatestScreenshot()
        }
    }

    private fun inspectLatestScreenshot() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.DATA
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        runCatching {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dataIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                val id = cursor.getLong(idIdx)
                val displayName = cursor.getString(nameIdx).orEmpty()
                val dateAdded = cursor.getLong(dateIdx)
                val filePath = if (dataIdx != -1) cursor.getString(dataIdx) else null

                val nowSec = System.currentTimeMillis() / 1000L
                // Check if screenshot was taken within the last 15 seconds
                if (nowSec - dateAdded > 15) return

                val isScreenshot = displayName.contains("screenshot", ignoreCase = true) ||
                    displayName.contains("screen_shot", ignoreCase = true)
                if (!isScreenshot) return

                val targetUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                if (targetUri == lastHandledUri) return
                lastHandledUri = targetUri
                lastHandledTimestamp = System.currentTimeMillis()

                processImageUri(targetUri, filePath)
            }
        }
    }

    private fun processImageUri(uri: Uri, filePath: String?) {
        val image = runCatching {
            InputImage.fromFilePath(context, uri)
        }.getOrNull() ?: return

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text
                val extractedCode = parsePairingCodeFromOcrText(fullText)
                if (extractedCode != null) {
                    onCodeExtracted(
                        ScreenshotPairingInfo(
                            code = extractedCode,
                            filePath = filePath,
                            uri = uri
                        )
                    )
                }
            }
    }

    companion object {
        fun parsePairingCodeFromOcrText(text: String): String? {
            // Find 6 continuous digits (optionally with spaces, e.g., '123 456' or '123456')
            val regexExact = Regex("\\b(\\d{6})\\b")
            val matchExact = regexExact.find(text)
            if (matchExact != null) {
                return matchExact.groupValues[1]
            }

            val regexSpaced = Regex("\\b(\\d{3})\\s+(\\d{3})\\b")
            val matchSpaced = regexSpaced.find(text)
            if (matchSpaced != null) {
                return "${matchSpaced.groupValues[1]}${matchSpaced.groupValues[2]}"
            }
            return null
        }
    }
}
