package com.andface.backend.service;

import com.andface.backend.dto.request.Requests.*;
import com.andface.backend.dto.response.Responses.*;
import com.andface.backend.entity.UserAccount;
import com.andface.backend.exception.ApiException;
import com.andface.backend.repository.UserRepository;
import com.andface.backend.security.TokenService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final TokenService tokens;
  private final String dummyHash;

  public UserService(UserRepository users, PasswordEncoder passwords, TokenService tokens) {
    this.users = users;
    this.passwords = passwords;
    this.tokens = tokens;
    dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public UserView register(RegisterRequest r) {
    if (r.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.invalid("Password exceeds BCrypt's 72-byte limit");
    if (users.existsByUsernameOrUserCode(r.username(), r.userCode()))
      throw new ApiException(
          HttpStatus.CONFLICT, "DUPLICATE_USER", "Username or userCode already exists");
    return UserView.of(
        users.saveAndFlush(
            new UserAccount(r.userCode(), r.username(), passwords.encode(r.password()))));
  }

  @Transactional(readOnly = true)
  public TokenView login(LoginRequest r) {
    if (r.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiException.invalid("Password exceeds BCrypt byte limit");
    var user = users.findByUsername(r.username());
    boolean valid =
        passwords.matches(r.password(), user.map(UserAccount::getPasswordHash).orElse(dummyHash));
    if (!valid || user.isEmpty())
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    return tokens.issue(user.get());
  }

  @Transactional(readOnly = true)
  public UserAccount current(Jwt jwt) {
    return users
        .findById(UUID.fromString(jwt.getSubject()))
        .orElseThrow(() -> ApiException.missing("USER_NOT_FOUND", "User not found"));
  }
}
