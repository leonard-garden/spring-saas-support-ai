# Security Patterns

Spring Security config, endpoint authorization. Load when the task adds endpoints or changes auth rules.

---

## Current State

`SecurityConfig` is currently a placeholder — `anyRequest().permitAll()`.
It will be updated when auth endpoints are implemented (#5).

---

## Adding Endpoint Rules

When adding protected endpoints, update `SecurityConfig.securityFilterChain`:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter)
        throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth
            // Public endpoints — no token required
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
            // Swagger UI
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
            // Health check
            .requestMatchers("/actuator/health").permitAll()
            // Admin endpoints — SUPER_ADMIN only
            .requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")
            // Everything else — must be authenticated
            .anyRequest().authenticated()
        );

    return http.build();
}
```

**Rules:**
- Public endpoints must explicitly use `permitAll()`
- Admin paths: `/api/v1/admin/**` → `hasRole("SUPER_ADMIN")`
- Default: `anyRequest().authenticated()`
- `JwtAuthFilter` is injected and added before `UsernamePasswordAuthenticationFilter`

---

## JwtAuthFilter — Already implemented

`JwtAuthFilter` (OncePerRequestFilter):
1. Extract `Authorization: Bearer <token>` header
2. `jwtService.validateAccessToken(token)` → `JwtClaims`
3. `TenantContext.setTenantId(claims.tenantId())`
4. Set `SecurityContextHolder` authentication
5. `finally`: `TenantContext.clear()`

The controller receives auth context via `SecurityContextHolder` or `@AuthenticationPrincipal`.

---

## Getting Current User in Controller

```java
// Option 1: @AuthenticationPrincipal (if JwtClaims implements UserDetails)
@GetMapping("/me")
public ApiResponse<MemberResponse> getMe(@AuthenticationPrincipal JwtClaims claims) {
    return ApiResponse.ok(memberService.getById(claims.memberId()));
}

// Option 2: SecurityContextHolder (anywhere)
JwtClaims claims = (JwtClaims) SecurityContextHolder.getContext()
    .getAuthentication().getPrincipal();
```

---

## Role-based Authorization

```java
// Method level (after enabling @EnableMethodSecurity)
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/members/{id}")
public void removeMember(@PathVariable UUID id) { ... }

// Programmatic check in service
if (claims.role().equals("OWNER") || claims.role().equals("ADMIN")) {
    // allowed
}
```

Available roles (from `Role` enum): `OWNER`, `ADMIN`, `MEMBER`

---

## Password Hashing

```java
// BCryptPasswordEncoder — @Bean in SecurityConfig
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

// Usage in service
String hash = passwordEncoder.encode(rawPassword);
boolean matches = passwordEncoder.matches(rawPassword, storedHash);
```

---

## CORS (if needed for widget)

```java
.cors(cors -> cors.configurationSource(request -> {
    var config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    return config;
}))
```
