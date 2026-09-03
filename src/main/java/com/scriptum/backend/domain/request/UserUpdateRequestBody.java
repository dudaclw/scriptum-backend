package com.scriptum.backend.domain.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequestBody {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    // Optional: only changes the password when present, unlike UserRequestBody
    // (shared with /auth/register, where a password is always required).
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private String avatarUrl;
}
