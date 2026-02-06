package eureto.opendoor.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import eureto.opendoor.data.AppPreferences // Zmień nazwę pakietu
import eureto.opendoor.databinding.ActivityLoginBinding // Zmień nazwę pakietu
import eureto.opendoor.network.EwelinkApiClient // Zmień nazwę pakietu
import kotlinx.coroutines.launch
import java.util.UUID
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64 // Import dla Base64
import eureto.opendoor.network.model.AccessTokenRequestBody
import com.google.gson.Gson

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var appPreferences: AppPreferences
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPreferences = EwelinkApiClient.getAppPreferences()

        binding.btnLogin.setOnClickListener {
            startOAuthLogin()
        }

        // Check if user is already signed in
        if (!appPreferences.getAccessToken().isNullOrEmpty()) {
            navigateToMain()
        }
    }

    // Obsługa powrotu z przekierowania OAuth2
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val uri = intent.data
        Log.d("LoginActivity", "Otrzymano URI: $uri")

        val redirectUri = appPreferences.getRedirectUri()
        val expectedScheme = redirectUri.split("://")[0]
        val expectedHost = redirectUri.split("://")[1]

        if (uri != null && uri.scheme == expectedScheme && uri.host == expectedHost) {
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")

            Log.d("LoginActivity", "OAuth Callback - Code: $code, State: $state")

            if (!code.isNullOrEmpty()) {
                Log.d("LoginActivity", "Otrzymano kod autoryzacji: $code")
                exchangeCodeForTokens(code)
            } else {
                Toast.makeText(this, "Błąd autoryzacji: brak kodu.", Toast.LENGTH_LONG).show()
                Log.e("LoginActivity", "OAuth callback missing code. URI: $uri")
            }
        } else {
            Toast.makeText(this, "Nieprawidłowe przekierowanie OAuth.", Toast.LENGTH_LONG).show()
            Log.e("LoginActivity", "Invalid OAuth redirect URI: $uri")
        }
    }

    private fun startOAuthLogin() {
        val currentTimestamp = System.currentTimeMillis().toString()
        val nonce = generateNonce(8)

        val clientId = appPreferences.getClientId()
        val redirectUrl = appPreferences.getRedirectUri()
        val grantType = "authorization_code"
        val state = generateRandomState()

        val authorizationSign = generateOAuthUrlParamSign(
            clientId = clientId,
            seq = currentTimestamp,
            clientSecret = appPreferences.getClientSecret()
        )


        val authUrl = Uri.parse("https://c2ccdn.coolkit.cc/oauth/index.html")
            .buildUpon()
            .appendQueryParameter("clientId", clientId)
            .appendQueryParameter("redirectUrl", redirectUrl)
            .appendQueryParameter("grantType", grantType)
            .appendQueryParameter("state", state)
            .appendQueryParameter("nonce", nonce)
            .appendQueryParameter("seq", currentTimestamp)
            .appendQueryParameter("authorization", authorizationSign)
            .build()
            .toString()

        Log.d("LoginActivity", "Otwieranie URL autoryzacji: $authUrl")
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        startActivity(browserIntent)
    }

    private fun exchangeCodeForTokens(code: String) {
        lifecycleScope.launch {
            try {
                val authService = EwelinkApiClient.createAuthService()

                val nonceHeader = generateNonce(8)
                val requestBody = AccessTokenRequestBody(
                    code = code,
                    redirectUrl = appPreferences.getRedirectUri(),
                    grantType = "authorization_code"
                )

                val authorizationHeader = generateAccessTokenHeaderSign(
                    requestBody = requestBody,
                    clientSecret = appPreferences.getClientSecret()
                )

                val loginResponse = authService.getAccessToken(
                    nonce = nonceHeader,
                    auth = authorizationHeader,
                    appid = appPreferences.getClientId(),
                    requestBody = requestBody
                )


                if (loginResponse.error != 0) {
                    val errorMessage = "Błąd serwera: ${loginResponse.msg} (Kod: ${loginResponse.error})"
                    Log.e("LoginActivity", errorMessage)
                    Toast.makeText(this@LoginActivity, "Błąd logowania: $errorMessage", Toast.LENGTH_LONG).show()
                    return@launch // Zakończ coroutine w przypadku błędu
                }

                if (loginResponse.data == null) {
                    val errorMessage = "Błąd: Odpowiedź serwera nie zawiera obiektu 'data'."
                    Log.e("LoginActivity", errorMessage)
                    Toast.makeText(this@LoginActivity, "Błąd logowania: $errorMessage", Toast.LENGTH_LONG).show()
                    return@launch
                }


                appPreferences.saveLoginData(loginResponse)

                Log.d("LoginActivity", "Login successful. AccessToken: ${loginResponse.data.accessToken?.take(10)}..., RefreshToken: ${loginResponse.data.refreshToken?.take(10)}..., Region: ${appPreferences.getRegion()}")
                Toast.makeText(this@LoginActivity, "Zalogowano pomyślnie!", Toast.LENGTH_SHORT).show()
                Log.d("LoginActivity", "Zalogowano pomyślnie. Region: ${appPreferences.getRegion()}")
                navigateToMain()
            } catch (e: Exception) {
                Log.e("LoginActivity", "Błąd wymiany kodu na tokeny: ${e.message}", e)
                Toast.makeText(this@LoginActivity, "Błąd logowania: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun generateRandomState(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Generuje alfanumeryczny string o podanej długości.
     * @param length długość nonce (np. 8 dla 8-znakowego)
     */
    private fun generateNonce(length: Int): String {
        val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    /**
     * Generuje podpis dla parametru 'authorization' w początkowym URL-u OAuth.
     * Zakłada algorytm HMAC-SHA256 z clientSecret jako kluczem
     * i posortowanym alfabetycznie ciągiem parametrów (clientId, redirectUrl, grantType, state, nonce, seq) jako danymi do podpisania.
     */
    private fun generateOAuthUrlParamSign(
        clientId: String,
        seq: String, // timestamp w ms
        clientSecret: String
    ): String {
        try {
            val stringToSign = "${clientId}_${seq}"

            val hmacSha256 = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(clientSecret.toByteArray(StandardCharsets.UTF_8), "HmacSha256")
            hmacSha256.init(secretKey)

            val hash = hmacSha256.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8))

            return Base64.encodeToString(hash, Base64.NO_WRAP)

        } catch (e: NoSuchAlgorithmException) {
            Log.e("LoginActivity", "NoSuchAlgorithmException podczas generowania OAuth URL sign: ${e.message}", e)
        } catch (e: InvalidKeyException) {
            Log.e("LoginActivity", "InvalidKeyException podczas generowania OAuth URL sign: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("LoginActivity", "Błąd podczas generowania OAuth URL sign: ${e.message}", e)
        }
        return ""
    }

    /**
     * Generuje podpis HMAC-SHA256 dla nagłówka 'Authorization' w żądaniu wymiany tokenu.
     * stringToSign = JSON_string_of_request_body
     * @return Zwraca hash Base64 z prefiksem "Sign ".
     */
    private fun generateAccessTokenHeaderSign(
        requestBody: AccessTokenRequestBody,
        clientSecret: String
    ): String {
        try {
            // Konwersja obiektu requestBody na string JSON
            val stringToSign = gson.toJson(requestBody)
            Log.d("LoginActivity", "String to sign for Access Token Header: $stringToSign")

            val hmacSha256 = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(clientSecret.toByteArray(StandardCharsets.UTF_8), "HmacSha256")
            hmacSha256.init(secretKey)

            val hash = hmacSha256.doFinal(stringToSign.toByteArray(StandardCharsets.UTF_8))

            val base64Hash = Base64.encodeToString(hash, Base64.NO_WRAP)

            return "Sign $base64Hash" // Zawsze dodawaj prefiks "Sign " dla nagłówka
        } catch (e: NoSuchAlgorithmException) {
            Log.e("LoginActivity", "NoSuchAlgorithmException podczas generowania Access Token Header sign: ${e.message}", e)
        } catch (e: InvalidKeyException) {
            Log.e("LoginActivity", "InvalidKeyException podczas generowania Access Token Header sign: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("LoginActivity", "Błąd podczas generowania Access Token Header sign: ${e.message}", e)
        }
        return ""
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}