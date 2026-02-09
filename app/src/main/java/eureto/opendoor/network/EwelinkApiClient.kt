package eureto.opendoor.network

import android.content.Context
import com.google.gson.GsonBuilder
import eureto.opendoor.data.AppPreferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Sigleton object for Ewelink API Client
object EwelinkApiClient {

    private lateinit var appPreferences: AppPreferences
    private lateinit var authInterceptor: AuthInterceptor


    fun initialize(context: Context) {
        appPreferences = AppPreferences(context)
        authInterceptor = AuthInterceptor(
            appPreferences,
            appPreferences.getClientId(),
            appPreferences.getClientSecret(),
            appPreferences.getRedirectUri()
        )
    }

    private val gson = GsonBuilder().setLenient().create()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val webSocketOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)) // Logowanie dla debugowania
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun createAuthService(): EwelinkAuthService {
        val region = appPreferences.getRegion() ?: "eu"
        val baseUrl = "https://$region-apia.coolkit.cc/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(webSocketOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(EwelinkAuthService::class.java)
    }

    fun createApiService(): EwelinkApiService {
        val region = appPreferences.getRegion() ?: "eu"
        val baseUrl = "https://$region-apia.coolkit.cc/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(EwelinkApiService::class.java)
    }


    fun getAppPreferences(): AppPreferences {
        return appPreferences
    }
}