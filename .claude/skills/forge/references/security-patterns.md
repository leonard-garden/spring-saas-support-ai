# Security Patterns

Spring Security config, endpoint authorization. Load khi task thêm endpoints hoặc thay đổi auth rules.

---

## Current State

`SecurityConfig` hiện là placeholder — `anyRequest().permitAll()`.
Sẽ được cập nhật khi implement auth endpoints (#5).

---

## Adding Endpoint Rules

Khi thêm protected endpoints, update `SecurityConfig.securityFilterChain`:

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
- Public endpoints phải explicit `permitAll()`
- Admin paths: `/api/v1/admin/**` → `hasRole("SUPER_ADMIN")`
- Default: `anyRequest().authenticated()`
- `JwtAuthFilter` được inject và add trước `UsernamePasswordAuthenticationFilter`

---

## JwtAuthFilter — Đã implement

`JwtAuthFilter` (OncePerRequestFilter):
1. Extract `Authorization: Bearer <token>` header
2. `jwtService.validateAccessToken(token)` → `JwtClaims`
3. `TenantContext.setTenantId(claims.tenantId())`
4. Set `SecurityContextHolder` authentication
5. `finally`: `TenantContext.clear()`

Controller nhận được auth context qua `SecurityContextHolder` hoặc `@AuthenticationPrincipal`.

---

## Getting Current User in Controller

```java
// Option 1: @AuthenticationPrincipal (nếu JwtClaims implement UserDetails)
@GetMapping("/me")
public ApiResponse<MemberResponse> getMe(@AuthenticationPrincipal JwtClaims claims) {
    return ApiResponse.ok(memberService.getById(claims.memberId()));
}

// Option 2: SecurityContextHolder (bất kỳ đâu)
JwtClaims claims = (JwtClaims) SecurityContextHolder.getContext()
    .getAuthentication().getPrincipal();
```

---

## Role-based Authorization

```java
// Method level (sau khi bật @EnableMethodSecurity)
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/members/{id}")
public void removeMember(@PathVariable UUID id) { ... }

// Programmatic check trong service
if (claims.role().equals("OWNER") || claims.role().equals("ADMIN")) {
    // allowed
}
```

Available roles (từ `Role` enum): `OWNER`, `ADMIN`, `MEMBER`

---

## Password Hashing

```java
// BCryptPasswordEncoder — @Bean trong SecurityConfig
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

// Usage trong service
String hash = passwordEncoder.encode(rawPassword);
boolean matches = passwordEncoder.matches(rawPassword, storedHash);
```

---

## CORS (nếu cần cho widget)

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
