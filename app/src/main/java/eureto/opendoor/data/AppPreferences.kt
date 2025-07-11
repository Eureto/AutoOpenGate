package eureto.opendoor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import eureto.opendoor.BuildConfig
import eureto.opendoor.network.model.LoginResponse // Zmień nazwę pakietu
//TODO: Zmień Depracated na aktualną nazwę pakietu


class AppPreferences(private val context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val encryptedSharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "secure_ewelink_prefs", // Nazwa pliku SharedPreferences
            masterKeyAlias,         // Alias klucza (String)
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Zastąp tymi danymi swoimi wartościami z konta deweloperskiego eWeLink
    private val CLIENT_ID = BuildConfig.CLIENT_ID
    private val CLIENT_SECRET = BuildConfig.CLIENT_SECRET
    private val REDIRECT_URI = "euretoapp://oauth2callback" // Musi pasować do AndroidManifest.xml

    fun getClientId(): String = CLIENT_ID
    fun getClientSecret(): String = CLIENT_SECRET
    fun getRedirectUri(): String = REDIRECT_URI

    fun saveLoginData(loginResponse: LoginResponse) {
        with(encryptedSharedPreferences.edit()) {
            // Bezpieczne zapisywanie pól z zagnieżdżonego obiektu 'data'
            putString("access_token", loginResponse.data?.accessToken)
            putString("refresh_token", loginResponse.data?.refreshToken)

            loginResponse.data?.atExpiredTime?.let { putLong("access_token_expires_at", it) } ?: remove("access_token_expires_at")
            loginResponse.data?.rtExpiredTime?.let { putLong("refresh_token_expires_at", it) } ?: remove("refresh_token_expires_at")

            apply()
        }
    }

    fun getAccessToken(): String? = encryptedSharedPreferences.getString("access_token", null)
    fun getRefreshToken(): String? = encryptedSharedPreferences.getString("refresh_token", null)
    // Region teraz domyślnie "eu"
    fun getRegion(): String = encryptedSharedPreferences.getString("region", "eu") ?: "eu"

    // Nowe funkcje do zapisywania i pobierania OAuth state (używane jako userId)
    fun saveOAuthState(state: String) {
        encryptedSharedPreferences.edit().putString("oauth_state", state).apply()
    }

    fun getOAuthState(): String? = encryptedSharedPreferences.getString("oauth_state", null)

    // Funkcje do pobierania czasów wygaśnięcia
    fun getAccessTokenExpiresAt(): Long? = encryptedSharedPreferences.getLong("access_token_expires_at", -1L).takeIf { it != -1L }
    fun getRefreshTokenExpiresAt(): Long? = encryptedSharedPreferences.getLong("refresh_token_expires_at", -1L).takeIf { it != -1L }

    fun clearLoginData() {
        encryptedSharedPreferences.edit().clear().apply()
    }

    // Dane wybranego urządzenia do automatyzacji
    fun saveSelectedDevice(deviceId: String, deviceName: String) {
        with(encryptedSharedPreferences.edit()) {
            putString("selected_device_id", deviceId)
            putString("selected_device_name", deviceName)
            apply()
        }
    }

    fun getSelectedDeviceId(): String? = encryptedSharedPreferences.getString("selected_device_id", null)
    fun getSelectedDeviceName(): String? = encryptedSharedPreferences.getString("selected_device_name", null)

    // Dane wielokąta (zapisane jako string JSON)
    fun savePolygonCoordinates(coordinatesJson: String) {
        with(encryptedSharedPreferences.edit()) {
            putString("polygon_coordinates", coordinatesJson)
            apply()
        }
    }

    fun getPolygonCoordinates(): String? = encryptedSharedPreferences.getString("polygon_coordinates", null)
}