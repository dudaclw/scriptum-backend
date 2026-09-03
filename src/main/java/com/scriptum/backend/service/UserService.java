package com.scriptum.backend.service;

import com.scriptum.backend.configuration.exception.BadRequestException;
import com.scriptum.backend.domain.request.UserUpdateRequestBody;
import com.scriptum.backend.domain.response.UserResponseBody;
import com.scriptum.backend.infrastructure.database.repository.IUserJpaRepository;
import com.scriptum.backend.infrastructure.database.jpa.UserJpaEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final IUserJpaRepository repository;
    private final PasswordEncoder passwordEncoder;

    public Optional<UserJpaEntity> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    public UserJpaEntity findByIdOrElseThrow(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
    }

    public UserJpaEntity save(UserJpaEntity user) {
        return repository.save(user);
    }

    public UserResponseBody mapToResponseBody(UserJpaEntity user) {
        UserResponseBody response = new UserResponseBody();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setEmailVerified(user.isEmailVerified());
        return response;
    }

    public UserResponseBody getUserResponseById(UUID id) {
        UserJpaEntity user = findByIdOrElseThrow(id);
        return mapToResponseBody(user);
    }

    public UserResponseBody getCurrentUserResponse(String email) {
        UserJpaEntity user = findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return mapToResponseBody(user);
    }

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            throw new BadRequestException("Authentication required");
        }

        return findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("User not found"))
                .getId();
    }

    @Transactional
    public UserResponseBody updateUser(UUID id, UserUpdateRequestBody request) {
        // Verify user exists
        UserJpaEntity user = findByIdOrElseThrow(id);

        // Check if email is being changed and if it's already in use
        if (!user.getEmail().equals(request.getEmail())) {
            findByEmail(request.getEmail()).ifPresent(u -> {
                throw new IllegalArgumentException("Email already in use");
            });
        }

        // Update user details
        user.setName(request.getName());
        user.setEmail(request.getEmail());

        // Only update password if provided
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // Update avatar if provided
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        UserJpaEntity updatedUser = save(user);
        return mapToResponseBody(updatedUser);
    }
}
