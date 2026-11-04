package app.duinch.adbagent.features.pairing.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
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
import kotlin.math.abs

class ScreenshotPairingWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCodeExtracted: (code: String, port: Int?, screenshotUri: Uri) -> Unit
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
            MediaStore.Images.Media.DATE_ADDED
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

                val id = cursor.getLong(idIdx)
                val displayName = cursor.getString(nameIdx).orEmpty()
                val dateAdded = cursor.getLong(dateIdx)

                val nowSec = System.currentTimeMillis() / 1000L
                if (abs(nowSec - dateAdded) > 60) return

                val lower = displayName.lowercase()
                val isScreenshot = lower.contains("screenshot") ||
                    lower.contains("screen_shot") ||
                    lower.contains("ảnh_chụp_màn_hình") ||
                    lower.contains("anh_chup_man_hinh") ||
                    lower.contains("capture")
                if (!isScreenshot) return

                val targetUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                if (targetUri == lastHandledUri) return
                lastHandledUri = targetUri
                lastHandledTimestamp = System.currentTimeMillis()

                processImageUri(targetUri)
            }
        }
    }

    private fun processImageUri(uri: Uri) {
        val image = runCatching {
            InputImage.fromFilePath(context, uri)
        }.getOrNull() ?: return

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text
                val extractedCode = parsePairingCodeFromOcrText(fullText)
                val extractedPort = parsePairingPortFromOcrText(fullText, extractedCode)
                if (extractedCode != null) {
                    onCodeExtracted(extractedCode, extractedPort, uri)
                }
            }
    }

    companion object {
        fun parsePairingCodeFromOcrText(text: String): String? {
            // The code sits alone on its line; a bare six digits elsewhere could be anything.
            val regexOwnLine = Regex("""(?m)^\s*(\d{6})\s*$""")
            regexOwnLine.find(text)?.let { return it.groupValues[1] }

            val regexExact = Regex("""\b(\d{6})\b""")
            val matchExact = regexExact.find(text)
            if (matchExact != null) {
                return matchExact.groupValues[1]
            }

            val regexSpaced = Regex("""\b(\d{3})\s+(\d{3})\b""")
            val matchSpaced = regexSpaced.find(text)
            if (matchSpaced != null) {
                return "${matchSpaced.groupValues[1]}${matchSpaced.groupValues[2]}"
            }

            val regexHyphen = Regex("""\b(\d{3})\s*-\s*(\d{3})\b""")
            val matchHyphen = regexHyphen.find(text)
            if (matchHyphen != null) {
                return "${matchHyphen.groupValues[1]}${matchHyphen.groupValues[2]}"
            }
            return null
        }

        /**
         * The Settings page behind the pairing dialog shows the connect port too, in the same
         * IP:port form. The pairing port is the one printed below the code, in the dialog.
         */
        fun parsePairingPortFromOcrText(text: String, code: String? = null): Int? {
            val regex = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\s*:\s*(\d{4,5})\b""")
            val matches = regex.findAll(text).toList()
            val codeIndex = code?.let { text.indexOf(it) } ?: -1
            val match = matches.firstOrNull { codeIndex >= 0 && it.range.first > codeIndex }
                ?: matches.lastOrNull()
                ?: return null
            val port = match.groupValues[1].toIntOrNull() ?: return null
            return if (port in 1024..65535) port else null
        }
    }
}
