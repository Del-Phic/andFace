package com.andface.backend.controller;

import com.andface.backend.dto.request.Requests.DeviceRequest;
import com.andface.backend.dto.response.Responses.DeviceView;
import com.andface.backend.service.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
  private final UserService users;
  private final DeviceService devices;

  public DeviceController(UserService users, DeviceService devices) {
    this.users = users;
    this.devices = devices;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Register or reactivate this account's device",
      description = "400 invalid device fields; 401 invalid token.")
  public DeviceView register(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeviceRequest r) {
    return devices.register(users.current(jwt), r);
  }

  @GetMapping
  @Operation(summary = "List own active devices", description = "401 invalid token.")
  public List<DeviceView> list(@AuthenticationPrincipal Jwt jwt) {
    return devices.list(users.current(jwt));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Revoke own device while preserving history",
      description = "401 invalid token; 404 device absent or owned by another account.")
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    devices.delete(users.current(jwt), id);
  }
}
