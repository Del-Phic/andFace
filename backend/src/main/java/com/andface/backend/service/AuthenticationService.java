package com.andface.backend.service;

import com.andface.backend.dto.request.Requests.ResultRequest;
import com.andface.backend.dto.response.Responses.*;
import com.andface.backend.entity.*;
import com.andface.backend.exception.ApiException;
import com.andface.backend.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {
  private final AuthenticationLogRepository logs;
  private final DeviceRepository devices;
  private final UserRepository users;

  public AuthenticationService(
      AuthenticationLogRepository logs, DeviceRepository devices, UserRepository users) {
    this.logs = logs;
    this.devices = devices;
    this.users = users;
  }

  @Transactional
  public LogView save(UserAccount user, ResultRequest r) {
    if (!user.getUserCode().equals(r.userCode())) throw ApiException.denied();
    for (double n :
        new double[] {
          r.fuzzyScore(), r.mahalanobisScore(), r.finalScore(), r.coverage(), r.margin()
        })
      if (!Double.isFinite(n) || n < 0 || n > 1)
        throw ApiException.invalid("Metrics must be finite numbers between 0 and 1");
    if (r.result() == AuthenticationLog.Result.SUCCESS
        && (!r.liveness() || r.failureReason() != null))
      throw ApiException.invalid("SUCCESS requires liveness=true and no failureReason");
    if (r.result() == AuthenticationLog.Result.FAILED
        && (r.failureReason() == null
            || r.failureReason().isBlank()
            || r.failureReason().equals("NONE")))
      throw ApiException.invalid("FAILED requires failureReason");
    var device =
        devices
            .findByUserIdAndDeviceId(user.getId(), r.deviceId())
            .filter(Device::isActive)
            .orElseThrow(
                () ->
                    ApiException.missing(
                        "DEVICE_NOT_REGISTERED", "Active device registration required"));
    if (r.eventId() != null) {
      var previous = logs.findByUserIdAndEventId(user.getId(), r.eventId());
      if (previous.isPresent()) {
        var old = previous.get();
        if (!old.getDevice().getId().equals(device.getId())
            || old.getResult() != r.result()
            || old.getFuzzyScore() != r.fuzzyScore()
            || old.getMahalanobisScore() != r.mahalanobisScore()
            || old.getFinalScore() != r.finalScore()
            || old.getCoverage() != r.coverage()
            || old.getMargin() != r.margin()
            || old.isLiveness() != r.liveness()
            || !Objects.equals(old.getFailureReason(), r.failureReason())
            || (r.occurredAt() != null
                && !old.getOccurredAt()
                    .equals(r.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS))))
          throw new ApiException(
              org.springframework.http.HttpStatus.CONFLICT,
              "EVENT_CONFLICT",
              "eventId already used with different data");
        return LogView.of(old);
      }
    }
    device.touch();
    return LogView.of(logs.saveAndFlush(new AuthenticationLog(user, device, r)));
  }

  private UUID scope(UserAccount actor, String code, boolean admin) {
    if (admin && actor.getRole() != UserAccount.Role.ADMIN) throw ApiException.denied();
    if (!admin) {
      if (code != null && !code.equals(actor.getUserCode())) throw ApiException.denied();
      return actor.getId();
    }
    return code == null
        ? null
        : users
            .findByUserCode(code)
            .orElseThrow(() -> ApiException.missing("USER_NOT_FOUND", "User not found"))
            .getId();
  }

  private Instant[] range(LocalDate from, LocalDate to) {
    if (from != null && to != null && from.isAfter(to))
      throw ApiException.invalid("from must not be after to");
    if ((from != null && (from.getYear() < 1970 || from.getYear() > 9998))
        || (to != null && (to.getYear() < 1970 || to.getYear() > 9998)))
      throw ApiException.invalid("Dates must be between 1970 and 9998");
    return new Instant[] {
      from == null ? Instant.EPOCH : from.atStartOfDay(ZoneOffset.UTC).toInstant(),
      to == null
          ? LocalDate.of(9999, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
          : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
    };
  }

  @Transactional(readOnly = true)
  public PageView<LogView> history(
      UserAccount actor,
      String code,
      LocalDate from,
      LocalDate to,
      AuthenticationLog.Result result,
      int page,
      int size,
      boolean admin) {
    if (page < 0 || size < 1 || size > 100)
      throw ApiException.invalid("page >= 0 and size between 1 and 100 required");
    UUID id = scope(actor, code, admin);
    Instant[] dates = range(from, to);
    Specification<AuthenticationLog> spec =
        (r, q, b) -> {
          List<jakarta.persistence.criteria.Predicate> conditions = new ArrayList<>();
          if (id != null) conditions.add(b.equal(r.get("user").get("id"), id));
          conditions.add(b.greaterThanOrEqualTo(r.get("createdAt"), dates[0]));
          conditions.add(b.lessThan(r.get("createdAt"), dates[1]));
          if (result != null) conditions.add(b.equal(r.get("result"), result));
          return b.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    return PageView.of(
        logs.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
            .map(LogView::of));
  }

  @Transactional(readOnly = true)
  public LogView detail(UserAccount actor, UUID id, boolean admin) {
    if (admin && actor.getRole() != UserAccount.Role.ADMIN) throw ApiException.denied();
    var log =
        logs.findById(id)
            .filter(l -> admin || l.getUser().getId().equals(actor.getId()))
            .orElseThrow(
                () -> ApiException.missing("LOG_NOT_FOUND", "Authentication log not found"));
    return LogView.of(log);
  }

  @Transactional(readOnly = true)
  public Statistics statistics(
      UserAccount actor, String code, LocalDate from, LocalDate to, boolean admin) {
    UUID id = scope(actor, code, admin);
    Instant[] dates = range(from, to);
    var t = logs.totals(id, dates[0], dates[1]);
    return new Statistics(
        t.getTotalCount(),
        t.getSuccessCount(),
        t.getTotalCount() - t.getSuccessCount(),
        t.getTotalCount() == 0 ? 0 : (double) t.getSuccessCount() / t.getTotalCount(),
        t.getAverageFuzzyScore(),
        t.getAverageMahalanobisScore(),
        t.getAverageFinalScore(),
        t.getAverageCoverage());
  }
}
