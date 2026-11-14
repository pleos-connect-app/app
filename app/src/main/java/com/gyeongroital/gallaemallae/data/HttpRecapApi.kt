package com.gyeongroital.gallaemallae.data

import com.gyeongroital.gallaemallae.domain.Recap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 실서버 구현. HttpURLConnection + org.json (추가 라이브러리 없음). */
/** [apiKey]: 서버 앱 키. 비어 있지 않으면 모든 요청에 X-App-Key 헤더로 보낸다(서버 계약, 401 UNAUTHORIZED 방지). */
class HttpRecapApi(private val baseUrl: String, private val apiKey: String = "") : RecapApi {

    // excludePlaceIds는 아직 서버 계약에 없어 보내지 않는다(서버 작업 요청서 3-1). 대신 화면 쪽에서 같은 곳이 나오면 다시 요청한다.
    override suspend fun randomDestination(lat: Double, lng: Double, radiusKm: Int, demo: Boolean, excludePlaceIds: Set<String>) =
        request("POST", "/api/v1/destinations/random", RecapJson.randomDestinationRequest(lat, lng, radiusKm, demo),
            RecapJson::parseRandomDestination)

    override suspend fun finishTrip(tripId: String, request: FinishTripRequest) =
        request("POST", "/api/v1/trips/${enc(tripId)}/finish", RecapJson.finishRequest(request), RecapJson::parseStatus)

    override suspend fun getRecap(tripId: String): Recap =
        request("GET", "/api/v1/trips/${enc(tripId)}/recap", null, RecapJson::parseRecap)

    override suspend fun areaName(lat: Double, lng: Double): AreaName? = try {
        request("GET", "/api/v1/geo/area?lat=$lat&lng=$lng", null, RecapJson::parseArea)
    } catch (e: ApiException) {
        null // 이름은 없어도 화면이 성립한다("현재 위치")
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /** 응답 파싱까지 이 안에서 한다 — 형식이 깨진 응답(HTML 프록시 페이지, 필드 누락)도 ApiException으로 바뀌어 앱이 죽지 않게. */
    private suspend fun <T> request(method: String, path: String, body: String?, parse: (String) -> T): T = withContext(Dispatchers.IO) {
        val conn = try {
            (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection)
        } catch (e: IOException) {
            throw ApiException(ApiException.CODE_NETWORK, "서버에 연결할 수 없어요", cause = e)
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) conn.setRequestProperty("X-App-Key", apiKey)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            if (code !in 200..299) {
                val (errCode, msg) = RecapJson.parseError(text) ?: ("HTTP_$code" to "서버 오류 ($code)")
                throw ApiException(errCode, msg, httpStatus = code)
            }
            parse(text ?: throw ApiException(ApiException.CODE_BAD_RESPONSE, "빈 응답", httpStatus = code))
        } catch (e: ApiException) {
            throw e
        } catch (e: JSONException) {
            throw ApiException(ApiException.CODE_BAD_RESPONSE, "응답 형식 오류", cause = e)
        } catch (e: IllegalArgumentException) {
            throw ApiException(ApiException.CODE_BAD_RESPONSE, "응답 형식 오류", cause = e)
        } catch (e: IOException) {
            throw ApiException(ApiException.CODE_NETWORK, "서버에 연결할 수 없어요", cause = e)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
    }
}
