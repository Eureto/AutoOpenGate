package eureto.opendoor.network

import com.google.gson.Gson
import eureto.opendoor.data.AppPreferences
import eureto.opendoor.network.model.Device
import eureto.opendoor.network.model.DeviceParams
import eureto.opendoor.network.model.WebSocketMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString


class EwelinkWebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val appPreferences: AppPreferences,
    private val gson: Gson
) {
    internal var webSocket: WebSocket? = null

    // TODO: Dodaj notyfikację o zmianie statusu urządzenia jeśli jest to wysłane przeze mnie lub jeśli ktoś inny otworzy bramę
    private val _deviceStatusUpdatesChannel = Channel<Device>(Channel.BUFFERED)
    val deviceStatusUpdatesFlow = _deviceStatusUpdatesChannel.receiveAsFlow()

    // Adres WebSocket jest zależny od regionu, który otrzymujesz po zalogowaniu
    private fun getWebSocketUrl(region: String): String {
        return when (region) {
            "us" -> "wss://us-apia.coolkit.cc:8080/api/ws"
            "eu" -> "wss://eu-apia.coolkit.cc:8080/api/ws"
            "as" -> "wss://as-apia.coolkit.cc:8080/api/ws"
            "cn" -> "wss://cn-apia.coolkit.cc:8080/api/ws"
            else -> "wss://us-apia.coolkit.cc:8080/api/ws" // Domyślnie
        }
    }

    fun connect() {
        val region = appPreferences.getRegion() // getRegion() teraz zwraca "eu" jeśli null
        val accessToken = appPreferences.getAccessToken()
        val userId = appPreferences.getOAuthState() // UserId to teraz stan OAuth
        val apikeyForWs = appPreferences.getClientSecret() // Używamy clientSecret jako apikey dla WebSocket

        if (accessToken.isNullOrEmpty() || userId.isNullOrEmpty() || region.isNullOrEmpty()) { // Usunięto apikey z warunku
            println("EwelinkWebSocketClient: Brak danych logowania (token, userId, region), nie można połączyć z WebSocket.")
            return
        }

        val wsUrl = getWebSocketUrl(region)
        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("EwelinkWebSocketClient: WebSocket Opened: ${response.message}")
                // Po otwarciu połączenia, wyślij wiadomość autoryzacyjną
                val authMessage = WebSocketMessage(
                    action = "userOnline",
                    at = accessToken,
                    apikey = apikeyForWs,
                    appid = appPreferences.getClientId(),
                    seq = (System.currentTimeMillis() / 1000).toString(), // seq to Unix timestamp w sekundach
                    userAgent = "app",
                    ts = (System.currentTimeMillis() / 1000).toString(),
                    version = 8
                )
                webSocket.send(gson.toJson(authMessage))
                println("EwelinkWebSocketClient: Wysłano wiadomość autoryzacyjną: ${gson.toJson(authMessage)}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("EwelinkWebSocketClient: Otrzymano wiadomość: $text")
                try {
                    val message = gson.fromJson(text, WebSocketMessage::class.java)
                    when (message.action) {
                        "sysMsg" -> {
                            // Obsłuż wiadomości systemowe, np. ping/pong
                            if (message.sequence == "pong") {
                                println("EwelinkWebSocketClient: Otrzymano pong.")
                            }
                        }
                        "update" -> {
                            // Obsłuż aktualizacje statusu urządzenia
                            // Pełna aktualizacja statusu urządzenia
                            val deviceId = message.deviceid ?: return
                            val paramsMap = message.params ?: return
                            val switchState = paramsMap["switch"] as? String // Zakładamy, że to przełącznik
                            if (switchState != null) {
                                val updatedDevice = Device(
                                    deviceid = deviceId,
                                    name = "Nieznane urządzenie", // Nazwa będzie aktualizowana z listy urządzeń
                                    online = true, // Zakładamy online po aktualizacji
                                    params = DeviceParams(switch = switchState)
                                )
                                _deviceStatusUpdatesChannel.trySend(updatedDevice)
                                println("EwelinkWebSocketClient: Zaktualizowano status urządzenia: $deviceId do $switchState")
                            }
                        }
                        // ... inne typy wiadomości, np. "data" z pełnymi danymi urządzenia
                    }
                } catch (e: Exception) {
                    println("EwelinkWebSocketClient: Błąd parsowania wiadomości WebSocket: ${e.message}")
                    e.printStackTrace()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                println("EwelinkWebSocketClient: Otrzymano bajty: ${bytes.hex()}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                println("EwelinkWebSocketClient: Zamykanie: $code / $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("EwelinkWebSocketClient: Błąd WebSocket: ${t.message}")
                t.printStackTrace()
                // Tutaj można zaimplementować logikę ponownego łączenia
                // np. opóźnić i spróbować ponownie po pewnym czasie
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _deviceStatusUpdatesChannel.close()
        println("EwelinkWebSocketClient: Rozłączono WebSocket.")
    }



    // Wysyłanie ping co jakiś czas, aby utrzymać połączenie
    fun sendPing() {
        if (webSocket == null) return
        val pingMessage = mapOf(
            "action" to "ping",
            "seq" to (System.currentTimeMillis() / 1000).toString()
        )
        webSocket?.send(gson.toJson(pingMessage))
        println("EwelinkWebSocketClient: Wysłano ping.")
    }
}