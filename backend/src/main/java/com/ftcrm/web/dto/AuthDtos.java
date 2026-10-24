package com.ftcrm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9._-]{3,32}$") String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 1, max = 40) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 72) String password
    ) {
    }

    public record UserResponse(
            Long id,
            String username,
            String displayName,
            String role,
            String roleLabel
    ) {
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
