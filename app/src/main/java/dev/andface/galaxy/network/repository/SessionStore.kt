package dev.andface.galaxy.network.repository
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ServerSession(val url: String,val token: String,val userCode: String,val deviceId: String,val expiresAt: Long)
internal class SessionStore(context: Context) {
 private val prefs=context.getSharedPreferences("andface_server_session",Context.MODE_PRIVATE)
 private fun key(): SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
  (store.getKey(ALIAS,null) as? SecretKey)?.let {return it}
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  }.generateKey()
 }
 fun load(): ServerSession? {
  val encoded=prefs.getString("session",null)?:return null
  val parts=encoded.split(':');require(parts.size==2)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))}
  val j=JSONObject(String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8))
  return ServerSession(j.getString("url"),j.getString("token"),j.getString("userCode"),j.getString("deviceId"),j.getLong("expiresAt"))
 }
 fun save(s: ServerSession) {
  val j=JSONObject().put("url",s.url).put("token",s.token).put("userCode",s.userCode).put("deviceId",s.deviceId).put("expiresAt",s.expiresAt)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
  val body=cipher.doFinal(j.toString().toByteArray(Charsets.UTF_8))
  check(prefs.edit().putString("session",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(body,Base64.NO_WRAP)).commit())
 }
 fun clear() {check(prefs.edit().remove("session").commit())}
 fun deviceId(): String = prefs.getString("installationId",null)?:java.util.UUID.randomUUID().toString().also {
  check(prefs.edit().putString("installationId",it).commit())
 }
 companion object {private const val ALIAS="andface_server_session_v1"}
}
