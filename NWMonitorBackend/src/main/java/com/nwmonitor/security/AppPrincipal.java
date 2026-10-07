package com.nwmonitor.security;

import com.nwmonitor.user.Role;

import java.util.UUID;

public record AppPrincipal(UUID userId, String username, Role role) {}
