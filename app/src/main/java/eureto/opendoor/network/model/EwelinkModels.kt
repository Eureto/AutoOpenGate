package eureto.opendoor.network.model

import com.google.gson.annotations.SerializedName

// Klasa danych dla zagnieżdżonego obiektu 'data' w odpowiedzi LoginResponse
data class LoginResponseData(
    @SerializedName("accessToken") val accessToken: String?,
    @SerializedName("refreshToken") val refreshToken: String?,
    @SerializedName("atExpiredTime") val atExpiredTime: Long?,
    @SerializedName("rtExpiredTime") val rtExpiredTime: Long?
    // Usunięto region, userId i apikey z LoginResponseData zgodnie z otrzymaną odpowiedzią JSON
)

// Odpowiedź na logowanie OAuth2
data class LoginResponse(
    val error: Int, // 0 dla sukcesu
    val msg: String?,
    @SerializedName("data") val data: LoginResponseData? // Zagnieżdżony obiekt danych
)

// Nowa klasa danych dla ciała żądania wymiany tokenu (JSON)
data class AccessTokenRequestBody(
    @SerializedName("code") val code: String,
    @SerializedName("redirectUrl") val redirectUrl: String,
    @SerializedName("grantType") val grantType: String
)

// Nowa klasa reprezentująca pojedynczy element "thingList"
data class ThingListItem(
    @SerializedName("itemType") val itemType: Int?,
    @SerializedName("itemData") val itemData: Device?, // Rzeczywiste dane urządzenia
    @SerializedName("index") val index: Int?
)

// Nowa klasa reprezentująca obiekt "data" dla listy urządzeń
data class DeviceListWrapper(
    @SerializedName("thingList") val thingList: List<ThingListItem>?,
    @SerializedName("total") val total: Int?
)

// Model urządzenia, zaktualizowany na podstawie struktury "itemData"
data class Device(
    @SerializedName("name") val name: String,
    @SerializedName("deviceid") val deviceid: String,
    @SerializedName("apikey") val deviceApiKey: String? = null, // Klucz API specyficzny dla urządzenia
    @SerializedName("extra") val extra: DeviceExtra? = null,
    @SerializedName("brandName") val brandName: String? = null,
    @SerializedName("brandLogo") val brandLogo: String? = null,
    @SerializedName("showBrand") val showBrand: Boolean? = null,
    @SerializedName("productModel") val productModel: String? = null,
    @SerializedName("tags") val tags: Map<String, Any>? = null, // Złożone, jako Map<String, Any>
    @SerializedName("devConfig") val devConfig: Map<String, Any>? = null, // Złożone, jako Map<String, Any>
    @SerializedName("settings") val settings: DeviceSettings? = null, // Nowa klasa DeviceSettings
    @SerializedName("devGroups") val devGroups: List<String>? = null, // Może być lista stringów
    @SerializedName("family") val family: Family? = null,
    @SerializedName("sharedBy") val sharedBy: SharedBy? = null, // Nowa klasa SharedBy
    @SerializedName("devicekey") val devicekey: String? = null,
    @SerializedName("online") val online: Boolean, // Przeniesiono tutaj z params
    @SerializedName("params") val params: DeviceParams,
    @SerializedName("denyFeatures") val denyFeatures: List<String>? = null,
    @SerializedName("isSupportGroup") val isSupportGroup: Boolean? = null,
    @SerializedName("isSupportedOnMP") val isSupportedOnMP: Boolean? = null,
    @SerializedName("isSupportChannelSplit") val isSupportChannelSplit: Boolean? = null,
    @SerializedName("wxModelId") val wxModelId: String? = null,
    @SerializedName("deviceFeature") val deviceFeature: Map<String, Any>? = null // Złożone, jako Map<String, Any>
)

data class DeviceParams(
    @SerializedName("timers") val timers: List<Timer>? = null, // Może być lista Any lub konkretna klasa
    @SerializedName("bssid") val bssid: String? = null,
    @SerializedName("ssid") val ssid: String? = null,
    @SerializedName("only_device") val onlyDevice: Map<String, Any>? = null,
    @SerializedName("bindInfos") val bindInfos: Map<String, Any>? = null,
    @SerializedName("pulseWidth") val pulseWidth: Int? = null,
    @SerializedName("pulse") val pulse: String? = null,
    @SerializedName("init") val init: Int? = null,
    @SerializedName("startup") val startup: String? = null,
    @SerializedName("staMac") val staMac: String? = null,
    @SerializedName("rssi") val rssi: Int? = null,
    @SerializedName("fwVersion") val fwVersion: String? = null,
    @SerializedName("switch") val switch: String? = null, // "on" lub "off" dla przełączników
    @SerializedName("sledOnline") val sledOnline: String? = null,
    @SerializedName("version") val version: Int? = null,
    @SerializedName("rstReason") val rstReason: Int? = null,
    @SerializedName("exccause") val exccause: Int? = null,
    @SerializedName("epc1") val epc1: Int? = null,
    @SerializedName("epc2") val epc2: Int? = null,
    @SerializedName("epc3") val epc3: Int? = null,
    @SerializedName("excvaddr") val excvaddr: Int? = null,
    @SerializedName("depc") val depc: Int? = null,
    @SerializedName("longOffline") val longOffline: Int? = null,
    @SerializedName("TZ") val TZ: String? = null,
    @SerializedName("status") val status: String? = null // Czasem status urządzenia
)

data class DeviceExtra(
    @SerializedName("uiid") val uiid: Int, // Unikalny identyfikator interfejsu (typ urządzenia)
    @SerializedName("familyid") val familyId: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("brandId") val brandId: String? = null,
    @SerializedName("apmac") val apmac: String? = null,
    @SerializedName("mac") val mac: String? = null,
    @SerializedName("ui") val ui: String? = null,
    @SerializedName("modelInfo") val modelInfo: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("manufacturer") val manufacturer: String? = null,
    @SerializedName("staMac") val staMac: String? = null,
    @SerializedName("family") val family: Family? = null // Rodzina może być też w extra
)

data class Family(
    @SerializedName("familyid") val familyid: String,
    @SerializedName("familyName") val familyName: String? = null, // Dodano familyName
    @SerializedName("index") val index: Int? = null, // Dodano index
    @SerializedName("rooms") val rooms: List<Room>? = null // Zmieniono na nullable
)

data class Room(
    @SerializedName("roomid") val roomid: String,
    @SerializedName("roomName") val roomName: String
)

data class DeviceSettings(
    @SerializedName("opsNotify") val opsNotify: Int? = null,
    @SerializedName("opsHistory") val opsHistory: Int? = null,
    @SerializedName("alarmNotify") val alarmNotify: Int? = null,
    @SerializedName("wxAlarmNotify") val wxAlarmNotify: Int? = null,
    @SerializedName("wxOpsNotify") val wxOpsNotify: Int? = null,
    @SerializedName("wxDoorbellNotify") val wxDoorbellNotify: Int? = null,
    @SerializedName("appDoorbellNotify") val appDoorbellNotify: Int? = null
)

data class SharedBy(
    @SerializedName("apikey") val apikey: String? = null, // Device-specific apikey for sharing
    @SerializedName("email") val email: String? = null,
    @SerializedName("nickname") val nickname: String? = null,
    @SerializedName("comment") val comment: String? = null,
    @SerializedName("permit") val permit: Int? = null,
    @SerializedName("shareTime") val shareTime: Long? = null,
    @SerializedName("authority") val authority: Authority? = null
)

data class Authority(
    @SerializedName("updateTimers") val updateTimers: Boolean? = null
)

// DODANO: Klasa dla pojedynczego timera
data class Timer(
    @SerializedName("mId") val mId: String?,
    @SerializedName("type") val type: String?, // np. "once" lub "duration"
    @SerializedName("at") val at: String?, // Data i czas, np. "2022-09-10T08:15:00.200Z"
    @SerializedName("coolkit_timer_type") val coolkitTimerType: String?,
    @SerializedName("enabled") val enabled: Int?, // 0 lub 1
    @SerializedName("do") val `do`: TimerDo? // Słowo kluczowe "do" wymaga użycia cudzysłowów
)

// DODANO: Klasa dla obiektu "do" w timerze
data class TimerDo(
    @SerializedName("outlet") val outlet: Int?, // Zazwyczaj 0 dla pojedynczego wyjścia
    @SerializedName("switch") val switch: String? // "on" lub "off"
)

// DODANO: Nowe klasy dla ciała żądania sterowania urządzeniem
data class DeviceControlParams(
    @SerializedName("switch") val switch: String
)

data class DeviceControlRequest(
    @SerializedName("type") val type: Int,
    @SerializedName("id") val id: String,
    @SerializedName("params") val params: DeviceControlParams
)

// Model dla ogólnej odpowiedzi API (jeśli nie jest loginem)
// Ewelink API często zwraca obiekty z `error` i `msg`
data class ApiResponse<T>(
    val error: Int, // 0 dla sukcesu
    val msg: String?,
    val data: T? // Może być null w przypadku błędu
)

// Odpowiedź na żądanie sterowania (może się różnić w zależności od endpointu)
data class ToggleResponse(
    val status: String? // np. "ok"
)

// Model wiadomości WebSocket (uproszczony)
data class WebSocketMessage(
    val action: String,
    val at: String? = null,
    val apikey: String? = null,
    val appid: String? = null,
    val seq: String? = null,
    val userAgent: String? = null,
    val ts: String? = null,
    val version: Int? = null,
    val deviceid: String? = null,
    val params: Map<String, Any>? = null,
    val sequence: String? = null // Dla ping/pong
)
