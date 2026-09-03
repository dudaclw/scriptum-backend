package com.scriptum.backend.infrastructure.database.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "APP_USER")
public class UserJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID", nullable = false, updatable = false, unique = true)
    private UUID id;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "EMAIL", nullable = false, unique = true)
    private String email;

    @Column(name = "PASSWORD", nullable = false)
    private String password;

    @Column(name = "AVATAR_URL", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "EMAIL_VERIFIED")
    private boolean emailVerified;

    @Column(name = "NEW_USER")
    private boolean newUser;

    @Column(name = "GOOGLE_AUTH_TOKEN")
    private String googleAuthToken;

}
