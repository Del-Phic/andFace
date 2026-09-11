package dev.andface.galaxy.network.repository
import android.content.Context
import android.os.Build
import android.os.SystemClock
import dev.andface.galaxy.BuildConfig
import dev.andface.galaxy.auth.*
import dev.andface.galaxy.network.*
import dev.andface.galaxy.network.dto.*
import java.util.concurrent.*

/** Only this worker touches storage/network/queue. No callbacks into the auth engine. */
class ServerRepository private constructor(context: Context) {
 private val store=SessionStore(context.applicationContext)
 private val worker=ScheduledThreadPoolExecutor(1).apply {removeOnCancelPolicy=true}
 private val sampler=DecisionEventSampler()
 private val queue=ArrayDeque<ResultRequest>()
 @Volatile private var session: ServerSession?=null
 @Volatile var status="서버 연결 안 함"; private set
 @Volatile var accountCode: String?=null; private set
 private var api: ApiService?=null
 private var nextRetryAt=0L
 private var retryMs=3000L
 init {
  worker.execute {
   runCatching {
    store.load()?.takeIf {it.expiresAt>System.currentTimeMillis()}?.let {
     api=RetrofitClient.create(RetrofitClient.normalizeUrl(it.url,BuildConfig.DEBUG));session=it;accountCode=it.userCode;status="${it.userCode} 연결됨"
    }
   }.onFailure {status="서버 로그인 정보 확인 필요"}
  }
  worker.scheduleWithFixedDelay({runCatching {flush()}},1,1,TimeUnit.SECONDS)
 }
 fun onRendered(incoming: AuthResult, displayed: AuthResult, selectedUser: String) {
  // MainActivity invokes this only after rendering. No exceptions escape this boundary.
  runCatching {
   val s=session?:return
   val code=if(incoming.decision==AuthDecision.SUCCESS) incoming.matchedUserId else selectedUser
   if(code!=s.userCode) return
   if(!sampler.accept(incoming,displayed,s.userCode,SystemClock.elapsedRealtime())) return
   val request=ResultMapper.map(incoming,s.userCode,s.deviceId)?:return
   // The sampler limits tasks; the separate queue also has a hard bound.
   if(worker.queue.size>=32) return
   worker.execute {
    if(session!=s)return@execute
    if(queue.size>=100)queue.removeFirst()
    queue.addLast(request)
   }
  }
 }
 fun connect(rawUrl: String, userCode: String, username: String, password: String, register: Boolean, done: (String)->Unit) {
  worker.execute {
   val message=runCatching {
    val url=RetrofitClient.normalizeUrl(rawUrl,BuildConfig.DEBUG)
    require(userCode in setOf("USER_1","USER_2","USER_3")) {"USER_1, USER_2, USER_3 중 하나를 입력하세요."}
    val service=RetrofitClient.create(url)
    if(register) {
     val response=service.register(Registration(userCode,username,password)).execute()
     response.body()?.close();response.errorBody()?.close()
     check(response.isSuccessful) {"회원가입 실패 (HTTP ${response.code()})"}
    }
    val response=service.login(Credentials(username,password)).execute()
    response.errorBody()?.close()
    check(response.isSuccessful) {"로그인 실패 (HTTP ${response.code()})"}
    val login=checkNotNull(response.body())
    check(login.user.userCode==userCode) {"계정의 userCode가 선택한 사용자와 다릅니다."}
    val id=store.deviceId()
    val device=service.device("Bearer ${login.accessToken}",DeviceRequest(id,"${Build.MANUFACTURER} ${Build.MODEL}")).execute()
    device.body()?.close();device.errorBody()?.close()
    check(device.isSuccessful) {"기기 등록 실패 (HTTP ${device.code()})"}
    val s=ServerSession(url,login.accessToken,userCode,id,System.currentTimeMillis()+login.expiresIn*1000)
    store.save(s);queue.clear();sampler.reset();api=service;session=s;accountCode=s.userCode;nextRetryAt=0;retryMs=3000
    "${s.userCode} 서버 연결 완료"
   }.getOrElse {if(it is IllegalArgumentException || it is IllegalStateException) it.message?:"설정 확인 필요" else "서버 연결 실패: 주소와 네트워크를 확인하세요."}
   status=message;done(message)
  }
 }
 fun disconnect(done: (String)->Unit) {
  worker.execute {session=null;accountCode=null;api=null;queue.clear();sampler.reset();runCatching{store.clear()};status="서버 연결 해제됨";done(status)}
 }
 private fun flush() {
  val s=session?:return
  if(System.currentTimeMillis()>=s.expiresAt) {session=null;accountCode=null;queue.clear();status="서버 로그인 만료 · 다시 로그인하세요";runCatching{store.clear()};return}
  if(queue.isEmpty() || SystemClock.elapsedRealtime()<nextRetryAt)return
  val item=queue.first()
  val response=runCatching {api!!.upload("Bearer ${s.token}",item).execute()}.getOrNull()
  response?.body()?.close();response?.errorBody()?.close()
  when {
   response?.isSuccessful==true -> {queue.removeFirst();retryMs=3000;nextRetryAt=0;status="${s.userCode} 기록 전송 완료 · 대기 ${queue.size}건"}
   response?.code()==401 -> {session=null;accountCode=null;queue.clear();runCatching{store.clear()};status="서버 로그인 만료 · 다시 로그인하세요"}
   response!=null && response.code() in 400..499 && response.code()!=429 -> {queue.removeFirst();status="기록 전송 거절 (HTTP ${response.code()}) · 서버 설정 확인"}
   else -> {status="서버 응답 없음 · ${queue.size}건 임시 대기";nextRetryAt=SystemClock.elapsedRealtime()+retryMs;retryMs=(retryMs*2).coerceAtMost(60000)}
  }
 }
 companion object {
  @Volatile private var instance: ServerRepository?=null
  fun get(context: Context): ServerRepository = instance?:synchronized(this) {instance?:ServerRepository(context).also {instance=it}}
 }
}
