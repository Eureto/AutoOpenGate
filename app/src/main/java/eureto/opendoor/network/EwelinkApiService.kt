package eureto.opendoor.network

import eureto.opendoor.network.model.ApiResponse
import eureto.opendoor.network.model.Device
import eureto.opendoor.network.model.DeviceControlRequest
import eureto.opendoor.network.model.DeviceListWrapper
import eureto.opendoor.network.model.ToggleResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

// Ten interfejs będzie używany do zapytań HTTP REST API
interface EwelinkApiService {

    // Przykład pobierania listy urządzeń
    // URI to /v2/device/thing
    @Headers("Content-Type: application/json") // DODANO: Nagłówek Content-Type dla żądania GET
    @GET("v2/device/thing")
    suspend fun getDevices(): ApiResponse<DeviceListWrapper> // ZMIENIONO: Typ zwracany na DeviceListWrappe

    // Przykład pobierania statusu pojedynczego urządzenia
    // URI to /v2/device/thing/status
    @GET("v2/device/thing/status")
    suspend fun getDeviceStatus(@Query("thingid") deviceId: String): ApiResponse<Device>

    // Przykład pobierania statusu pojedynczego urządzenia
    // URI to /v2/device/thing/status
    @POST("v2/device/thing/status") // Metoda POST zgodnie z dokumentacją
    @Headers("Content-Type: application/json") // Dodano nagłówek Content-Type dla POST
    suspend fun setDeviceStatus(@Body body: DeviceControlRequest): ApiResponse<ToggleResponse> // ZMIENIONO: Typ 'body' na DeviceControlRequest
}