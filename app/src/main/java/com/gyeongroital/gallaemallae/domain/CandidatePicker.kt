package com.gyeongroital.gallaemallae.domain

import kotlin.random.Random

data class Candidate(val placeId: String, val name: String, val lat: Double, val lng: Double, val address: String) {
    val latLng get() = LatLng(lat, lng)
}

/**
 * 반경 기반 랜덤 목적지 추첨 (MVP F-01). 반경은 직선거리 기준.
 * [minKm] 미만 후보는 "너무 가까운 목적지"라 제외한다.
 */
class CandidatePicker(private val random: Random = Random.Default) {

    fun candidatesWithin(origin: LatLng, radiusKm: Int, all: List<Candidate>, minKm: Double = MIN_KM): List<Pair<Candidate, Double>> =
        all.map { it to Geo.distanceMeters(origin, it.latLng) / 1000.0 }
            .filter { (_, km) -> km in minKm..radiusKm.toDouble() }

    fun pick(origin: LatLng, radiusKm: Int, all: List<Candidate>, excludeIds: Set<String> = emptySet()): Destination? {
        val inRange = candidatesWithin(origin, radiusKm, all)
        val pool = inRange.filter { it.first.placeId !in excludeIds }.ifEmpty { inRange }
        if (pool.isEmpty()) return null
        val (c, km) = pool[random.nextInt(pool.size)]
        return Destination(c.placeId, c.name, c.lat, c.lng, c.address, imageUrl = null, distanceKm = km)
    }

    companion object {
        const val MIN_KM = 3.0
        val RADIUS_OPTIONS_KM = listOf(50, 100, 150)
        const val DEMO_PLACE_ID = "demo-namhansanseong"
    }
}
