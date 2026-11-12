package eureto.opendoor.network

import android.util.Base64
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

/**
 * Odpowiedzialny za generowanie podpisu HMAC-SHA256 wymaganego przez eWeLink API.
 * Wykorzystywany zarówno przez żądania HTTP (AuthInterceptor) jak i połączenia WebSocket.
 */
object EwelinkSigner {

    /**
     * Format sygnatury: HMAC-SHA256(stringToSign, apikey)
     * stringToSign = "appid=${appid}&nonce=${nonce}&seq=${seq}&ts=${ts}&version=${version}&{uri}"
     *
     * @param requestBody treść body żądania (nie jest używana w samym stringToSign, zachowana dla kompatybilności sygnatury)
     */
    fun generateSign(
        method: String,
        uri: String,
        requestBody: String?,
        params: Map<String, String>,
        accessToken: String,
        apiKey: String,
        nonce: String,
        timestamp: String,
        version: String,
        appId: String
    ): String {
        try {
            val tsInSeconds = (timestamp.toLong() / 1000).toString() // eWeLink zazwyczaj używa sekund

            // Parametry do podpisu, posortowane alfabetycznie
            val signParams = mutableMapOf<String, String>()
            signParams["appid"] = appId
            signParams["nonce"] = nonce
            signParams["ts"] = tsInSeconds
            signParams["version"] = version

            params.forEach { (key, value) ->
                signParams[key] = value
            }

            val sortedString = signParams.toSortedMap().map { "${it.key}=${it.value}" }.joinToString("&")

            val stringToSign = "${sortedString}&${uri}"

            val hmacSha256 = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(apiKey.toByteArray(StandardCharsets.UTF_8), "HmacSha256")
            hmacSha256.init(secretKey)

            val hash = hmacSha256.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8))

            return Base64.encodeToString(hash, Base64.NO_WRAP)

        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}
