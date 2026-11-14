package com.gyeongroital.gallaemallae.domain

/**
 * 좌표 없이 온 경유지(에뮬레이터 내비는 0,0)의 좌표를 이름·주소 검색으로 찾는다.
 *
 * 이름만 물으면 엉뚱한 곳이 나올 수 있다("양평두물머리" → 수원). 그래서
 * 1) "이름 주소"를 함께 묻고, 2) 안 되면 이름·주소를 따로 물어 이름 결과가 주소에서
 * [MAX_FROM_ADDRESS_M]보다 멀면 버리고 주소 좌표를 쓴다.
 *
 * [search]는 못 찾거나 실패하면 null을 돌려주고 예외를 던지지 않는다.
 */
class WaypointResolver(private val search: suspend (String) -> LatLng?) {

    suspend fun resolve(name: String, address: String?): LatLng? {
        val addr = address?.trim()?.takeIf { it.isNotEmpty() && it != name }
            ?: return search(name)
        search("$name $addr")?.let { return it }
        val byName = search(name)
        val byAddr = search(addr)
        return when {
            byName != null && byAddr != null ->
                if (Geo.distanceMeters(byName, byAddr) <= MAX_FROM_ADDRESS_M) byName else byAddr
            else -> byName ?: byAddr
        }
    }

    companion object {
        /** 내비 주소는 "양평군 양서면"처럼 읍·면·동까지라, 그 중심에서 이 정도 안이면 같은 곳으로 본다. */
        const val MAX_FROM_ADDRESS_M = 15_000.0
    }
}
