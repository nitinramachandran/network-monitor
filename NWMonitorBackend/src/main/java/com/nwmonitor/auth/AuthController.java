package com.nwmonitor.auth;

import com.nwmonitor.auth.AuthDtos.*;
import com.nwmonitor.security.AppPrincipal;
import com.nwmonitor.user.UserDtos.UserView;
import com.nwmonitor.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final UserService users;

    public AuthController(AuthService auth, UserService users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest req) {
        return auth.login(r.username(), r.password(), req.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest r) { return auth.refresh(r.refreshToken()); }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest r) { auth.logout(r.refreshToken()); }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AppPrincipal p) { return UserView.of(users.get(p.userId())); }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AppPrincipal p, @Valid @RequestBody ChangePasswordRequest r) {
        auth.changePassword(p.userId(), r.currentPassword(), r.newPassword());
    }
}
