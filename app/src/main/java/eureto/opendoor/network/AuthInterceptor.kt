package eureto.opendoor.network

import android.util.Base64 // Import dla Base64 (od API 26+), dla starszych użyj commons-codec
import eureto.opendoor.data.AppPreferences // Zmień nazwę pakietu
import eureto.opendoor.network.model.LoginResponse // Zmień nazwę pakietu
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.net.URLEncoder
import java.nio.charset.StandardCharsets // Wymaga API 19
//TODO: usuń nieużywane importy

/**
 * This interceptor handles OAuth2 token management and request signing for eWeLink API.
 * It automatically adds the necessary headers to each request, including the Authorization header
 * with the Bearer token, and signs the request using HMAC-SHA256 as required by eWeLink.
 */
class AuthInterceptor(
    private val appPreferences: AppPreferences,
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String
) : Interceptor {

    private val authRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://oauth.ewelink.cc/") // Bazowy URL dla endpointów OAuth (będzie zmieniony dynamicznie w EwelinkApiClient)
            .client(OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val authService: EwelinkAuthService by lazy {
        authRetrofit.create(EwelinkAuthService::class.java)
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Nie dodawaj nagłówków autoryzacji do żądań OAuth2
        if (originalRequest.url.toString().contains("oauth/token")) {
            return chain.proceed(originalRequest)
        }

        val accessToken = appPreferences.getAccessToken()
        val region = appPreferences.getRegion()

        // Jeśli brakuje tokenów lub regionu, nie możemy kontynuować z autoryzowanym żądaniem
        if (accessToken.isNullOrEmpty() || region.isNullOrEmpty()) { // apiKey może być null, obsłużymy to niżej
            // Możesz przekierować użytkownika do logowania lub zgłosić błąd
            // Na potrzeby testów, po prostu zwróć błąd
            throw IOException("Brak tokenów autoryzacyjnych lub regionu. Użytkownik musi się zalogować.")
        }

        val requestBuilder = originalRequest.newBuilder()

        val apiKeyForSigning = clientSecret

        // Tworzymy nagłówki wymagane przez eWeLink API
        val nonce = generateNonce(8)
        val currentTimestamp = System.currentTimeMillis()
        val version = 8 // Typowa wersja API dla eWeLink, może wymagać aktualizacji

        // Tak ogolnie to wydaje mi sie ze tu jest w chuj za duzo parametrow bo np getDevice czy ten token
        // to pobiera tak z 3/4 parametry a tu jest ich w chuj
        // no ale oni nie sprawdzaja co ja im dodatkowo wysylam tylko czy dostali to co potrzebuja
        // wiec na razie to zostawiam
        // ale powinoo byc zrobione porzondnie hehehe

        // Generowanie podpisu
        val sign = generateSign(
            method = originalRequest.method,
            uri = originalRequest.url.encodedPath,
            requestBody = originalRequest.body?.readContent(), // Treść body, jeśli istnieje
            params = originalRequest.url.queryParameterNames.associateWith { originalRequest.url.queryParameter(it) ?: "" },
            accessToken = accessToken,
            apiKey = apiKeyForSigning, // Użyj wybranego klucza do podpisu
            nonce = nonce,
            timestamp = currentTimestamp.toString(),
            version = version.toString(),
            appId = clientId
        )

        requestBuilder
            .header("Authorization", "Bearer $accessToken")
            .header("X-CK-Appid", clientId)
            .header("X-CK-Nonce", nonce)
            .header("X-CK-Ts", (currentTimestamp / 1000).toString()) // eWeLink często używa sekund
            .header("X-CK-Sign", sign)
            .header("X-CK-Region", region) // Dodaj region, jeśli wymagany jest przez endpoint
            .header("X-CK-Device-Id", appPreferences.getSelectedDeviceId() ?: "") // Może być wymagany ID urządzenia
            .header("X-CK-UA", "Android") // User Agent

        var response = chain.proceed(requestBuilder.build())

        // Obsługa odświeżania tokenu, jeśli wygasł (kody 401 Unauthorized lub 403 Forbidden)
        if (response.code == 401 || response.code == 403) {
            val refreshToken = appPreferences.getRefreshToken()
            if (!refreshToken.isNullOrEmpty()) {
                synchronized(this) {
                    val newAccessToken = appPreferences.getAccessToken()
                    // Sprawdź ponownie, czy token nie został już odświeżony przez inny wątek
                    if (newAccessToken != accessToken) {
                        response.close() // Zamknij poprzednią odpowiedź
                        // Powtórz żądanie z nowym tokenem
                        val newRequest = originalRequest.newBuilder()
                            .header("Authorization", "Bearer $newAccessToken")
                            // Ponownie wygeneruj sign, bo zmienił się access token i prawdopodobnie apikey
                            .header("X-CK-Nonce", generateNonce()) // Nowy nonce
                            .header("X-CK-Ts", (System.currentTimeMillis() / 1000).toString())
                            .header("X-CK-Sign", generateSign( // Generuj nowy podpis z nowymi danymi
                                method = originalRequest.method,
                                uri = originalRequest.url.encodedPath,
                                requestBody = originalRequest.body?.readContent(),
                                params = originalRequest.url.queryParameterNames.associateWith { originalRequest.url.queryParameter(it) ?: "" },
                                accessToken = newAccessToken ?: "", // Użyj nowego tokena (dodano ?: "")
                                apiKey = clientSecret, // Użyj aktualnego API key lub clientSecret
                                nonce = generateNonce(), // Nowy nonce
                                timestamp = (System.currentTimeMillis() / 1000).toString(),
                                version = version.toString(),
                                appId = clientId
                            ))
                            .build()
                        return chain.proceed(newRequest)
                    }

                    // Odśwież token
                    val refreshResponse: LoginResponse? = try {
                        runBlocking {
                            authService.refreshAccessToken(
                                "refresh_token",
                                refreshToken,
                                clientId,
                                clientSecret
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }

                    // Użyj safe call operatora '?.' dla dostępu do 'data' oraz 'accessToken'
                    if (refreshResponse?.data != null && !refreshResponse.data.accessToken.isNullOrEmpty()) {
                        // Zapisz nowe tokeny
                        appPreferences.saveLoginData(refreshResponse)
                        response.close() // Zamknij poprzednią odpowiedź
                        // Powtórz oryginalne żądanie z nowym tokenem
                        val newRequest = originalRequest.newBuilder()
                            // Użyj safe call operatora '?.' dla dostępu do 'data' oraz 'accessToken'
                            .header("Authorization", "Bearer ${refreshResponse.data.accessToken}")
                            // Ponownie wygeneruj sign, bo zmienił się access token i prawdopodobnie apikey
                            .header("X-CK-Nonce", generateNonce()) // Nowy nonce
                            .header("X-CK-Ts", (System.currentTimeMillis() / 1000).toString())
                            .header("X-CK-Sign", generateSign( // Generuj nowy podpis z nowymi danymi
                                method = originalRequest.method,
                                uri = originalRequest.url.encodedPath,
                                requestBody = originalRequest.body?.readContent(),
                                params = originalRequest.url.queryParameterNames.associateWith { originalRequest.url.queryParameter(it) ?: "" },
                                accessToken = refreshResponse.data.accessToken, // Użyj access tokenu z data
                                apiKey = clientSecret, // Użyj nowego API key z data lub clientSecret
                                nonce = generateNonce(), // Nowy nonce
                                timestamp = (System.currentTimeMillis() / 1000).toString(),
                                version = version.toString(),
                                appId = clientId
                            ))
                            .build()
                        return chain.proceed(newRequest)
                    }
                }
            }
        }
        return response
    }

    // Pomocnicza funkcja do odczytywania body żądania bez jego zużywania
    private fun okhttp3.RequestBody.readContent(): String? {
        val buffer = okio.Buffer()
        writeTo(buffer)
        return buffer.readString(StandardCharsets.UTF_8)
    }

    private fun generateNonce(length: Int): String {
        val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    private fun generateNonce(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

    /**
     * Implementacja algorytmu podpisu HMAC-SHA256 dla eWeLink API.
     * Na podstawie analizy popularnych bibliotek (np. ewelink-api-next).
     *
     * Format sygnatury: HMAC-SHA256(stringToSign, apikey)
     * stringToSign = "appid=${appid}&nonce=${nonce}&seq=${seq}&ts=${ts}&version=${version}&{uri}"
     *
     * UWAGA: Parametry do podpisu mogą się różnić w zależności od wersji API i typu żądania.
     * To jest jedna z najczęściej spotykanych implementacji.
     * Musisz sprawdzić, czy Twój "kod" z konta deweloperskiego podaje inny algorygorytm lub parametry.
     */
    private fun generateSign(
        method: String,
        uri: String,
        requestBody: String?,
        params: Map<String, String>,
        accessToken: String,
        apiKey: String, // Ten klucz zostanie zawsze przekazany jako non-null
        nonce: String,
        timestamp: String, // Unix timestamp in milliseconds, convert to seconds if API expects
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

            // Dodaj parametry z URI (query parameters)
            params.forEach { (key, value) ->
                signParams[key] = value
            }

            // Dodaj body, jeśli istnieje (często hasz MD5 z body lub samo body w stringu)
            // Ta część jest bardzo zmienna i zależy od API.
            // Dla prostych POSTów często używa się "params" jako JSON string, ale nie jest on częścią podpisu stringu
            // W niektórych implementacjach MD5 z body jest dodawane do podpisu, ale nie w stringToSign.

            // Posortuj wszystkie parametry alfabetycznie i zbuduj stringToSign
            val sortedString = signParams.toSortedMap().map { "${it.key}=${it.value}" }.joinToString("&")

            // final String to sign includes the URI path
            val stringToSign = "${sortedString}&${uri}"

            val hmacSha256 = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(apiKey.toByteArray(StandardCharsets.UTF_8), "HmacSha256")
            hmacSha256.init(secretKey)

            val hash = hmacSha256.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8))

            // Konwertuj bajty na Base64 (bez zawijania w wiersze, bez Paddingu)
            // Użyj standardowej klasy Base64 z Androida lub commons-codec
            return Base64.encodeToString(hash, Base64.NO_WRAP)

        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "" // Zwróć pusty string w przypadku błędu
    }
}