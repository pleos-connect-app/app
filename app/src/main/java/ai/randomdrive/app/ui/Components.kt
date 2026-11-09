package ai.randomdrive.app.ui

import ai.randomdrive.app.ui.theme.Tokens
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** 큰 기본 버튼. press 시 95% 스케일(가이드), 손가락이 벗어나면 취소(기본 동작). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = RoundedCornerShape(Tokens.iviButtonRadius),
        modifier = modifier.fillMaxWidth().height(Tokens.iviButtonHeight).scale(if (pressed) Tokens.PRESSED_SCALE else 1f),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = RoundedCornerShape(Tokens.iviButtonRadius),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurfaceVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.fillMaxWidth().height(Tokens.iviButtonHeight).scale(if (pressed) Tokens.PRESSED_SCALE else 1f),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Tokens.iviCardRadius),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Tokens.iviSpacingMedium), verticalArrangement = Arrangement.spacedBy(Tokens.iviSpacingSmall), content = content)
    }
}

@Composable
fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)

@Composable
fun Body(text: String, modifier: Modifier = Modifier, muted: Boolean = false, error: Boolean = false, center: Boolean = false) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    color = when {
        error -> MaterialTheme.colorScheme.error
        muted -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    },
    textAlign = if (center) TextAlign.Center else null,
    modifier = modifier,
)

/**
 * 반응형 화면 틀. 넓으면(가로·2 window) 좌우 2단, 좁으면(세로·1 window) 위아래로 쌓는다.
 * Driving view 영역은 시스템이 관리하므로 앱은 주어진 창 크기만 사용한다.
 */
@Composable
fun ResponsivePanes(
    primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(Tokens.iviSpacingLarge)) {
        if (maxWidth >= Tokens.iviTwoPaneMinWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.iviSpacingLarge), modifier = Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.iviSpacingMedium), content = primary)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.iviSpacingMedium), content = secondary)
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = Tokens.iviContentMaxWidth).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Tokens.iviSpacingMedium),
                ) {
                    primary()
                    secondary()
                }
            }
        }
    }
}

/** 공유 URL → QR. 네트워크·GMS 없이 기기에서 생성(ZXing core). */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { qrBitmap(content, 512) }
    Box(modifier.size(Tokens.iviQrSize).semantics { contentDescription = "리캡 영상 QR 코드" }) {
        Image(bitmap.asImageBitmap(), contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
    }
}

private fun qrBitmap(content: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 2))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
