package com.andface.backend.controller;

import com.andface.backend.dto.response.Responses.*;
import com.andface.backend.entity.AuthenticationLog.Result;
import com.andface.backend.service.*;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/authentication")
public class AdminAuthenticationController {
  private final UserService users;
  private final AuthenticationService service;

  public AdminAuthenticationController(UserService users, AuthenticationService service) {
    this.users = users;
    this.service = service;
  }

  @GetMapping("/history")
  @Operation(
      summary = "Query paginated authentication history",
      description =
          "Dates use server receipt time in UTC; to date inclusive. size 1..100. 400 invalid"
              + " filters; 401 invalid JWT; 403 unauthorized scope; 404 unknown user.")
  public PageView<LogView> history(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String userCode,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) Result result,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.history(users.current(jwt), userCode, from, to, result, page, size, true);
  }

  @GetMapping("/history/{id}")
  @Operation(
      summary = "Get an authentication record",
      description =
          "401 invalid JWT; 403 non-admin on admin route; 404 absent or inaccessible log.")
  public LogView detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.detail(users.current(jwt), id, true);
  }

  @GetMapping("/statistics")
  @Operation(
      summary = "Get counts and mean scores",
      description =
          "successRate is 0..1; empty data returns zeros. Same UTC date filters as history. 400"
              + " invalid dates; 401 invalid JWT; 403 unauthorized scope; 404 unknown user.")
  public Statistics statistics(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String userCode,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.statistics(users.current(jwt), userCode, from, to, true);
  }
}
