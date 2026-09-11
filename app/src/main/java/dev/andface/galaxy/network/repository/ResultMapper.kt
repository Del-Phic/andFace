package dev.andface.galaxy.network.repository
import dev.andface.galaxy.auth.*
import dev.andface.galaxy.network.dto.ResultRequest
import java.time.Instant
import java.util.UUID

object ResultMapper {
 fun map(result: AuthResult, userCode: String, deviceId: String): ResultRequest? {
  if(result.failureReason==FailureReason.UNSTABLE_DECISION || result.failureReason==FailureReason.SESSION_RESET) return null
  if(result.decision==AuthDecision.SUCCESS && result.matchedUserId!=userCode) return null
  val metrics=listOf(result.fuzzyScore,result.mahalanobisScore,result.finalScore,result.coverage,result.margin)
  if(metrics.any { !it.isFinite() || it !in 0.0..1.0 }) return null // Reject upload; never clamp or change recognition.
  return ResultRequest(userCode,deviceId,result.decision.name,result.fuzzyScore,result.mahalanobisScore,
   result.finalScore,result.coverage,result.margin,result.livenessPassed,
   if(result.decision==AuthDecision.SUCCESS) null else result.failureReason.name,
   UUID.randomUUID().toString(),Instant.now().toString())
 }
}

/** Decision events, not per-frame accuracy measurements. One success per transition,
 * failure transitions at most once per 5 seconds; no idle/session-start records. */
class DecisionEventSampler {
 private var lastKey: String?=null
 private var lastFailureMs: Long?=null
 @Synchronized fun accept(incoming: AuthResult, displayed: AuthResult, user: String, now: Long): Boolean {
  if(incoming.decision!=displayed.decision || incoming.failureReason!=displayed.failureReason) return false
  if(incoming.failureReason in setOf(FailureReason.UNSTABLE_DECISION,FailureReason.SESSION_RESET)) return false
  if(incoming.observableCount==0 && lastKey==null) return false
  val key="$user/${incoming.decision}/${incoming.failureReason}/${incoming.matchedUserId}"
  if(key==lastKey) return false
  if(incoming.decision==AuthDecision.FAILED && lastFailureMs?.let { now-it<5000 }==true) {
   // A failure still ends a success episode even if its upload is rate limited.
   lastKey=key
   return false
  }
  lastKey=key
  if(incoming.decision==AuthDecision.FAILED) lastFailureMs=now
  return true
 }
 @Synchronized fun reset() {lastKey=null;lastFailureMs=null}
}
