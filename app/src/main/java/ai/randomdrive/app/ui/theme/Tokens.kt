package ai.randomdrive.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 디자인 토큰 모음. Pleos 디자인 가이드의 컬러 hex·타입 스케일·컴포넌트 크기는 원문에 이미지로만 있어
 * 텍스트로 확인된 기준(대비 ≥4.5:1, 글자 ≥18px 권장)만 지키는 임시 값이다.
 * TODO(pleos-token): Figma(Pleos_Connect_design_assets_215.fig) 값으로 교체. 이름은 ivi_ 접두사 규칙을 따름.
 */
object Tokens {
    // TODO(pleos-token) colors — 어두운 배경 + 밝은 글자(대비 > 7:1)
    val iviColorBackground = Color(0xFF101418)
    val iviColorSurface = Color(0xFF1C2228)
    val iviColorSurfaceRaised = Color(0xFF262E36)
    val iviColorOnSurface = Color(0xFFF2F4F6)
    val iviColorOnSurfaceVariant = Color(0xFFB8C0C8)
    val iviColorPrimary = Color(0xFF4DA3FF)
    val iviColorOnPrimary = Color(0xFF00121F)
    val iviColorError = Color(0xFFFF8A80)
    val iviColorSuccess = Color(0xFF7BE0A0)
    /** 추첨 화면에서 최종 목적지 핀 색(일반 탐색 핀은 primary). */
    val iviColorHighlight = Color(0xFFFFC94D)

    val iviColorBackgroundLight = Color(0xFFF5F7F9)
    val iviColorSurfaceLight = Color(0xFFFFFFFF)
    val iviColorSurfaceRaisedLight = Color(0xFFE9EDF1)
    val iviColorOnSurfaceLight = Color(0xFF111418)
    val iviColorOnSurfaceVariantLight = Color(0xFF4A535C)
    val iviColorPrimaryLight = Color(0xFF0B5CAD)

    // TODO(pleos-token) typography (sp). 최소 18 이상.
    val iviFontSizeDisplay = 44.sp
    val iviFontSizeTitle = 32.sp
    val iviFontSizeBody = 22.sp
    val iviFontSizeCaption = 18.sp

    // TODO(pleos-token) dimensions
    val iviSpacingSmall = 12.dp
    val iviSpacingMedium = 24.dp
    val iviSpacingLarge = 40.dp
    val iviButtonHeight = 88.dp
    val iviButtonRadius = 20.dp
    val iviCardRadius = 24.dp
    val iviContentMaxWidth = 880.dp
    /** 이 폭 이상이면 2단(가로) 레이아웃. */
    val iviTwoPaneMinWidth = 1000.dp
    val iviQrSize = 320.dp
    val iviPhotoHeight = 220.dp

    // TODO(pleos-token) motion — 가이드의 duration/easing 값은 이미지로만 있어 임시 값. 추첨 연출 총 길이 약 2.5초.
    /** 서버 응답이 빨라도 핀이 이 시간만큼은 돌아다닌다. */
    const val iviMotionDrawMinSpinMs = 1_000L
    /** 탐색 중 핀이 옮겨 가는 간격. */
    const val iviMotionDrawHopMs = 110L
    /** 착지 직전 점점 느려지는 간격들. */
    val iviMotionDrawLandingHopsMs = listOf(150L, 210L, 290L, 380L)
    /** 착지 후 목적지 이름을 보여 주는 시간. */
    const val iviMotionDrawHoldMs = 900L

    const val PRESSED_SCALE = 0.95f // 가이드: press 시 95% 스케일
}
