package eureto.opendoor.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys  //TODO: Change depracated MasterKeys to new implementation
import com.google.android.gms.maps.model.LatLng
import eureto.opendoor.BuildConfig
import eureto.opendoor.network.model.LoginResponse


/**
 * Handles secure storage and retrieval of application preferences using encrypted SharedPreferences.
 *
 * This class manages sensitive data such as OAuth tokens, device selections, and polygon coordinates,
 * ensuring all information is stored securely on the device.
 *
 * @param context The application context used to access SharedPreferences.
 */
class AppPreferences(private val context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val encryptedSharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "secure_ewelink_prefs",
            masterKeyAlias,         // Key Alias (String)
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val CLIENT_ID = BuildConfig.CLIENT_ID
    private val CLIENT_SECRET = BuildConfig.CLIENT_SECRET
    // Replace with your actual redirect URI
    private val REDIRECT_URI = "euretoapp://oauth2callback" // Must be the same as in AndroidManifest.xml

    fun getClientId(): String = CLIENT_ID
    fun getClientSecret(): String = CLIENT_SECRET
    fun getRedirectUri(): String = REDIRECT_URI

    // Saves access token and refresh token along with their expiration times to safely encrypted SharedPreferences
    fun saveLoginData(loginResponse: LoginResponse) {
        with(encryptedSharedPreferences.edit()) {
            putString("access_token", loginResponse.data?.accessToken)
            putString("refresh_token", loginResponse.data?.refreshToken)

            loginResponse.data?.atExpiredTime?.let { putLong("access_token_expires_at", it) } ?: remove("access_token_expires_at")
            loginResponse.data?.rtExpiredTime?.let { putLong("refresh_token_expires_at", it) } ?: remove("refresh_token_expires_at")

            apply()
        }
    }
    // Returns access token
    fun getAccessToken(): String? = encryptedSharedPreferences.getString("access_token", null)
    //Returns refresh token
    fun getRefreshToken(): String? = encryptedSharedPreferences.getString("refresh_token", null)
    // Returns region, default is "eu"
    fun getRegion(): String = encryptedSharedPreferences.getString("region", "eu") ?: "eu"

    // Functions to save and get OAuth state parameter
    fun saveOAuthState(state: String) {
        encryptedSharedPreferences.edit().putString("oauth_state", state).apply()
    }
    // Returns saved OAuth state parameter
    fun getOAuthState(): String? = encryptedSharedPreferences.getString("oauth_state", null)

    // Functions to get token expiration times
    fun getAccessTokenExpiresAt(): Long? = encryptedSharedPreferences.getLong("access_token_expires_at", -1L).takeIf { it != -1L }
    fun getRefreshTokenExpiresAt(): Long? = encryptedSharedPreferences.getLong("refresh_token_expires_at", -1L).takeIf { it != -1L }

    // Clears all saved login data
    fun clearLoginData() {
        encryptedSharedPreferences.edit().clear().apply()
    }

    // Saves selected device ID and name
    fun saveSelectedDevice(deviceId: String, deviceName: String) {
        with(encryptedSharedPreferences.edit()) {
            putString("selected_device_id", deviceId)
            putString("selected_device_name", deviceName)
            apply()
        }
    }
    // Returns selected device ID and name
    fun getSelectedDeviceId(): String? = encryptedSharedPreferences.getString("selected_device_id", null)
    fun getSelectedDeviceName(): String? = encryptedSharedPreferences.getString("selected_device_name", null)

    // Saves polygon coordinates as JSON string
    fun savePolygonCoordinates(coordinatesJson: String) {
        with(encryptedSharedPreferences.edit()) {
            putString("polygon_coordinates", coordinatesJson)
            apply()
        }
    }
    // Saves polygon center as "latitude,longitude" string

    fun savePolygonCenter(latLng: LatLng) {
        with(encryptedSharedPreferences.edit()) {
            putString("polygon_center", "${latLng.latitude},${latLng.longitude}")
            apply()
        }
    }
    // Returns saved polygon center
    fun getPolygonCenter(): String? = encryptedSharedPreferences.getString("polygon_center", null)
    // Returns saved polygon coordinates JSON string
    fun getPolygonCoordinates(): String? = encryptedSharedPreferences.getString("polygon_coordinates", null)

    fun getIsLocationCheckWorkerRunning(): Boolean = encryptedSharedPreferences.getBoolean("is_location_check_worker_running", false)

    fun setIsLocationCheckWorkerRunning(value: Boolean) {
        with(encryptedSharedPreferences.edit()) {
            putBoolean("is_location_check_worker_running", value)
            apply()
        }
    }
}