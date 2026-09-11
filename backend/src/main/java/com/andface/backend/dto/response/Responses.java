package com.andface.backend.dto.response;

import com.andface.backend.entity.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class Responses {
  private Responses() {}

  public record UserView(
      UUID id,
      String userCode,
      String username,
      String role,
      Instant createdAt,
      Instant updatedAt) {
    public static UserView of(UserAccount u) {
      return new UserView(
          u.getId(),
          u.getUserCode(),
          u.getUsername(),
          u.getRole().name(),
          u.getCreatedAt(),
          u.getUpdatedAt());
    }
  }

  public record TokenView(String accessToken, String tokenType, long expiresIn, UserView user) {}

  public record DeviceView(
      UUID id,
      String deviceId,
      String deviceName,
      Instant registeredAt,
      Instant lastAccessAt,
      boolean active) {
    public static DeviceView of(Device d) {
      return new DeviceView(
          d.getId(),
          d.getDeviceId(),
          d.getDeviceName(),
          d.getRegisteredAt(),
          d.getLastAccessAt(),
          d.isActive());
    }
  }

  public record LogView(
      UUID id,
      String userCode,
      String deviceId,
      UUID eventId,
      String result,
      double fuzzyScore,
      double mahalanobisScore,
      double finalScore,
      double coverage,
      double margin,
      boolean liveness,
      String failureReason,
      Instant occurredAt,
      Instant createdAt) {
    public static LogView of(AuthenticationLog l) {
      return new LogView(
          l.getId(),
          l.getUser().getUserCode(),
          l.getDevice().getDeviceId(),
          l.getEventId(),
          l.getResult().name(),
          l.getFuzzyScore(),
          l.getMahalanobisScore(),
          l.getFinalScore(),
          l.getCoverage(),
          l.getMargin(),
          l.isLiveness(),
          l.getFailureReason(),
          l.getOccurredAt(),
          l.getCreatedAt());
    }
  }

  public record PageView<T>(
      List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageView<T> of(Page<T> page) {
      return new PageView<>(
          page.getContent(),
          page.getNumber(),
          page.getSize(),
          page.getTotalElements(),
          page.getTotalPages());
    }
  }

  public record Statistics(
      long totalCount,
      long successCount,
      long failureCount,
      double successRate,
      double averageFuzzyScore,
      double averageMahalanobisScore,
      double averageFinalScore,
      double averageCoverage) {}
}
