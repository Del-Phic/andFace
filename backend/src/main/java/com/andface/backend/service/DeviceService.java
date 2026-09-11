package com.andface.backend.service;

import com.andface.backend.dto.request.Requests.DeviceRequest;
import com.andface.backend.dto.response.Responses.DeviceView;
import com.andface.backend.entity.*;
import com.andface.backend.exception.ApiException;
import com.andface.backend.repository.DeviceRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {
  private final DeviceRepository devices;

  public DeviceService(DeviceRepository devices) {
    this.devices = devices;
  }

  @Transactional
  public DeviceView register(UserAccount user, DeviceRequest request) {
    var device =
        devices
            .findByUserIdAndDeviceId(user.getId(), request.deviceId())
            .orElseGet(() -> new Device(user, request.deviceId(), request.deviceName()));
    device.registerAgain(request.deviceName());
    return DeviceView.of(devices.saveAndFlush(device));
  }

  @Transactional(readOnly = true)
  public List<DeviceView> list(UserAccount user) {
    return devices.findByUserIdAndActiveTrueOrderByRegisteredAtDesc(user.getId()).stream()
        .map(DeviceView::of)
        .toList();
  }

  @Transactional
  public void delete(UserAccount user, UUID id) {
    var device =
        devices
            .findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> ApiException.missing("DEVICE_NOT_FOUND", "Device not found"));
    device.revoke();
  }
}
