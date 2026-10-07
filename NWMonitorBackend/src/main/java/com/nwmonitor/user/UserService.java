package com.nwmonitor.user;

import com.nwmonitor.auth.RefreshTokenRepository;
import com.nwmonitor.common.ApiException;
import com.nwmonitor.user.UserDtos.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
    }

    public List<User> list() { return users.findAllByOrderByCreatedAtAsc(); }

    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    public boolean rootExists() { return users.existsByRole(Role.ROOT); }

    /** The only way a ROOT user comes into existence; refuses when one already exists. */
    @Transactional
    public User createRoot(String username, String displayName, String email, String password) {
        if (rootExists()) throw ApiException.conflict("A root user already exists");
        return create(username, displayName, email, password, Role.ROOT);
    }

    @Transactional
    public User createAdmin(CreateUserRequest r) {
        return create(r.username(), r.displayName(), r.email(), r.password(), Role.ADMIN);
    }

    private User create(String username, String displayName, String email, String password, Role role) {
        if (users.existsByUsernameIgnoreCase(username)) throw ApiException.conflict("Username already taken");
        return users.save(new User(username.trim(), displayName.trim(), email, encoder.encode(password), role));
    }

    @Transactional
    public User update(UUID id, UpdateUserRequest r) {
        User u = get(id);
        if (r.displayName() != null && !r.displayName().isBlank()) u.setDisplayName(r.displayName().trim());
        if (r.email() != null) u.setEmail(r.email().isBlank() ? null : r.email());
        if (r.enabled() != null && r.enabled() != u.isEnabled()) {
            if (u.getRole() == Role.ROOT) throw ApiException.badRequest("The root user cannot be disabled");
            u.setEnabled(r.enabled());
            if (!r.enabled()) refreshTokens.revokeAllForUser(u.getId());
        }
        return u;
    }

    @Transactional
    public void delete(UUID id) {
        User u = get(id);
        if (u.getRole() == Role.ROOT) throw ApiException.badRequest("The root user cannot be removed");
        users.delete(u);
    }

    @Transactional
    public void setPassword(UUID id, String newPassword) {
        User u = get(id);
        u.setPasswordHash(encoder.encode(newPassword));
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        refreshTokens.revokeAllForUser(u.getId());
    }
}
