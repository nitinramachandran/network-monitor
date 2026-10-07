package com.nwmonitor.auth;

import com.nwmonitor.user.UserDtos.UserView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 128) String newPassword) {}
    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds, UserView user) {}
}
