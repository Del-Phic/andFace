package com.andface.backend.entity;

import com.andface.backend.dto.request.Requests.ResultRequest;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "authentication_logs")
@Getter
@NoArgsConstructor
public class AuthenticationLog {
  public enum Result {
    SUCCESS,
    FAILED
  }

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private UserAccount user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Device device;

  @Column(nullable = false)
  private UUID eventId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Result result;

  @Column(nullable = false)
  private double fuzzyScore;

  @Column(nullable = false)
  private double mahalanobisScore;

  @Column(nullable = false)
  private double finalScore;

  @Column(nullable = false)
  private double coverage;

  @Column(nullable = false)
  private double margin;

  @Column(nullable = false)
  private boolean liveness;

  @Column(length = 80)
  private String failureReason;

  @Column(nullable = false)
  private Instant occurredAt;

  @Column(nullable = false)
  private Instant createdAt;

  public AuthenticationLog(UserAccount user, Device device, ResultRequest request) {
    id = UUID.randomUUID();
    this.user = user;
    this.device = device;
    eventId = request.eventId() == null ? UUID.randomUUID() : request.eventId();
    result = request.result();
    fuzzyScore = request.fuzzyScore();
    mahalanobisScore = request.mahalanobisScore();
    finalScore = request.finalScore();
    coverage = request.coverage();
    margin = request.margin();
    liveness = request.liveness();
    failureReason = request.failureReason();
    createdAt = Instant.now();
    occurredAt =
        request.occurredAt() == null
            ? createdAt
            : request.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }
}
