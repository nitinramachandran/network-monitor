package com.nwmonitor.user;

import com.nwmonitor.common.ApiException;
import com.nwmonitor.user.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** First-run setup: creates the one-and-only root user. Unavailable once a root exists. */
@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {
    private final UserService users;

    public SetupController(UserService users) { this.users = users; }

    @GetMapping("/status")
    public Map<String, Boolean> status() { return Map.of("setupRequired", !users.rootExists()); }

    @PostMapping("/root")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createRoot(@Valid @RequestBody CreateUserRequest r) {
        if (users.rootExists()) throw ApiException.conflict("Setup already completed");
        return UserView.of(users.createRoot(r.username(), r.displayName(), r.email(), r.password()));
    }
}
