package ai.randomdrive.app.data

import ai.randomdrive.app.domain.Recap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 실서버 구현. HttpURLConnection + org.json (추가 라이브러리 없음). */
class HttpRecapApi(private val baseUrl: String) : RecapApi {

    override suspend fun randomDestination(lat: Double, lng: Double, radiusKm: Int, demo: Boolean, excludePlaceIds: Set<String>) =
        RecapJson.parseRandomDestination(
            request("POST", "/api/v1/destinations/random", RecapJson.randomDestinationRequest(lat, lng, radiusKm, demo)),
        )

    override suspend fun finishTrip(tripId: String, request: FinishTripRequest) =
        RecapJson.parseStatus(request("POST", "/api/v1/trips/${enc(tripId)}/finish", RecapJson.finishRequest(request)))

    override suspend fun getRecap(tripId: String): Recap =
        RecapJson.parseRecap(request("GET", "/api/v1/trips/${enc(tripId)}/recap", null))

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private suspend fun request(method: String, path: String, body: String?): String = withContext(Dispatchers.IO) {
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
            text ?: throw ApiException(ApiException.CODE_BAD_RESPONSE, "빈 응답", httpStatus = code)
        } catch (e: ApiException) {
            throw e
        } catch (e: JSONException) {
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
