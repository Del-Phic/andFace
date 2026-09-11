package dev.andface.galaxy.network
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
object RetrofitClient {
 fun normalizeUrl(raw: String, allowLocalHttp: Boolean): String {
  val url=raw.trim().toHttpUrl()
  require(url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null) { "주소에 계정·쿼리·앵커를 넣을 수 없습니다." }
  require(url.isHttps || (allowLocalHttp && url.host=="127.0.0.1")) { "HTTPS 서버 주소를 사용하세요. USB 시험은 http://127.0.0.1:8080/을 사용합니다." }
  return url.toString().trimEnd('/')+"/"
 }
 fun create(url: String): ApiService = Retrofit.Builder().baseUrl(url)
  .client(OkHttpClient.Builder().connectTimeout(3,TimeUnit.SECONDS).readTimeout(5,TimeUnit.SECONDS)
   .callTimeout(8,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build())
  .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
}
