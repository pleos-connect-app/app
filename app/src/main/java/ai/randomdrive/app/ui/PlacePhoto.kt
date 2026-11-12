package ai.randomdrive.app.ui

import ai.randomdrive.app.ui.theme.Tokens
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 목적지 대표 사진. 서버가 주는 이미지는 한국관광공사 TourAPI 사진이라 출처 표시가 필요하고,
 * 일부는 공공누리 3유형(변경 금지)이라 자르지 않고 비율 그대로 보여 준다(ContentScale.Fit).
 * 받아 오지 못하면 아무것도 그리지 않는다(사진 없이도 화면이 성립해야 함).
 */
@Composable
fun PlacePhoto(url: String?, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) return
    val bitmap by produceState<Bitmap?>(PhotoCache.peek(url), url) {
        if (value == null) value = PhotoCache.load(url)
    }
    val b = bitmap ?: return
    Column(modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Tokens.iviCardRadius),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.fillMaxWidth().height(Tokens.iviPhotoHeight), contentAlignment = Alignment.Center) {
                Image(
                    b.asImageBitmap(),
                    contentDescription = "목적지 사진",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(Tokens.iviPhotoHeight).clip(RoundedCornerShape(Tokens.iviCardRadius)),
                )
            }
        }
        Text(PHOTO_CREDIT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val PHOTO_CREDIT = "사진: 한국관광공사"

/** 작은 메모리 캐시 + 다운샘플링 로더. 라이브러리 없이 HttpURLConnection으로 충분한 규모. */
private object PhotoCache {
    private const val TAG = "PlacePhoto"
    private const val MAX_EDGE_PX = 1024
    private const val TIMEOUT_MS = 10_000

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun peek(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null // 릴리스는 평문 HTTP 차단
        runCatching {
            val bytes = (URL(url).openConnection() as HttpURLConnection).run {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                try {
                    if (responseCode !in 200..299) error("HTTP $responseCode")
                    inputStream.use { it.readBytes() }
                } finally {
                    disconnect()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE_PX) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.onFailure { Log.w(TAG, "photo load failed: ${it.message}") }
            .getOrNull()
            ?.also { cache.put(url, it) }
    }
}
