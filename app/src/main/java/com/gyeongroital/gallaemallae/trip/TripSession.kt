package com.gyeongroital.gallaemallae.trip

import com.gyeongroital.gallaemallae.domain.ArrivalSource
import com.gyeongroital.gallaemallae.domain.Destination
import com.gyeongroital.gallaemallae.domain.LatLng
import com.gyeongroital.gallaemallae.domain.RecapSummary
import com.gyeongroital.gallaemallae.domain.TripEvent

/** 여행 한 건의 진행 단계. 화면 복원 기준. */
enum class TripPhase {
    /** 내비 안내 시작, GPS 기록 중. */
    RECORDING,
    /** 도착 판정(내비 이벤트 또는 앱 자체 판정) → 기록 종료, 리캡 만들기 대기. */
    ARRIVED,
    /** 여행 종료 확정 → 업로드/리캡 생성 진행 중. */
    FINISHING,
    /** 리캡 완성. */
    DONE,
}

data class TripSession(
    val tripId: String,
    val destination: Destination,
    val origin: LatLng,
    val originLabel: String,
    val radiusKm: Int,
    val phase: TripPhase,
    val startedAt: Long,
    val endedAt: Long? = null,
    val arrivalSource: ArrivalSource? = null,
    val events: List<TripEvent> = emptyList(),
    /** finish API 성공 여부(재시도·복원 시 중복 전송 판단용. 서버는 멱등이라 다시 보내도 안전). */
    val finishAccepted: Boolean = false,
    val shareUrl: String? = null,
    val thumbnailUrl: String? = null,
    val summary: RecapSummary? = null,
    /** 앱이 계산한 요약(서버 요약이 없을 때 표시). */
    val localSummary: RecapSummary? = null,
    val pointCount: Int = 0,
    /** 마지막 GPS 수신 시각(epoch ms). 여행 화면의 "위치 수신 없음" 경고용. */
    val lastPointAt: Long? = null,
)
