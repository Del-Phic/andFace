package com.andface.backend.repository;

import com.andface.backend.entity.Device;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Device> findByUserIdAndDeviceId(UUID userId, String deviceId);

  List<Device> findByUserIdAndActiveTrueOrderByRegisteredAtDesc(UUID userId);

  Optional<Device> findByIdAndUserId(UUID id, UUID userId);
}
