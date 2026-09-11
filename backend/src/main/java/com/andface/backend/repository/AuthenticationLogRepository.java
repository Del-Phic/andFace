package com.andface.backend.repository;

import com.andface.backend.entity.AuthenticationLog;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AuthenticationLogRepository
    extends JpaRepository<AuthenticationLog, UUID>, JpaSpecificationExecutor<AuthenticationLog> {
  Optional<AuthenticationLog> findByUserIdAndEventId(UUID userId, UUID eventId);

  interface Totals {
    long getTotalCount();

    long getSuccessCount();

    double getAverageFuzzyScore();

    double getAverageMahalanobisScore();

    double getAverageFinalScore();

    double getAverageCoverage();
  }

  @Query(
      """
      select count(l) as totalCount,
      coalesce(sum(case when l.result=com.andface.backend.entity.AuthenticationLog.Result.SUCCESS then 1 else 0 end),0) as successCount,
      coalesce(avg(l.fuzzyScore),0.0) as averageFuzzyScore,
      coalesce(avg(l.mahalanobisScore),0.0) as averageMahalanobisScore,
      coalesce(avg(l.finalScore),0.0) as averageFinalScore,
      coalesce(avg(l.coverage),0.0) as averageCoverage
      from AuthenticationLog l where (:userId is null or l.user.id=:userId)
      and l.createdAt >= :from and l.createdAt < :until
      """)
  Totals totals(UUID userId, Instant from, Instant until);
}
