package com.andface.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "devices")
@Getter
@NoArgsConstructor
public class Device {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private UserAccount user;

  @Column(nullable = false, length = 100)
  private String deviceId;

  @Column(nullable = false, length = 100)
  private String deviceName;

  @Column(nullable = false)
  private Instant registeredAt;

  @Column(nullable = false)
  private Instant lastAccessAt;

  @Column(nullable = false)
  private boolean active;

  public Device(UserAccount user, String deviceId, String deviceName) {
    id = UUID.randomUUID();
    this.user = user;
    this.deviceId = deviceId;
    this.deviceName = deviceName;
    registeredAt = lastAccessAt = Instant.now();
    active = true;
  }

  public void registerAgain(String name) {
    deviceName = name;
    active = true;
    touch();
  }

  public void touch() {
    lastAccessAt = Instant.now();
  }

  public void revoke() {
    active = false;
  }
}
