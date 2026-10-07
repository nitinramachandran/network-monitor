package com.nwmonitor.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}

    public record UserView(UUID id, String username, String displayName, String email, Role role,
                           boolean enabled, Instant createdAt, Instant lastLoginAt) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.getRole(),
                    u.isEnabled(), u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "letters, digits, . _ - only") String username,
            @NotBlank @Size(max = 128) String displayName,
            @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 10, max = 128) String password) {}

    public record UpdateUserRequest(@Size(max = 128) String displayName, @Email @Size(max = 255) String email, Boolean enabled) {}

    public record ResetPasswordRequest(@NotBlank @Size(min = 10, max = 128) String newPassword) {}
}
