# AndFace S195 서버 연동 구현 보고서

검증일: 2026-09-11. 기준 Android 소스: `9a358e8828c2db8339ef83d641d1892ad63b093c`. Android 서버 연동 소스, 독립 Java 백엔드 및 실기기 검증 결과를 함께 정리했습니다.

## 1. 새로 생성한 파일

아래 파일과 이 보고서를 추가했습니다.

- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/res/xml/network_security_debug.xml`
- `app/src/main/java/dev/andface/galaxy/network/ApiService.kt`
- `app/src/main/java/dev/andface/galaxy/network/RetrofitClient.kt`
- `app/src/main/java/dev/andface/galaxy/network/ServerConnectionActivity.kt`
- `app/src/main/java/dev/andface/galaxy/network/dto/Models.kt`
- `app/src/main/java/dev/andface/galaxy/network/repository/ResultMapper.kt`
- `app/src/main/java/dev/andface/galaxy/network/repository/ServerRepository.kt`
- `app/src/main/java/dev/andface/galaxy/network/repository/SessionStore.kt`
- `app/src/test/java/dev/andface/galaxy/network/ServerIntegrationTest.kt`
- `backend/.dockerignore`
- `backend/.env.example`
- `backend/.gitignore`
- `backend/Dockerfile`
- `backend/README.md`
- `backend/docker-compose.yml`
- `backend/pom.xml`
- `backend/scripts/run-local-demo.ps1`
- `backend/src/main/java/com/andface/backend/AndFaceApplication.java`
- `backend/src/main/java/com/andface/backend/common/ApiError.java`
- `backend/src/main/java/com/andface/backend/config/OpenApiConfig.java`
- `backend/src/main/java/com/andface/backend/config/SecurityConfig.java`
- `backend/src/main/java/com/andface/backend/controller/AdminAuthenticationController.java`
- `backend/src/main/java/com/andface/backend/controller/AuthenticationController.java`
- `backend/src/main/java/com/andface/backend/controller/DeviceController.java`
- `backend/src/main/java/com/andface/backend/controller/UserController.java`
- `backend/src/main/java/com/andface/backend/dto/request/Requests.java`
- `backend/src/main/java/com/andface/backend/dto/response/Responses.java`
- `backend/src/main/java/com/andface/backend/entity/AuthenticationLog.java`
- `backend/src/main/java/com/andface/backend/entity/Device.java`
- `backend/src/main/java/com/andface/backend/entity/UserAccount.java`
- `backend/src/main/java/com/andface/backend/exception/ApiException.java`
- `backend/src/main/java/com/andface/backend/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/andface/backend/repository/AuthenticationLogRepository.java`
- `backend/src/main/java/com/andface/backend/repository/DeviceRepository.java`
- `backend/src/main/java/com/andface/backend/repository/UserRepository.java`
- `backend/src/main/java/com/andface/backend/security/TokenService.java`
- `backend/src/main/java/com/andface/backend/service/AuthenticationService.java`
- `backend/src/main/java/com/andface/backend/service/DeviceService.java`
- `backend/src/main/java/com/andface/backend/service/UserService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V1__initial_schema.sql`
- `backend/src/test/java/com/andface/backend/AuthenticationServiceTest.java`
- `backend/src/test/java/com/andface/backend/BackendIntegrationTest.java`
- `backend/src/test/java/com/andface/backend/LocalDemoServer.java`

## 2. 수정한 기존 파일

- `.gitignore`: 서버 빌드/로컬 DB/실제 비밀 제외, `.env.example` 허용.
- `README.md`: 선택형 서버 연결 안내.
- `app/build.gradle.kts`: Retrofit/Gson/네트워크 시험 의존성, versionCode 195.
- `app/src/main/AndroidManifest.xml`: INTERNET 권한, 외부 앱이 열 수 없는 서버 설정 Activity.
- `app/src/main/java/dev/andface/galaxy/MainActivity.kt`: 설정 진입, 화면 표시 후 전송 호출, S195 표시.
- `app/src/test/java/dev/andface/galaxy/access/DeploymentSecurityConfigTest.kt`: 새 요구사항의 INTERNET 권한을 허용하도록 검사를 변경. 기존 보안 플래그 검사는 보존.

## 3. Java Backend 구조

Java 21 / Spring Boot 4.1.1 / Maven 독립 프로젝트입니다. Controller → Service → Repository 구조와 Security/JWT, 요청·응답 DTO, 예외 처리, Swagger, Flyway를 분리했습니다. 실행 및 코드 구조는 [README](README.md)에 있습니다.

## 4. Database Schema

`users`, `devices`, `authentication_logs` 테이블을 구성했습니다. BCrypt 비밀번호, JWT 계정 소유권, 다중 기기 등록, 비활성 기기 기록 제한, 이력 보존, eventId 재전송 중복 방지를 구현했습니다. 점수는 double precision이며 0..1 제약을 적용합니다. 이미지·랜드마크·baseline 컬럼은 없습니다. 정확한 DDL: `src/main/resources/db/migration/V1__initial_schema.sql`.

## 5. 구현한 REST API 목록

- 사용자: POST `/api/users/register`, POST `/api/users/login`, GET `/api/users/me`
- 기기: POST/GET `/api/devices`, DELETE `/api/devices/{id}`
- 판정: POST `/api/authentication/results`
- 이력: GET `/api/authentication/history`, GET `/api/authentication/history/{id}`
- 통계: GET `/api/authentication/statistics`
- 관리자: GET `/api/admin/authentication/history`, GET `/api/admin/authentication/history/{id}`, GET `/api/admin/authentication/statistics`
- 문서: `/swagger-ui/index.html`, `/v3/api-docs`

## 6. Android에서 통신을 추가한 위치

`MainActivity.renderAuthResult()`의 화면 갱신 마지막에 `ServerRepository.onRendered()`를 호출합니다. `ResultMapper`는 AuthResult의 실제 fuzzyScore, mahalanobisScore, finalScore, coverage, margin, livenessPassed, failureReason을 그대로 매핑합니다. 판정/화면 안정화 코드는 수정하지 않았으며, 이전 성공 화면 유지 중 현재 실패 프레임이 성공으로 전송되지 않게 구분합니다.

통신은 별도 작업 스레드에서 처리하고, 동일 프레임 반복·확인 중 상태를 제외합니다. 전송 실패는 얼굴 인증 엔진에 전달되지 않습니다. 서버 설정 화면을 열려면 우측 상단 버전 문구를 탭합니다.

## 7. 기존 인증 알고리즘 보존 확인

인증·화면 안정화·Fuzzy·Mahalanobis·특징·가림·등록·MediaPipe 관련 **28개 파일의 SHA-256이 변경 전과 동일**합니다. 현재 170개 특징, Fuzzy 0.72 / Mahalanobis 0.28, threshold, clean baseline, Liveness, SUCCESS/FAILED 결정 방식이 유지됩니다. 기존 Android 테스트 297개에 통신 테스트 7개를 더해 총 304개를 통과했습니다. Galaxy 업데이트는 `install -r`로 진행했고, 등록 3/3이 유지되는 것을 확인했습니다.

## 8. 실행 방법

Java 21 및 PostgreSQL과 환경 변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`을 설정한 다음 `mvn clean package`와 `java -jar target/andface-backend-1.0.0.jar`로 실행합니다. Docker가 있으면 `.env.example`을 참고해 `docker compose up --build`를 사용합니다. USB 개발 시연은 `scripts/run-local-demo.ps1` 및 `adb reverse tcp:8080 tcp:8080`을 사용합니다. 자세한 단계와 보안 범위는 [README](README.md)에 있습니다.

## 9. 실제 테스트 결과

- Java 원본 프로젝트 위치에서 `mvn clean package`: 성공. 서비스 단위 1개 + 실제 PostgreSQL/HTTP 통합 6개, 총 **7개 통과**.
- Android `testDebugUnitTest lintDebug assembleDebug assembleRelease`: 성공. **304개 통과**, lint 오류 0개/경고 65개. release 산출물은 운영 서명이 없는 unsigned APK입니다.
- 실제 Galaxy: 서버 계정 생성 → 로그인/JWT → 기기 등록 성공.
- 실제 Galaxy: 실패 판정과 성공 판정이 서버를 통해 PostgreSQL에 저장됨.
- 확인한 성공 기록: 2026-09-11 15:38:53 KST, Fuzzy `0.7932196363651459`, Mahalanobis `0.9345890375777332`, final `0.8328030687046704`, coverage `1.0`, margin `0.20310867805306265`, liveness `true`. 사용자가 화면의 ‘인증 완료’도 확인함.
- Swagger/OpenAPI 응답 및 이력·통계 API 조회 성공. 시험 DB를 정상 종료 후 프로젝트 backend/.local에 보존 복사하고, 원본 backend에서 서버를 재시작한 뒤 두 성공 기록이 그대로 조회되는 것도 확인.
- 서버 USB 터널 제거 및 앱 재시작 후에도 USER_1 SUCCESS 확인(사용자 화면 확인 + 새 앱 프로세스 로그). 연결 단절 중 화면에 3건 임시 대기 확인. 터널 복구 후 15:40:53 관측 SUCCESS(final=0.8500011160288252)가 15:41:35에 DB 저장됨. 즉 오프라인 인증과 연결 복구 후 대기 기록 전송을 모두 실기기로 검증.

## 10. 남아 있는 문제 및 추가 개선사항

- 서버는 클라이언트가 보고한 판정의 기록 서버입니다. 계정/JWT 검증만으로 실제 얼굴 신원이나 앱·기기의 위변조 여부를 독립적으로 증명하지 않습니다.
- 서버 통계는 중복 프레임을 제외한 기록 사건 수입니다. 시연 중 실패/성공 비율을 인식 정확도/FAR/FRR로 사용하면 안 됩니다.
- 임시 전송 큐는 메모리 최대 100건으로, 종료/로그아웃/계정 전환/토큰 만료 시 미전송 데이터가 사라집니다. JWT는 15분 후 재로그인이 필요합니다.
- 운영용 HTTPS, 기기 attestation/서명, 계정 가입 통제, 요청 제한, 백업·보존 정책 등은 운영 환경별 추가 작업입니다. 얼굴 인증 횟수 잠금을 다시 추가하지 않았습니다.
- Docker 미설치로 Compose 기동은 검증하지 않았습니다. 테스트·USB 시연의 PostgreSQL은 14.22이고 Compose 정의는 17입니다.
- 여러 휴대폰의 USER_1 슬롯을 서로 다른 인물로 운영하려면 전역 서버 계정과 로컬 얼굴 슬롯의 매핑 확장이 필요합니다. 현재 한 번에 한 서버 계정에 연결하며 일치하는 로컬 사용자 결과만 업로드합니다.
