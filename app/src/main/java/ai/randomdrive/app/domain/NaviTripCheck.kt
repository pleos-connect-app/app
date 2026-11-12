package ai.randomdrive.app.domain

/**
 * 내비가 알려 준 도로 거리가 앱이 아는 직선거리와 말이 되는지 본다.
 *
 * 내비는 자기 현재 위치에서 경로를 계산한다. 에뮬레이터 내비는 위치가 시흥동에 고정돼 있고(운영사무국 확인),
 * 실차에서도 내비 위치가 아직 안 잡혔으면 같은 일이 생길 수 있다. 출발점이 다르면 숫자가 어긋나
 * "직선 13km인데 내비 35km·1시간 29분"처럼 보이므로, 그런 값은 화면에 보여 주지 않는다.
 */
object NaviTripCheck {
    /** 도로 거리는 직선거리보다 짧을 수 없다. GPS 오차를 감안해 10% 여유. */
    private const val MIN_RATIO = 0.9

    /** 짧은 거리에서 GPS·장소 좌표 오차를 감안한 절대 여유(km). */
    private const val ABS_TOLERANCE_KM = 0.5

    /** 이보다 크게 돌아가는 경로는 출발점이 다른 것으로 본다. */
    private const val MAX_RATIO = 2.5

    /** 가까운 목적지는 비율이 쉽게 커지므로 차이가 이만큼은 나야 이상하다고 본다. */
    private const val MAX_EXTRA_KM = 10.0

    fun isPlausible(straightKm: Double, naviKm: Double): Boolean {
        if (straightKm <= 0 || naviKm <= 0) return false
        if (naviKm < straightKm * MIN_RATIO - ABS_TOLERANCE_KM) return false
        return !(naviKm > straightKm * MAX_RATIO && naviKm - straightKm > MAX_EXTRA_KM)
    }
}
