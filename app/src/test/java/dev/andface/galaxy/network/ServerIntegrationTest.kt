package dev.andface.galaxy.network

import dev.andface.galaxy.auth.*
import dev.andface.galaxy.network.repository.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServerIntegrationTest {
 private fun success() = AuthResult.failed(FailureReason.NONE).copy(
  decision=AuthDecision.SUCCESS,matchedUserId="USER_1",fuzzyScore=0.87321,
  mahalanobisScore=0.81765,finalScore=0.8576532,coverage=0.5823,margin=0.2381,
  livenessPassed=true,observableCount=101
 )
 @Test fun actualScoresAreSerializedWithoutBiometricData() {
  MockWebServer().use { server ->
   server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
   val r=success()
   val dto=ResultMapper.map(r,"USER_1","DEVICE")!!
   RetrofitClient.create(server.url("/").toString()).upload("Bearer test-token",dto).execute().body()?.close()
   val received=server.takeRequest()
   assertEquals("/api/authentication/results",received.path)
   assertEquals("Bearer test-token",received.getHeader("Authorization"))
   val json=JSONObject(received.body.readUtf8())
   assertEquals(r.fuzzyScore,json.getDouble("fuzzyScore"),0.0)
   assertEquals(r.mahalanobisScore,json.getDouble("mahalanobisScore"),0.0)
   assertEquals(r.finalScore,json.getDouble("finalScore"),0.0)
   assertEquals(r.coverage,json.getDouble("coverage"),0.0)
   assertEquals(r.margin,json.getDouble("margin"),0.0)
   assertEquals(r.livenessPassed,json.getBoolean("liveness"))
   assertEquals(setOf("userCode","deviceId","result","fuzzyScore","mahalanobisScore","finalScore","coverage","margin","liveness","eventId","occurredAt"),json.keys().asSequence().toSet())
  }
 }
 @Test fun failedUploadCannotMutateAuthResultAndRedirectDoesNotLeakToken() {
  MockWebServer().use { server -> MockWebServer().use { other ->
   server.enqueue(MockResponse().setResponseCode(302).addHeader("Location",other.url("/stolen")))
   val r=success();val original=r.copy()
   val response=RetrofitClient.create(server.url("/").toString()).upload("Bearer test-token",ResultMapper.map(r,"USER_1","D")!!).execute()
   assertEquals(302,response.code());response.errorBody()?.close()
   assertEquals(0,other.requestCount);assertEquals(original,r)
  }}
 }
 @Test fun serverErrorRemainsAnUploadError() {
  MockWebServer().use { server ->
   server.enqueue(MockResponse().setResponseCode(503))
   val r=success();val response=RetrofitClient.create(server.url("/").toString()).upload("Bearer test-token",ResultMapper.map(r,"USER_1","D")!!).execute()
   assertFalse(response.isSuccessful);response.errorBody()?.close();assertEquals(AuthDecision.SUCCESS,r.decision)
  }
 }
 @Test fun mapperRejectsInvalidMetricsAndOtherIdentityWithoutChangingThem() {
  assertNull(ResultMapper.map(success().copy(fuzzyScore=Double.NaN),"USER_1","D"))
  assertNull(ResultMapper.map(success(),"USER_2","D"))
  val r=success().copy(decision=AuthDecision.FAILED,failureReason=FailureReason.LOW_SCORE)
  assertEquals("LOW_SCORE",ResultMapper.map(r,"USER_1","D")!!.failureReason)
 }
 @Test fun samplerSuppressesFramesAndHeldSuccessButAllowsNextEpisode() {
  val sampler=DecisionEventSampler();val ok=success()
  val failed=ok.copy(decision=AuthDecision.FAILED,failureReason=FailureReason.LOW_SCORE)
  assertTrue(sampler.accept(ok,ok,"USER_1",0))
  repeat(100){assertFalse(sampler.accept(ok,ok,"USER_1",it.toLong()+1))}
  assertFalse(sampler.accept(failed,ok,"USER_1",150))
  assertTrue(sampler.accept(failed,failed,"USER_1",200))
  assertTrue(sampler.accept(ok,ok,"USER_1",300))
  assertFalse(sampler.accept(failed,failed,"USER_1",400))
  assertTrue(sampler.accept(ok,ok,"USER_1",500))
 }
 @Test fun pendingAndIdleAreNotAttempts() {
  val sampler=DecisionEventSampler()
  val idle=AuthResult.failed(FailureReason.NO_FACE)
  assertFalse(sampler.accept(idle,idle,"USER_1",0))
  val pending=success().copy(decision=AuthDecision.FAILED,failureReason=FailureReason.UNSTABLE_DECISION)
  assertFalse(sampler.accept(pending,pending,"USER_1",1))
 }
 @Test fun onlyDebugLoopbackMayUseHttp() {
  assertEquals("http://127.0.0.1:8080/",RetrofitClient.normalizeUrl("http://127.0.0.1:8080",true))
  for(url in listOf("http://example.com/","https://user:password@example.com/","https://example.com/?token=x")) {
   assertThrows(IllegalArgumentException::class.java){RetrofitClient.normalizeUrl(url,true)}
  }
  assertThrows(IllegalArgumentException::class.java){RetrofitClient.normalizeUrl("http://127.0.0.1:8080",false)}
 }
}
