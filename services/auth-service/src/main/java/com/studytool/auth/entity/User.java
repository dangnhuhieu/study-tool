package com.studytool.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "email", unique = true, nullable = false, length = 255)
  private String email;

  @Column(name = "password", length = 255)
  private String password;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Builder.Default
  @Column(name = "provider", length = 20, nullable = false)
  private String provider = "LOCAL";

  @Column(name = "provider_id", length = 255)
  private String providerId;

  @Builder.Default
  @Column(name = "role", length = 20, nullable = false)
  private String role = "ROLE_USER";

  @Builder.Default
  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", updatable = false, nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    this.updatedAt = Instant.now();
  }
}
