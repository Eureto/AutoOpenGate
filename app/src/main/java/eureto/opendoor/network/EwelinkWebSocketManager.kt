package eureto.opendoor.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.network.model.DispatchInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Zarządza połączeniem WebSocket z eWeLink zgodnie z dokumentacją API Center v2.
 *
 * Przepływ:
 * 1. Aplikacja wywołuje DispatchService (HTTP) aby uzyskać domain/port serwera WebSocket.
 * 2. Nawiązuje się połączenie WebSocket pod wss://{domain}:{port}/api/ws.
 * 3. Wysyła się wiadomość userOnline (handshake) z tokenem accessToken i userApikey.
 * 4. Serwer odpowiada z config.hbInterval (interwał keepalive).
 * 5. Klient wysyła ping co hbInterval+7 sekund i oczekuje pong.
 * 6. Serwer pasywnie wysyła aktualizacje statusu urządzeń (action: update) oraz sysmsg (online/offline).
 */
object EwelinkWebSocketManager {

    private const val TAG = "EwelinkWebSocketManager"

    private var webSocket: WebSocket? = null
    private var isConnected = false

    @Volatile
    private var isConnecting = false

    private var heartbeatJob: ScheduledFuture<*>? = null
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    private val gson = Gson()

    /**
     * Nawiązuje połączenie WebSocket z eWeLink.
     *
     * Jeśli połączenie już istnieje lub trwa łączenie, wywołanie jest ignorowane.
     *
     * @param context kontekst aplikacji (potrzebny do odczytu tokenów z AppPreferences)
     * @param dispatchInfo dane serwera WebSocket z DispatchService (domain, port)
     * @param onDeviceStatusUpdate callback wywoływany, gdy serwer wyśle nowy status urządzenia (action: update)
     * @param onRawMessage callback z surowym tekstem wiadomości (do debugowania)
     * @param onOpen callback wywoływany po pomyślnym otwarciu połączenia (przed wysłaniem userOnline)
     * @param onFailure callback wywoływany przy błędzie połączenia
     */
    fun connect(
        context: Context,
        dispatchInfo: DispatchInfo,
        onDeviceStatusUpdate: (deviceId: String, isOn: Boolean) -> Unit,
        onRawMessage: (String) -> Unit = {},
        onOpen: () -> Unit = {},
        onFailure: (Throwable) -> Unit = {}
    ) {
        if (isConnected || isConnecting) {
            Log.d(TAG, "connect() pominięty — połączenie już istnieje lub trwa łączenie")
            return
        }

        val prefs = AppPreferences(context)
        val accessToken = prefs.getAccessToken()
        val userApikey = prefs.getUserApikey()

        if (accessToken.isNullOrEmpty() || userApikey.isNullOrEmpty()) {
            Log.e(TAG, "Brak accessToken lub userApikey — nie można otworzyć WebSocket")
            return
        }

        val clientId = prefs.getClientId()

        val wsUrl = "wss://${dispatchInfo.domain}:${dispatchInfo.port}/api/ws"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // Dla WebSocket nie ustawiamy readTimeout — połączenie ma żyć bezczynnie,
            // a o żywotność dba heartbeat (ping/pong).
            .readTimeout(0, TimeUnit.SECONDS)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                super.onOpen(webSocket, response)
                isConnected = true
                isConnecting = false
                this@EwelinkWebSocketManager.webSocket = webSocket
                Log.d(TAG, "WebSocket opened: $wsUrl")
                onOpen()

                // --- KROK 2: Wysłanie handshake userOnline ---
                val nowMs = System.currentTimeMillis()
                val nonce = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                val ts = (nowMs / 1000)
                val sequence = nowMs.toString()

                val handshake = mapOf(
                    "action" to "userOnline",
                    "version" to 8,
                    "ts" to ts,
                    "at" to accessToken,
                    "userAgent" to "app",
                    "apikey" to userApikey,
                    "appid" to clientId,
                    "nonce" to nonce,
                    "sequence" to sequence
                )
                webSocket.send(gson.toJson(handshake))
                Log.d(TAG, "Sent userOnline handshake")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Log.d(TAG, "WebSocket message: $text")
                onRawMessage(text)

                try {
                    val msg: Map<*, *> = gson.fromJson(text, Map::class.java)
                    // Serwer odpowiada na handshake z polem "actionName" zamiast "action"
                    val action = (msg["action"] as? String) ?: (msg["actionName"] as? String)

                    when (action) {
                        "userOnline" -> {
                            // Odpowiedź na handshake — zawiera config.hb / config.hbInterval
                            // Uwaga: Gson parsuje liczby z JSON do Double, dlatego rzutujemy przez Number.
                            val error = (msg["error"] as? Number)?.toInt()
                            val config = msg["config"] as? Map<*, *>
                            val hb = (config?.get("hb") as? Number)?.toInt()
                            val hbInterval = (config?.get("hbInterval") as? Number)?.toInt()
                            Log.d(TAG, "Handshake response: error=$error, hb=$hb, hbInterval=$hbInterval")

                            if (error != null && error != 0) {
                                Log.e(
                                    TAG,
                                    "Autoryzacja WebSocket nieudana: error=$error, reason=${msg["reason"]}"
                                )
                                // Zamknij połączenie i wyzeruj stan, żeby kolejne wejście do
                                // aplikacji mogło ponowić próbę z poprawnym apikey.
                                isConnected = false
                                isConnecting = false
                                stopHeartbeat()
                                webSocket.close(1000, "auth failed")
                                onFailure(IOException("WebSocket authentication failed: $error ${msg["reason"]}"))
                                return
                            }

                            // Zawsze uruchamiamy heartbeat po udanym handshake.
                            // Jeśli serwer nie poda config lub da hb = 0, i tak wysyłamy ping
                            // co 90 s — inaczej po ~5 minutach ciszy serwer zamyka połączenie
                            // i w OkHttp leci EOFException.
                            val intervalSeconds = if (hb == 0) 90 else (hbInterval ?: 90) + 7
                            startHeartbeat(intervalSeconds)
                            Log.d(TAG, "Heartbeat started every ${intervalSeconds}s")
                        }

                        "ping" -> {
                            // Serwer sprawdza czy klient żyje — odpowiadamy pong
                            val sequence = msg["sequence"] as? String
                            if (sequence != null) {
                                val pong = mapOf("action" to "pong", "sequence" to sequence)
                                webSocket.send(gson.toJson(pong))
                                Log.d(TAG, "Sent pong: $sequence")
                            }
                        }

                        "update" -> {
                            // Pasywne powiadomienie o zmianie statusu urządzenia.
                            // params może być obiektem albo tablicą obiektów (wg dokumentacji),
                            // a wiadomości bez pola "switch" ignorujemy, żeby nie nadpisywać
                            // stanu w UI fałszywym "off".
                            val deviceId = msg["deviceid"] as? String ?: return
                            val rawParams = msg["params"] ?: return
                            val switchVal = when (rawParams) {
                                is Map<*, *> -> rawParams["switch"] as? String
                                is List<*> -> rawParams.filterIsInstance<Map<*, *>>()
                                    .firstNotNullOfOrNull { it["switch"] as? String }
                                else -> null
                            } ?: return
                            onDeviceStatusUpdate(deviceId, switchVal == "on")
                        }

                        "sysmsg" -> {
                            // Urządzenie online/offline
                            val deviceId = msg["deviceid"] as? String
                            val params = msg["params"] as? Map<*, *>
                            val online = params?.get("online") as? Boolean
                            Log.d(TAG, "Sysmsg: device=$deviceId online=$online")
                        }

                        else -> Log.d(TAG, "Unhandled action: $action")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Błąd parsowania wiadomości WebSocket", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                super.onFailure(webSocket, t, response)
                isConnected = false
                isConnecting = false
                stopHeartbeat()
                Log.e(TAG, "WebSocket failure (HTTP ${response?.code}): ${t.message}", t)
                onFailure(t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                isConnected = false
                isConnecting = false
                stopHeartbeat()
                Log.d(TAG, "WebSocket closing: $code $reason")
            }
        }

        isConnecting = true
        webSocket = client.newWebSocket(request, listener)
    }

    private fun startHeartbeat(intervalSeconds: Int) {
        stopHeartbeat()
        heartbeatJob = scheduler.scheduleAtFixedRate({
            val socket = webSocket
            if (isConnected && socket != null) {
                val sequence = System.currentTimeMillis().toString()
                val ping = mapOf("action" to "ping", "sequence" to sequence)
                socket.send(gson.toJson(ping))
                Log.d(TAG, "Sent ping: $sequence")
            }
        }, intervalSeconds.toLong(), intervalSeconds.toLong(), TimeUnit.SECONDS)
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel(false)
        heartbeatJob = null
    }

    /// Zamyka połączenie WebSocket (np. przy wylogowaniu lub zniszczeniu Activity).
    fun disconnect() {
        stopHeartbeat()
        webSocket?.close(1000, null)
        webSocket = null
        isConnected = false
        isConnecting = false
    }

    /// Wysyła surowy tekst na WebSocket (np. komendę sterowania urządzeniem).
    fun send(text: String) {
        if (isConnected) {
            webSocket?.send(text)
        }
    }

    /// Zwraca true, jeśli połączenie jest aktualnie otwarte.
    fun isConnected(): Boolean = isConnected
}
