package com.gyeongroital.gallaemallae.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaypointResolverTest {
    private val dumulmeori = LatLng(37.5341, 127.3170)
    private val suwon = LatLng(37.2641, 127.0240)      // "양평두물머리"만 물으면 VWorld가 주는 엉뚱한 곳
    private val yangseo = LatLng(37.5412, 127.3803)    // "양평군 양서면"

    private fun resolver(answers: Map<String, LatLng>, asked: MutableList<String> = mutableListOf()) =
        WaypointResolver { q -> asked += q; answers[q] }

    @Test fun `이름과 주소를 함께 물어 찾으면 그 좌표`() = runBlocking {
        val asked = mutableListOf<String>()
        val r = resolver(mapOf("양평두물머리 양평군 양서면" to dumulmeori, "양평두물머리" to suwon), asked)
        assertEquals(dumulmeori, r.resolve("양평두물머리", "양평군 양서면"))
        assertEquals(listOf("양평두물머리 양평군 양서면"), asked)
    }

    @Test fun `이름 결과가 주소에서 멀면 주소 좌표`() = runBlocking {
        val r = resolver(mapOf("양평두물머리" to suwon, "양평군 양서면" to yangseo))
        assertEquals(yangseo, r.resolve("양평두물머리", "양평군 양서면"))
    }

    @Test fun `이름 결과가 주소 근처면 이름 좌표`() = runBlocking {
        val r = resolver(mapOf("양평두물머리" to dumulmeori, "양평군 양서면" to yangseo))
        assertEquals(dumulmeori, r.resolve("양평두물머리", "양평군 양서면"))
    }

    @Test fun `주소가 없으면 이름만`() = runBlocking {
        assertEquals(dumulmeori, resolver(mapOf("두물머리" to dumulmeori)).resolve("두물머리", null))
    }

    @Test fun `주소가 이름과 같으면 한 번만 묻는다`() = runBlocking {
        val asked = mutableListOf<String>()
        val addr = "서울특별시 양천구 목동중앙남로 11-15 (목동)"
        val p = LatLng(37.535, 126.8688)
        assertEquals(p, resolver(mapOf(addr to p), asked).resolve(addr, addr))
        assertEquals(listOf(addr), asked)
    }

    @Test fun `어디서도 못 찾으면 null`() = runBlocking {
        assertNull(resolver(emptyMap()).resolve("스타벅스 양평점", "양평군 양평읍"))
    }

    @Test fun `이름으로만 찾히면 이름 좌표`() = runBlocking {
        assertEquals(dumulmeori, resolver(mapOf("양평두물머리" to dumulmeori)).resolve("양평두물머리", "양평군 양서면"))
    }
}
