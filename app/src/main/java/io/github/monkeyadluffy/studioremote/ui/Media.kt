package io.github.monkeyadluffy.studioremote.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_FILE_BYTES = 50L * 1024 * 1024
private const val MAX_IMAGE_SIDE = 1280

class PickedFile(val name: String, val mime: String, val bytes: ByteArray)

/** Reads a picked document; null when unreadable or over 50 MB. */
suspend fun readDocument(context: Context, uri: Uri): PickedFile? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        var name = "dosya"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size > MAX_FILE_BYTES) return@runCatching null
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        if (bytes.size > MAX_FILE_BYTES) return@runCatching null
        PickedFile(name, resolver.getType(uri) ?: "application/octet-stream", bytes)
    }.getOrNull()
}

/** Downscales to ≤1280 px and JPEG-encodes as a data URL for the OpenAI-style image part. */
suspend fun encodeImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_IMAGE_SIDE) {
                val scale = MAX_IMAGE_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

/** Renders a `data:` image URL; a blank tile when it can't be decoded. */
@Composable
fun DataImage(dataUrl: String, modifier: Modifier, scale: ContentScale = ContentScale.Fit) {
    val bitmap = remember(dataUrl) {
        runCatching {
            val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) Image(bitmap, null, modifier, contentScale = scale)
    else Box(modifier.background(C.SurfaceHigh))
}
