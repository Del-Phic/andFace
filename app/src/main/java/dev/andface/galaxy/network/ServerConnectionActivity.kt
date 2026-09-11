package dev.andface.galaxy.network
import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import dev.andface.galaxy.BuildConfig
import dev.andface.galaxy.network.repository.ServerRepository

/** Separate optional settings; never reads or writes enrolled face profiles. */
class ServerConnectionActivity: Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  val repository=ServerRepository.get(this)
  val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(32,64,32,32)}
  setContentView(ScrollView(this).apply{addView(root)})
  fun label(value:String)=TextView(this).apply{text=value;textSize=17f;root.addView(this)}
  fun field(hintText:String,value:String="",password:Boolean=false)=EditText(this).apply {
   hint=hintText;setText(value);setSingleLine();filterTouchesWhenObscured=true
   inputType=if(password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
   importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO;root.addView(this)
  }
  label("서버 기록 연결")
  label("얼굴 인증은 휴대폰에서 계속 처리합니다. 연결하면 판정과 점수만 전송합니다. 한 번에 하나의 사용자 계정을 연결합니다.")
  val url=field("서버 주소 (HTTPS)",if(BuildConfig.DEBUG) "http://127.0.0.1:8080/" else "https://")
  val code=field("사용자 코드",repository.accountCode?:"USER_1")
  val username=field("서버 계정 아이디")
  val password=field("비밀번호",password=true)
  val state=label(repository.status)
  val controls=mutableListOf<Button>()
  fun button(title:String,action:()->Unit) {val b=Button(this).apply{text=title;filterTouchesWhenObscured=true;setOnClickListener{action()}};controls.add(b);root.addView(b)}
  fun callback(message:String) {runOnUiThread{if(!isFinishing && !isDestroyed){state.text=message;controls.forEach{it.isEnabled=true};password.text.clear()}}}
  fun connect(register:Boolean) {
   controls.forEach{it.isEnabled=false};state.text="서버 연결 중…"
   repository.connect(url.text.toString(),code.text.toString().trim(),username.text.toString().trim(),password.text.toString(),register,::callback)
  }
  button("로그인 및 기기 연결"){connect(false)}
  button("새 계정 등록 및 연결"){connect(true)}
  button("전송 상태 새로고침"){state.text=repository.status}
  button("서버 연결 해제"){repository.disconnect(::callback)}
  button("얼굴 인증으로 돌아가기"){finish()}
 }
}
