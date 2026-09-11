package com.andface.backend;

import static org.junit.jupiter.api.Assertions.*;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import tools.jackson.databind.*;

class BackendIntegrationTest {
  static EmbeddedPostgres pg;
  static ConfigurableApplicationContext app;
  static String base;
  static final ObjectMapper json = new ObjectMapper();
  static final HttpClient http = HttpClient.newHttpClient();

  @BeforeAll
  static void start() throws Exception {
    pg = EmbeddedPostgres.builder().setPort(0).start();
    byte[] secret = new byte[32];
    new java.security.SecureRandom().nextBytes(secret);
    app =
        new SpringApplication(AndFaceApplication.class)
            .run(
                "--server.port=0",
                "--spring.datasource.url=" + pg.getJdbcUrl("postgres", "postgres"),
                "--spring.datasource.username=postgres",
                "--spring.datasource.password=",
                "--andface.jwt.secret=" + Base64.getEncoder().encodeToString(secret));
    base = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
  }

  @AfterAll
  static void stop() throws Exception {
    if (app != null) app.close();
    if (pg != null) pg.close();
  }

  record Reply(int status, JsonNode body) {}

  static Reply call(String method, String path, String token, Object body) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json");
    if (token != null) b.header("Authorization", "Bearer " + token);
    b.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    return new Reply(
        r.statusCode(), r.body().isBlank() ? json.nullNode() : json.readTree(r.body()));
  }

  static String code() {
    return "U_"
        + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
  }

  static String account(String code) throws Exception {
    var r =
        call(
            "POST",
            "/api/users/register",
            null,
            Map.of("userCode", code, "username", code, "password", "test-password-8472"));
    assertEquals(201, r.status, r.body.toString());
    assertFalse(r.body.has("password"));
    r =
        call(
            "POST",
            "/api/users/login",
            null,
            Map.of("username", code, "password", "test-password-8472"));
    assertEquals(200, r.status, r.body.toString());
    return r.body.path("accessToken").asText();
  }

  static Map<String, Object> payload(String code, String result) {
    var m = new HashMap<String, Object>();
    m.put("userCode", code);
    m.put("deviceId", "GALAXY_TEST");
    m.put("result", result);
    m.put("fuzzyScore", 0.87);
    m.put("mahalanobisScore", 0.81);
    m.put("finalScore", 0.8532);
    m.put("coverage", 0.92);
    m.put("margin", 0.18);
    m.put("liveness", true);
    m.put("failureReason", result.equals("SUCCESS") ? null : "LOW_SCORE");
    m.put("eventId", UUID.randomUUID().toString());
    m.put("occurredAt", Instant.now().minusSeconds(1).toString());
    return m;
  }

  static String device(String token) throws Exception {
    var r =
        call(
            "POST",
            "/api/devices",
            token,
            Map.of("deviceId", "GALAXY_TEST", "deviceName", "Integration test"));
    assertEquals(201, r.status, r.body.toString());
    return r.body.path("id").asText();
  }

  @Test
  void registrationLoginAndBcrypt() throws Exception {
    String c = code(), t = account(c);
    assertEquals(200, call("GET", "/api/users/me", t, null).status);
    assertEquals(
        409,
        call(
                "POST",
                "/api/users/register",
                null,
                Map.of("userCode", c, "username", c, "password", "password8472"))
            .status);
    assertEquals(
        401,
        call("POST", "/api/users/login", null, Map.of("username", c, "password", "wrongpassword"))
            .status);
    assertEquals(
        400,
        call("POST", "/api/users/login", null, Map.of("username", c, "password", "한".repeat(30)))
            .status);
    String hash =
        app.getBean(JdbcTemplate.class)
            .queryForObject("select password_hash from users where user_code=?", String.class, c);
    assertTrue(hash.startsWith("$2"));
    assertNotEquals("test-password-8472", hash);
  }

  @Test
  void jwtAccessExpiryAndUnknownSubject() throws Exception {
    assertEquals(401, call("GET", "/api/users/me", null, null).status);
    assertEquals(401, call("GET", "/api/users/me", "garbage", null).status);
    String c = code(), t = account(c);
    var me = call("GET", "/api/users/me", t, null);
    var encoder = app.getBean(JwtEncoder.class);
    for (String subject :
        List.of(me.body.path("id").asText(), "not-a-uuid", UUID.randomUUID().toString())) {
      Instant issued = Instant.now().minusSeconds(3600);
      var claims =
          JwtClaimsSet.builder()
              .issuer("andface-backend")
              .subject(subject)
              .issuedAt(issued)
              .expiresAt(
                  subject.equals(me.body.path("id").asText())
                      ? issued.plusSeconds(30)
                      : Instant.now().plusSeconds(900))
              .build();
      String bad =
          encoder
              .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
              .getTokenValue();
      assertEquals(401, call("GET", "/api/users/me", bad, null).status);
    }
  }

  @Test
  void saveHistoryStatisticsAndIdempotency() throws Exception {
    String c = code(), t = account(c);
    device(t);
    var p = payload(c, "SUCCESS");
    var first = call("POST", "/api/authentication/results", t, p);
    assertEquals(201, first.status, first.body.toString());
    assertEquals(
        first.body.path("id"), call("POST", "/api/authentication/results", t, p).body.path("id"));
    p.put("finalScore", 0.9);
    assertEquals(409, call("POST", "/api/authentication/results", t, p).status);
    assertEquals(201, call("POST", "/api/authentication/results", t, payload(c, "FAILED")).status);
    var h = call("GET", "/api/authentication/history?userCode=" + c + "&size=1", t, null);
    assertEquals(200, h.status, h.body.toString());
    assertEquals(2, h.body.path("totalElements").asInt());
    assertEquals(1, h.body.path("content").size());
    assertEquals(
        1,
        call("GET", "/api/authentication/history?result=SUCCESS", t, null)
            .body
            .path("totalElements")
            .asInt());
    assertEquals(
        200,
        call("GET", "/api/authentication/history/" + first.body.path("id").asText(), t, null)
            .status);
    var s = call("GET", "/api/authentication/statistics", t, null);
    assertEquals(200, s.status, s.body.toString());
    assertEquals(2, s.body.path("totalCount").asInt());
    assertEquals(1, s.body.path("successCount").asInt());
    assertEquals(1, s.body.path("failureCount").asInt());
    assertEquals(0.5, s.body.path("successRate").asDouble(), 1e-9);
    assertEquals(0.8532, s.body.path("averageFinalScore").asDouble(), 1e-9);
    assertEquals(
        0,
        call("GET", "/api/authentication/statistics?from=2000-01-01&to=2000-01-02", t, null)
            .body
            .path("totalCount")
            .asInt());
  }

  @Test
  void ownershipAndRevokedDevice() throws Exception {
    String c = code(), t = account(c), other = account(code()), id = device(t);
    var log = call("POST", "/api/authentication/results", t, payload(c, "SUCCESS"));
    assertEquals(
        403, call("POST", "/api/authentication/results", other, payload(c, "SUCCESS")).status);
    assertEquals(403, call("GET", "/api/authentication/history?userCode=" + c, other, null).status);
    assertEquals(
        404,
        call("GET", "/api/authentication/history/" + log.body.path("id").asText(), other, null)
            .status);
    assertEquals(404, call("DELETE", "/api/devices/" + id, other, null).status);
    assertEquals(204, call("DELETE", "/api/devices/" + id, t, null).status);
    assertEquals(404, call("POST", "/api/authentication/results", t, payload(c, "SUCCESS")).status);
    assertEquals(
        1, call("GET", "/api/authentication/history", t, null).body.path("totalElements").asInt());
  }

  @Test
  void validationAndUniformErrors() throws Exception {
    String c = code(), t = account(c);
    device(t);
    for (String field :
        List.of("fuzzyScore", "mahalanobisScore", "finalScore", "coverage", "margin")) {
      var p = payload(c, "SUCCESS");
      p.put(field, 1.01);
      var r = call("POST", "/api/authentication/results", t, p);
      assertEquals(400, r.status, r.body.toString());
      assertEquals(400, r.body.path("status").asInt());
      assertTrue(r.body.has("timestamp"));
    }
    var p = payload(c, "SUCCESS");
    p.put("liveness", false);
    assertEquals(400, call("POST", "/api/authentication/results", t, p).status);
    p = payload(c, "SUCCESS");
    p.put("landmarks", List.of(1, 2, 3));
    assertEquals(400, call("POST", "/api/authentication/results", t, p).status);
    for (String query :
        List.of("size=101", "page=-1", "result=BOGUS", "from=2026-09-30&to=2026-09-01", "from=bad"))
      assertEquals(400, call("GET", "/api/authentication/history?" + query, t, null).status);
  }

  @Test
  void adminScopeAndOpenApi() throws Exception {
    String c = code(), t = account(c);
    assertEquals(403, call("GET", "/api/admin/authentication/history", t, null).status);
    app.getBean(JdbcTemplate.class).update("update users set role='ADMIN' where user_code=?", c);
    assertEquals(200, call("GET", "/api/admin/authentication/history", t, null).status);
    assertEquals(200, call("GET", "/api/admin/authentication/statistics", t, null).status);
    var api = call("GET", "/v3/api-docs", null, null);
    assertEquals(200, api.status, api.body.toString());
    assertTrue(api.body.path("paths").has("/api/authentication/results"));
  }
}
