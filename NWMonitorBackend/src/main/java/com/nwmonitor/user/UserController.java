package com.nwmonitor.user;

import com.nwmonitor.user.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Both roles can read; only ROOT can add, change or remove admins. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) { this.service = service; }

    @GetMapping
    public List<UserView> list() { return service.list().stream().map(UserView::of).toList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ROOT')")
    public UserView create(@Valid @RequestBody CreateUserRequest r) { return UserView.of(service.createAdmin(r)); }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROOT')")
    public UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest r) {
        return UserView.of(service.update(id, r));
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ROOT')")
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest r) {
        service.setPassword(id, r.newPassword());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ROOT')")
    public void delete(@PathVariable UUID id) { service.delete(id); }
}
