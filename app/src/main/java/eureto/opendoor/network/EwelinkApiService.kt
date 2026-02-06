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

// Interface for Ewelink API service using Retrofit
interface EwelinkApiService {

    @Headers("Content-Type: application/json")
    @GET("v2/device/thing")
    suspend fun getDevices(): ApiResponse<DeviceListWrapper>

    @GET("v2/device/thing/status")
    suspend fun getDeviceStatus(@Query("thingid") deviceId: String): ApiResponse<Device>

    @POST("v2/device/thing/status")
    @Headers("Content-Type: application/json")
    suspend fun setDeviceStatus(@Body body: DeviceControlRequest): ApiResponse<ToggleResponse>
}