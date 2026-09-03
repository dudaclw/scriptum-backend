package com.scriptum.backend.api.controllers;

import com.scriptum.backend.domain.request.UserUpdateRequestBody;
import com.scriptum.backend.domain.response.UserResponseBody;
import com.scriptum.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseBody> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        UserResponseBody response = userService.getCurrentUserResponse(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseBody> getUserById(@PathVariable UUID id) {
        UserResponseBody response = userService.getUserResponseById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseBody> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequestBody request) {
        UserResponseBody response = userService.updateUser(id, request);
        return ResponseEntity.ok(response);
    }
}
