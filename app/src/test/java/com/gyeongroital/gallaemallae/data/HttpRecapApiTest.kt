package com.gyeongroital.gallaemallae.data

import com.gyeongroital.gallaemallae.domain.RecapStatus
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** JDK 내장 HttpServer로 서버 응답을 흉내 내서 HttpRecapApi의 오류 처리를 확인한다. */
class HttpRecapApiTest {
    private lateinit var server: HttpServer
    private var status = 200
    private var body = ""
    private var lastRequest = ""
    private var lastAppKey: String? = null

    @Before fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            lastAppKey = ex.requestHeaders.getFirst("X-App-Key")
            lastRequest = ex.requestMethod + " " + ex.requestURI + " " + ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val bytes = body.toByteArray(Charsets.UTF_8)
            ex.sendResponseHeaders(status, bytes.size.toLong().coerceAtLeast(1).takeIf { bytes.isNotEmpty() } ?: -1)
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        server.start()
    }

    @After fun tearDown() = server.stop(0)

    private fun api() = HttpRecapApi("http://127.0.0.1:${server.address.port}")

    private suspend fun expectCode(code: String, block: suspend () -> Unit): ApiException {
        try {
            block()
            fail("expected ApiException $code")
        } catch (e: ApiException) {
            assertEquals(code, e.code)
            return e
        }
        throw AssertionError()
    }

    @Test fun `정상 응답`() = runTest {
        body = """{"tripId":"t_1","status":"RUNNING","shareUrl":null,"videoUrl":null,"summary":null}"""
        assertEquals(RecapStatus.RUNNING, api().getRecap("t_1").status)
        assertTrue(lastRequest.startsWith("GET /api/v1/trips/t_1/recap"))
    }

    @Test fun `앱 키가 있으면 X-App-Key 헤더로 보낸다`() = runTest {
        body = """{"tripId":"t_1","status":"RUNNING"}"""
        HttpRecapApi("http://127.0.0.1:${server.address.port}", apiKey = "k-123").getRecap("t_1")
        assertEquals("k-123", lastAppKey)
    }

    @Test fun `앱 키가 비어 있으면 헤더를 보내지 않는다`() = runTest {
        body = """{"tripId":"t_1","status":"RUNNING"}"""
        api().getRecap("t_1")
        assertEquals(null, lastAppKey)
    }

    @Test fun `401·429는 서버 message를 그대로 전달하고 자동 재시도하지 않는다`() = runTest {
        status = 429
        body = """{"code":"RATE_LIMITED","message":"요청이 너무 많아요. 잠시 후 다시 시도해 주세요"}"""
        val e = expectCode("RATE_LIMITED") { api().getRecap("t_1") }
        assertEquals("요청이 너무 많아요. 잠시 후 다시 시도해 주세요", e.message)
        assertTrue(!e.retryable)
        status = 401
        body = """{"code":"UNAUTHORIZED","message":"앱 인증에 실패했어요"}"""
        assertEquals("앱 인증에 실패했어요", expectCode("UNAUTHORIZED") { api().getRecap("t_1") }.message)
    }

    @Test fun `지역 이름은 서버에 묻고 앱 키를 붙인다`() = runTest {
        body = """{"full":"서울특별시 금천구 시흥동","short":"금천구 시흥동"}"""
        val area = HttpRecapApi("http://127.0.0.1:${server.address.port}", apiKey = "k-1").areaName(37.4527, 126.9037)
        assertEquals("금천구 시흥동", area?.short)
        assertTrue(lastRequest.startsWith("GET /api/v1/geo/area?lat=37.4527&lng=126.9037"))
        assertEquals("k-1", lastAppKey)
    }

    @Test fun `지역 이름 실패는 예외 없이 null`() = runTest {
        status = 429
        body = """{"code":"RATE_LIMITED","message":"요청이 너무 많아요"}"""
        assertEquals(null, api().areaName(37.5, 127.0))
    }

    @Test fun `200인데 HTML이면 앱이 죽지 않고 BAD_RESPONSE`() = runTest {
        body = "<html>captive portal</html>"
        expectCode(ApiException.CODE_BAD_RESPONSE) { api().getRecap("t_1") }
    }

    @Test fun `필수 필드 누락도 BAD_RESPONSE`() = runTest {
        body = """{"tripId":"t_1","status":"DONE","summary":{"distanceKm":1.0}}"""
        expectCode(ApiException.CODE_BAD_RESPONSE) { api().getRecap("t_1") }
    }

    @Test fun `서버 오류 바디의 code를 그대로 전달, 4xx는 재시도 안 함`() = runTest {
        status = 404
        body = """{"code":"TRIP_NOT_FOUND","message":"trip not found: t_x"}"""
        val e = expectCode("TRIP_NOT_FOUND") { api().getRecap("t_x") }
        assertEquals(404, e.httpStatus)
        assertTrue(!e.retryable)
    }

    @Test fun `5xx는 재시도 대상`() = runTest {
        status = 503
        body = "unavailable"
        assertTrue(expectCode("HTTP_503") { api().getRecap("t_1") }.retryable)
    }

    @Test fun `연결 실패는 NETWORK`() = runTest {
        val port = server.address.port
        server.stop(0)
        expectCode(ApiException.CODE_NETWORK) { HttpRecapApi("http://127.0.0.1:$port").getRecap("t_1") }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
    }

    @Test fun `random 요청 바디는 계약 필드`() = runTest {
        body = """{"tripId":"t_2","destination":{"placeId":"p","name":"n","lat":1.0,"lng":2.0,"address":"a","imageUrl":null,"distanceKm":3.0}}"""
        api().randomDestination(37.5, 127.0, 50, demo = true)
        assertTrue(lastRequest, lastRequest.startsWith("POST /api/v1/destinations/random"))
        assertTrue(lastRequest, lastRequest.contains("\"radiusKm\":50") && lastRequest.contains("\"demo\":true"))
    }
}
