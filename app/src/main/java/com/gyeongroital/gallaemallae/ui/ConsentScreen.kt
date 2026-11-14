package com.gyeongroital.gallaemallae.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 개인정보·위치정보 수집·이용 동의(법적 고지 + 명시적 동의). 첫 실행 때, 그리고 고지 내용이 바뀌었을 때 보인다.
 * - 동의는 "동의하고 시작" 버튼으로만 한다. Back·빈 곳 터치는 동의가 아니다(호출 측 BackHandler는 앱을 뒤로 보냄).
 * - 전문(개인정보처리방침·위치정보 이용약관)은 휴대폰으로 QR을 찍어 본다.
 * [withdrawMode]: 홈의 "개인정보·위치정보 안내"에서 연 경우. 이미 동의한 상태라 "동의 철회"를 보여 준다.
 */
@Composable
fun ConsentScreen(
    policyUrl: String,
    withdrawMode: Boolean,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onWithdraw: () -> Unit,
    onClose: () -> Unit,
) {
    var declined by remember { mutableStateOf(false) }
    var confirmWithdraw by remember { mutableStateOf(false) }
    ResponsivePanes(
        primary = {
            Text("위치정보 수집·이용 안내", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            Body("갈래말래는 목적지를 뽑고 여행 리캡을 만들기 위해 차량 위치를 사용해요.", muted = true)
            Card {
                Title("수집하는 정보")
                Body("현재 위치, 여행 중 이동 경로(좌표·시각), 목적지 도착 같은 여행 기록. 이름·연락처·차량 번호는 받지 않아요.")
            }
            Card {
                Title("이용 목적")
                Body("반경 안 랜덤 목적지 추천, 리캡 영상과 공유 링크 만들기")
            }
            Card {
                Title("보관 기간")
                Body("차량 안 기록: 새 여행을 시작하거나 기록을 지우면 삭제")
                Body("서버의 이동 경로: 리캡 영상을 만든 직후 삭제")
                Body("리캡 영상·공유 링크: 만든 날부터 30일 뒤 삭제 (출발지 주변 1.5km는 영상에 넣지 않아요)")
            }
            Card {
                Title("동의하지 않을 권리")
                Body("동의하지 않아도 돼요. 다만 위치가 없으면 목적지 추천과 리캡을 쓸 수 없어요. 동의는 홈 화면의 안내에서 언제든 철회할 수 있어요.")
            }
        },
        secondary = {
            Body("전체 내용(개인정보처리방침·위치정보 이용약관)은 휴대폰 카메라로 QR을 찍어 볼 수 있어요")
            QrCode(policyUrl, Modifier.align(Alignment.CenterHorizontally))
            Body(policyUrl, muted = true, center = true, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (withdrawMode) {
                PrimaryButton("닫기", onClose)
                SecondaryButton("동의 철회", { confirmWithdraw = true })
            } else {
                PrimaryButton("동의하고 시작", onAgree)
                SecondaryButton("동의하지 않음", { declined = true; onDecline() })
                if (declined) {
                    Card { Body("동의하지 않으면 갈래말래를 이용할 수 없어요. 필요할 때 다시 열어 동의해 주세요.", error = true) }
                }
            }
        },
    )
    if (confirmWithdraw) {
        ConfirmDialog(
            title = "동의를 철회할까요?",
            text = "철회하면 위치를 더 이상 사용하지 않아요. 다시 쓰려면 다시 동의해야 해요.",
            confirm = "철회",
            onConfirm = { confirmWithdraw = false; onWithdraw() },
            onDismiss = { confirmWithdraw = false },
        )
    }
}
