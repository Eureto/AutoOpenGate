package eureto.opendoor.network

import eureto.opendoor.network.model.AccessTokenRequestBody
import eureto.opendoor.network.model.LoginResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.*

interface EwelinkAuthService {

    // Endpoint do wymiany kodu autoryzacji na tokeny
    @POST("v2/user/oauth/token")
    suspend fun getAccessToken(
        @Header("X-CK-Nonce") nonce: String,
        @Header("Authorization") auth: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("X-CK-Appid") appid: String,
        @Body requestBody: AccessTokenRequestBody // Ciało żądania w formacie JSON
    ): LoginResponse

    // Endpoint do odświeżania tokenów
    @FormUrlEncoded
    @POST("v2/user/oauth/token")
    suspend fun refreshAccessToken(
        @Field("grant_type") grantType: String, // zawsze "refresh_token"
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String
    ): LoginResponse
}