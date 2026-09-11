package com.andface.backend.controller;

import com.andface.backend.dto.request.Requests.ResultRequest;
import com.andface.backend.dto.response.Responses.*;
import com.andface.backend.entity.AuthenticationLog.Result;
import com.andface.backend.service.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/authentication")
public class AuthenticationController {
  private final UserService users;
  private final AuthenticationService service;

  public AuthenticationController(UserService users, AuthenticationService service) {
    this.users = users;
    this.service = service;
  }

  @PostMapping("/results")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Store a client-reported final result",
      description =
          "400 invalid metrics/result; 401 invalid JWT; 403 userCode mismatch; 404 unregistered"
              + " device; 409 eventId conflict. Repeating the same eventId and payload returns the"
              + " existing record.")
  public LogView save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ResultRequest r) {
    return service.save(users.current(jwt), r);
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
    return service.history(users.current(jwt), userCode, from, to, result, page, size, false);
  }

  @GetMapping("/history/{id}")
  @Operation(
      summary = "Get an authentication record",
      description =
          "401 invalid JWT; 403 non-admin on admin route; 404 absent or inaccessible log.")
  public LogView detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.detail(users.current(jwt), id, false);
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
    return service.statistics(users.current(jwt), userCode, from, to, false);
  }
}
