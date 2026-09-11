package com.andface.backend.controller;

import com.andface.backend.dto.request.Requests.*;
import com.andface.backend.dto.response.Responses.*;
import com.andface.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirements
  @Operation(
      summary = "Register an account with USER role",
      description =
          "400 invalid input; 409 duplicate username/userCode. Password is BCrypt hashed.")
  public UserView register(@Valid @RequestBody RegisterRequest r) {
    return users.register(r);
  }

  @PostMapping("/login")
  @SecurityRequirements
  @Operation(
      summary = "Log in and issue a 15-minute JWT",
      description = "400 invalid request; 401 invalid credentials. Password is never returned.")
  public TokenView login(@Valid @RequestBody LoginRequest r) {
    return users.login(r);
  }

  @GetMapping("/me")
  @Operation(
      summary = "Get current account",
      description = "401 missing/invalid/expired token; 404 unknown user.")
  public UserView me(@AuthenticationPrincipal Jwt jwt) {
    return UserView.of(users.current(jwt));
  }
}
