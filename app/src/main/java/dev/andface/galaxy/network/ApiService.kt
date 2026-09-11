package dev.andface.galaxy.network
import dev.andface.galaxy.network.dto.*
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*
interface ApiService {
 @POST("api/users/register") fun register(@Body request: Registration): Call<ResponseBody>
 @POST("api/users/login") fun login(@Body request: Credentials): Call<LoginResponse>
 @POST("api/devices") fun device(@Header("Authorization") token: String, @Body request: DeviceRequest): Call<ResponseBody>
 @POST("api/authentication/results") fun upload(@Header("Authorization") token: String, @Body request: ResultRequest): Call<ResponseBody>
 @GET("api/authentication/statistics") fun statistics(@Header("Authorization") token: String): Call<ResponseBody>
}
