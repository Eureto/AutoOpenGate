package eureto.opendoor.network

import android.content.Context
import com.google.gson.GsonBuilder
import eureto.opendoor.data.AppPreferences // Zmień nazwę pakietu
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Sigleton object for Ewelink API Client
object EwelinkApiClient {

    private lateinit var appPreferences: AppPreferences
    private lateinit var authInterceptor: AuthInterceptor

    // Metoda do inicjalizacji z Context
    fun initialize(context: Context) {
        appPreferences = AppPreferences(context)
        authInterceptor = AuthInterceptor(
            appPreferences,
            appPreferences.getClientId(),
            appPreferences.getClientSecret(),
            appPreferences.getRedirectUri()
        )
    }

    private val gson = GsonBuilder().setLenient().create() // Lenient dla elastyczności JSONa

    // Klient OkHttp dla ogólnych zapytań API (z interceptorem autoryzacji)
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)) // Logowanie dla debugowania
            .addInterceptor(authInterceptor) // Dodajemy interceptor autoryzacji
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Klient OkHttp dla WebSocket (bez interceptora Auth, ponieważ auth jest w wiadomości WS)
    private val webSocketOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)) // Logowanie dla debugowania
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Retrofit dla endpointów autoryzacji (np. [https://oauth.ewelink.cc/](https://oauth.ewelink.cc/))
    // Nie używa AuthInterceptor, bo jeszcze nie mamy tokenów lub je odświeżamy
    fun createAuthService(): EwelinkAuthService {
        val region = appPreferences.getRegion() ?: "eu" // Pobierz region, domyślnie "eu"
        val baseUrl = "https://$region-apia.coolkit.cc/" // Dynamiczny URL bazowy dla API i OAuth

        return Retrofit.Builder()
            .baseUrl(baseUrl) // Ustaw dynamiczny URL bazowy dla OAuth
            .client(webSocketOkHttpClient) // Użyj klienta bez interceptora autoryzacji dla auth endpointów
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(EwelinkAuthService::class.java)
    }

    // Retrofit dla endpointów API (np. [https://eu-api.coolkit.cn/](https://eu-api.coolkit.cn/))
    // Używa AuthInterceptor do dodawania tokenów i podpisu
    // UWAGA: Bazowy URL dla API jest zależny od regionu.
    // Tutaj musimy zastosować dynamiczny URL. Interceptor może to obsługiwać,
    // albo możemy zmieniać bazowy URL dynamicznie przed stworzeniem instancji,
    // ale najprościej jest obsłużyć to wewnątrz funkcji getDevices() w EwelinkApiService
    // poprzez użycie @Url w Retrofit.
    fun createApiService(): EwelinkApiService {
        val region = appPreferences.getRegion() ?: "eu" // Pobierz region
        val baseUrl = "https://$region-apia.coolkit.cc/" // Dynamiczny URL bazowy

        return Retrofit.Builder()
            .baseUrl(baseUrl) // Ustaw dynamiczny URL bazowy
            .client(okHttpClient) // Użyj klienta z interceptorem autoryzacji
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(EwelinkApiService::class.java)
    }

    // Instancja WebSocketClient
    fun createWebSocketClient(): EwelinkWebSocketClient {
        return EwelinkWebSocketClient(webSocketOkHttpClient, appPreferences, gson)
    }

    // Metoda do pobierania preferencji (przydatna w ViewModelach)
    fun getAppPreferences(): AppPreferences {
        return appPreferences
    }
}