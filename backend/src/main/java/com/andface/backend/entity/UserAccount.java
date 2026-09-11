package com.andface.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class UserAccount {
  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 32)
  private String userCode;

  @Column(nullable = false, unique = true, length = 64)
  private String username;

  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Role role;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  public enum Role {
    USER,
    ADMIN
  }

  public UserAccount(String userCode, String username, String passwordHash) {
    this.id = UUID.randomUUID();
    this.userCode = userCode;
    this.username = username;
    this.passwordHash = passwordHash;
    role = Role.USER;
    createdAt = updatedAt = Instant.now();
  }
}
