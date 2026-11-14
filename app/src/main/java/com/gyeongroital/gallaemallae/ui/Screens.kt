package com.gyeongroital.gallaemallae.ui

import com.gyeongroital.gallaemallae.domain.ArrivalSource
import com.gyeongroital.gallaemallae.domain.MapCamera
import com.gyeongroital.gallaemallae.domain.NaviTripCheck
import com.gyeongroital.gallaemallae.domain.WebMercator
import com.gyeongroital.gallaemallae.navi.NaviConnection
import com.gyeongroital.gallaemallae.trip.TripPhase
import com.gyeongroital.gallaemallae.trip.TripSession
import com.gyeongroital.gallaemallae.ui.theme.Tokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

// 이 앱은 DO(주행 중 사용)를 선언하지 않는다. 아래 모든 화면은 정차(P) 중에만 보이며,
// 주행 중에는 시스템이 "주행 중에는 사용할 수 없습니다" 화면으로 대체한다(에뮬레이터 실측).

// ---------------- 홈: 반경 선택 ----------------

@Composable
fun HomeScreen(
    state: UiState,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onDraw: (Int) -> Unit,
    onRetryNavi: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    var radius by remember { mutableStateOf(MainViewModel.RADIUS_OPTIONS.first()) }
    val ready = state.location is LocationState.Ready
    // 추첨 화면 위성 지도를 미리 받아 둔다(반경·대략적인 위치가 바뀔 때만). 추첨 화면과 같은 창 크기로 계산.
    val here = (state.location as? LocationState.Ready)?.point?.latLng
    val hereKey = here?.let { (it.lat * 100).roundToInt() to (it.lng * 100).roundToInt() }
    val conf = LocalConfiguration.current
    val density = LocalDensity.current.density
    LaunchedEffect(radius, hereKey, conf.screenWidthDp, conf.screenHeightDp) {
        val p = here ?: return@LaunchedEffect
        val w = (conf.screenWidthDp * density).toInt()
        val h = (conf.screenHeightDp * density).toInt()
        SatelliteTiles.prefetch(MapCamera(p, WebMercator.fitZoom(p.lat, radius * 1000.0, w, h)), w, h)
    }
    ResponsivePanes(
        primary = {
            Text("갈래말래", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            Body("반경만 고르면 오늘의 목적지를 뽑아 드려요", muted = true)
            here?.let {
                RadiusPreviewMap(it, radius, MainViewModel.RADIUS_OPTIONS, Modifier.fillMaxWidth().height(Tokens.iviMapHeight))
            }
            Card {
                Body("출발 위치", muted = true)
                when (val loc = state.location) {
                    LocationState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Body("  현재 위치를 확인하는 중…")
                    }
                    LocationState.NoPermission -> {
                        Body("목적지를 고르려면 위치 권한이 필요해요. 위치는 여행 기록 생성에만 사용해요.")
                        SecondaryButton("위치 권한 허용", onRequestPermission)
                    }
                    LocationState.Disabled -> {
                        Body("위치 기능이 꺼져 있어요.")
                        SecondaryButton("위치 설정 열기", onOpenLocationSettings)
                    }
                    is LocationState.Ready -> {
                        // 좌표 대신 시·구·동. 이름을 아직 모르면(받는 중·지원 안 됨) "현재 위치"
                        Title(loc.address ?: loc.label)
                    }
                }
            }
            NaviStatus(state.naviConnection, onRetryNavi)
        },
        secondary = {
            Title("얼마나 멀리 갈까요?")
            RadiusSelector(selected = radius, onSelect = { radius = it; onDismissMessage() })
            // 오류는 잠깐 뜨는 알림 대신 다음 행동 전까지 화면에 남긴다(주행 환경에서 놓치기 쉬움)
            state.message?.let { Card { Body(it, error = true) } }
            PrimaryButton("목적지 뽑기", onClick = { onDraw(radius) }, enabled = ready)
            if (state.usingFakeServer) Body("개발용: 앱 내장 후보 목록 사용 중 (서버 미연결)", muted = true)
        },
    )
}

@Composable
private fun RadiusSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.iviSpacingSmall), modifier = Modifier.fillMaxWidth()) {
        MainViewModel.RADIUS_OPTIONS.forEach { km ->
            val label = "${km}km"
            if (km == selected) {
                PrimaryButton(label, onClick = { onSelect(km) }, modifier = Modifier.weight(1f))
            } else {
                SecondaryButton(label, onClick = { onSelect(km) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NaviStatus(connection: NaviConnection, onRetry: () -> Unit) {
    when (connection) {
        NaviConnection.CONNECTED -> Body("내비게이션 연결됨", muted = true)
        NaviConnection.CONNECTING -> Body("내비게이션 연결 중…", muted = true)
        NaviConnection.FAILED -> Card {
            Body("내비게이션에 연결하지 못했어요", error = true)
            SecondaryButton("다시 연결", onRetry)
        }
    }
}

// ---------------- 추첨 결과 ----------------

@Composable
fun ResultScreen(r: PreTrip.Result, message: String?, onStart: () -> Unit, onReroll: () -> Unit, onBack: () -> Unit) {
    ResponsivePanes(
        primary = {
            Body("오늘의 랜덤 목적지", muted = true)
            Text(r.destination.name, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            Body(r.destination.address, muted = true)
            PlacePhoto(r.destination.imageUrl)
            Card {
                Body("${r.originLabel}에서 직선 ${formatKm(r.destination.distanceKm)}")
                when {
                    r.naviTripLoading -> Body("내비 경로 계산 중…", muted = true)
                    r.naviTrip != null && NaviTripCheck.isPlausible(r.destination.distanceKm, r.naviTrip.distanceM / 1000.0) ->
                        Body("내비 기준 약 ${formatDuration(r.naviTrip.etaS)} · ${formatKm(r.naviTrip.distanceM / 1000.0)}")
                    // 내비 출발점이 앱 위치와 다르면(에뮬레이터 내비 위치 고정 등) 어긋난 숫자를 보여 주지 않는다
                    r.naviTrip != null -> Body("예상 시간은 내비게이션 안내에서 확인할 수 있어요", muted = true)
                    else -> Body("내비 경로 정보를 가져오지 못했어요", muted = true)
                }
            }
        },
        secondary = {
            // 다시 뽑기 직전 위치를 잃은 경우 등 ViewModel 메시지도 여기서 보여 준다
            message?.let { Body(it, error = true) }
            if (r.sendError != null) Body(r.sendError, error = true)
            if (r.sending) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Body("  내비게이션에 목적지 전달 중…")
                }
            }
            PrimaryButton(if (r.sendError != null) "다시 시도" else "내비게이션 시작", onStart, enabled = !r.sending)
            SecondaryButton("다시 뽑기", onReroll, enabled = !r.sending)
            TextButton(onClick = onBack, enabled = !r.sending) { Body("반경 다시 고르기", muted = true) }
        },
    )
}

// ---------------- 여행 중 / 도착 ----------------

@Composable
fun TripScreen(s: TripSession, onShowNavi: () -> Unit, onEnd: () -> Unit, onDiscard: () -> Unit) {
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val arrived = s.phase == TripPhase.ARRIVED
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(arrived) {
        while (!arrived) { // 화면이 보일 때만 돈다(Compose는 백그라운드에서 프레임을 그리지 않음)
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    ResponsivePanes(
        primary = {
            Body(if (arrived) "도착했어요!" else "여행 기록 중", muted = !arrived)
            Text(s.destination.name, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            Card {
                val end = s.endedAt ?: now
                Body("${s.originLabel} 출발 · ${formatElapsed(end - s.startedAt)}")
                Body("기록한 위치 ${s.pointCount}개 · 이벤트 ${s.events.size}개", muted = true)
                val lastFix = s.lastPointAt ?: s.startedAt
                if (!arrived && now - lastFix > NO_FIX_WARNING_MS) {
                    Body("위치 신호를 받지 못하고 있어요. 위치 설정을 확인해 주세요", error = true)
                }
                if (arrived) {
                    Body(
                        if (s.arrivalSource == ArrivalSource.NAVI_EVENT) "내비게이션이 도착을 알려왔어요" else "목적지 근처에 도착한 것을 확인했어요",
                        muted = true,
                    )
                }
            }
        },
        secondary = {
            if (arrived) {
                PrimaryButton("리캡 영상 만들기", onEnd)
            } else {
                PrimaryButton("내비게이션으로 돌아가기", onShowNavi)
                SecondaryButton("여행 종료", { confirmEnd = true })
                // 앱이 2칸을 다 쓰고 있으면 내비가 가려진 상태. 창 배치는 시스템(홈 화면)이 정하고 앱이 요청할
                // 공개 API가 없어(에뮬레이터·SDK 확인), 사용자가 직접 나눌 수 있게 방법만 알려 준다.
                if (LocalConfiguration.current.screenWidthDp >= Tokens.iviTwoPaneMinWidth.value) {
                    Card { Body("오른쪽 끝 ⋮ 손잡이를 왼쪽으로 끌면 내비와 나란히 볼 수 있어요", muted = true) }
                }
            }
            TextButton(onClick = { confirmDiscard = true }) { Body("기록 취소", muted = true) }
        },
    )
    if (confirmEnd) {
        ConfirmDialog(
            title = "여행을 끝낼까요?",
            text = "지금까지 기록한 경로로 리캡 영상을 만들어요.",
            confirm = "여행 종료",
            onConfirm = { confirmEnd = false; onEnd() },
            onDismiss = { confirmEnd = false },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "기록을 취소할까요?",
            text = "기록한 경로가 삭제되고 리캡을 만들지 않아요. 내비게이션 안내는 계속돼요.",
            confirm = "기록 취소",
            onConfirm = { confirmDiscard = false; onDiscard() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Title(title) },
        text = { Body(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Body(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Body("돌아가기", muted = true) } },
    )
}

// ---------------- 리캡 생성 중 ----------------

@Composable
fun FinishingScreen(s: TripSession, finish: FinishState, onRetry: () -> Unit, onDiscard: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Tokens.iviSpacingLarge), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Tokens.iviSpacingMedium),
            modifier = Modifier.fillMaxWidth(0.7f),
        ) {
            Title("${s.destination.name} 여행 리캡")
            when (finish) {
                FinishState.Uploading -> { CircularProgressIndicator(); Body("여행 기록을 보내는 중…", center = true) }
                FinishState.Generating -> { CircularProgressIndicator(); Body("리캡 영상을 만드는 중…", center = true) }
                is FinishState.Error -> {
                    Body(finish.message, error = true, center = true)
                    if (finish.canRetry) PrimaryButton("다시 시도", onRetry)
                    SecondaryButton("처음으로", onDiscard)
                }
            }
        }
    }
}

// ---------------- 리캡 완성 ----------------

@Composable
fun RecapScreen(s: TripSession, onNewTrip: () -> Unit) {
    val summary = s.summary ?: s.localSummary
    ResponsivePanes(
        primary = {
            Body("리캡 영상 완성", muted = true)
            Text("${s.originLabel} → ${s.destination.name}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            RecapThumbnail(s.thumbnailUrl, s.destination.imageUrl)
            if (summary != null) {
                Card {
                    Body("이동 거리 ${formatKm(summary.distanceKm)}")
                    Body("이동 시간 ${formatMinutes(summary.durationMin)}")
                    Body("예상 밖 정차 ${summary.stopCount}회")
                }
            }
            PrimaryButton("새 여행 시작", onNewTrip)
        },
        secondary = {
            val url = s.shareUrl
            if (url != null) {
                Body("휴대폰 카메라로 QR을 찍으면 영상을 보고 저장할 수 있어요")
                QrCode(url, Modifier.align(Alignment.CenterHorizontally))
            } else {
                Body("공유 링크를 받지 못했어요", error = true)
            }
        },
    )
}

private const val NO_FIX_WARNING_MS = 30_000L

// ---------------- 포맷 ----------------

internal fun formatKm(km: Double): String = if (km < 10) String.format(Locale.US, "%.1fkm", km) else "${Math.round(km)}km"

internal fun formatDuration(seconds: Int): String = formatMinutes((seconds + 59) / 60)

internal fun formatMinutes(min: Int): String = when {
    min < 1 -> "1분 미만"
    min < 60 -> "${min}분"
    min % 60 == 0 -> "${min / 60}시간"
    else -> "${min / 60}시간 ${min % 60}분"
}

internal fun formatElapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}
