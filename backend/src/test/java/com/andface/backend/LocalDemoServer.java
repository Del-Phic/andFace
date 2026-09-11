package com.andface.backend;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.*;
import java.util.Base64;
import org.springframework.boot.SpringApplication;

/**
 * Local USB demonstration only. This class and embedded PostgreSQL are excluded from the production
 * jar. Production uses the configured external database.
 */
public class LocalDemoServer {
  public static void main(String[] args) throws Exception {
    Path local = Path.of(".local").toAbsolutePath();
    Files.createDirectories(local);
    Path secretFile = local.resolve("jwt-secret");
    if (!Files.exists(secretFile)) {
      byte[] bytes = new byte[32];
      new java.security.SecureRandom().nextBytes(bytes);
      Files.writeString(
          secretFile, Base64.getEncoder().encodeToString(bytes), StandardOpenOption.CREATE_NEW);
    }
    var pg =
        EmbeddedPostgres.builder()
            .setPort(55432)
            .setServerConfig("listen_addresses", "127.0.0.1")
            .setDataDirectory(local.resolve("postgres"))
            .setCleanDataDirectory(false)
            .start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    pg.close();
                  } catch (Exception ignored) {
                  }
                }));
    new SpringApplication(AndFaceApplication.class)
        .run(
            "--server.address=127.0.0.1",
            "--server.port=8080",
            "--spring.datasource.url=" + pg.getJdbcUrl("postgres", "postgres"),
            "--spring.datasource.username=postgres",
            "--spring.datasource.password=",
            "--andface.jwt.secret=" + Files.readString(secretFile).trim());
  }
}
