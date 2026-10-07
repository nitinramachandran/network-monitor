package com.nwmonitor.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nwmonitor.common.NwmProperties;
import com.nwmonitor.ingest.ApiKeyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtService jwt, ApiKeyService apiKeys, ObjectMapper mapper) throws Exception {
        http.csrf(c -> c.disable())
            .cors(c -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/setup/**").permitAll()
                .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/v1/ingest/**").hasRole("COLLECTOR")
                .requestMatchers("/api/v1/**").hasAnyRole("ROOT", "ADMIN")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> problem(mapper, res, HttpStatus.UNAUTHORIZED, "Authentication required"))
                .accessDeniedHandler((req, res, ex) -> problem(mapper, res, HttpStatus.FORBIDDEN, "Not allowed")))
            .addFilterBefore(new JwtAuthFilter(jwt), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new ApiKeyFilter(apiKeys), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void problem(ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse res, HttpStatus s, String detail)
            throws java.io.IOException {
        res.setStatus(s.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), Map.of("status", s.value(), "title", s.getReasonPhrase(), "detail", detail));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(NwmProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(props.security().corsAllowedOrigins().split(",")).map(String::trim).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", c);
        return src;
    }
}
