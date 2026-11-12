package ai.randomdrive.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaviTripCheckTest {

    @Test
    fun normalRoadDetourIsShown() {
        assertTrue(NaviTripCheck.isPlausible(straightKm = 20.4, naviKm = 27.0))
        assertTrue(NaviTripCheck.isPlausible(straightKm = 44.0, naviKm = 69.0))
    }

    @Test
    fun shortTripWithBigRatioButSmallGapIsShown() {
        // 직선 3km, 도로 9km: 비율은 3배지만 차이 6km라 실제로 있을 법함
        assertTrue(NaviTripCheck.isPlausible(straightKm = 3.0, naviKm = 9.0))
    }

    @Test
    fun routeFromDifferentOriginIsHidden() {
        // 에뮬레이터 실측: 직선 13km인데 내비 35km (내비 위치가 시흥동 고정)
        assertFalse(NaviTripCheck.isPlausible(straightKm = 13.0, naviKm = 35.0))
    }

    @Test
    fun roadShorterThanStraightLineIsHidden() {
        // 에뮬레이터 실측: 직선 18km인데 내비 10km
        assertFalse(NaviTripCheck.isPlausible(straightKm = 18.3, naviKm = 10.0))
    }

    @Test
    fun missingValuesAreHidden() {
        assertFalse(NaviTripCheck.isPlausible(straightKm = 10.0, naviKm = 0.0))
        assertFalse(NaviTripCheck.isPlausible(straightKm = 0.0, naviKm = 5.0))
    }
}
