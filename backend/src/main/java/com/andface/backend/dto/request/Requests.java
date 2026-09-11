package com.andface.backend.dto.request;

import com.andface.backend.entity.AuthenticationLog.Result;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class Requests {
  private Requests() {}

  public record RegisterRequest(
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,31}") @Schema(example = "USER_1")
          String userCode,
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,64}") String username,
      @NotBlank @Size(min = 8, max = 72) @Schema(format = "password") String password) {}

  public record LoginRequest(
      @NotBlank @Size(max = 64) String username,
      @NotBlank @Size(max = 72) @Schema(format = "password") String password) {}

  public record DeviceRequest(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{1,100}") String deviceId,
      @NotBlank @Size(max = 100) String deviceName) {}

  @Schema(
      description =
          "Client-reported result, not independent server proof of face identity. No images or"
              + " landmarks.")
  public record ResultRequest(
      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,31}") String userCode,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{1,100}") String deviceId,
      @NotNull Result result,
      @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double fuzzyScore,
      @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double mahalanobisScore,
      @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double finalScore,
      @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double coverage,
      @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double margin,
      @NotNull Boolean liveness,
      @Size(max = 80) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String failureReason,
      @Schema(description = "Optional retry idempotency key, unique per account") UUID eventId,
      @PastOrPresent
          @Schema(
              description =
                  "Optional client observation time. Filters use server createdAt in UTC.")
          Instant occurredAt) {}
}
