package com.leonardtrinh.supportsaas.common;

import com.leonardtrinh.supportsaas.auth.EmailAlreadyExistsException;
import com.leonardtrinh.supportsaas.billing.QuotaExceededException;
import com.leonardtrinh.supportsaas.tenant.TenantNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("AppException subclass maps to correct HTTP status")
    void appException_mapsToCorrectStatus() throws Exception {
        mockMvc.perform(get("/test/email-exists"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("An account with this email already exists"));
    }

    @Test
    @DisplayName("TenantNotFoundException maps to 404")
    void tenantNotFound_maps404() throws Exception {
        mockMvc.perform(get("/test/tenant-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("QuotaExceededException includes metric, limit, current, upgrade_url")
    void quotaExceeded_includesRichProperties() throws Exception {
        mockMvc.perform(get("/test/quota-exceeded"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.metric").value("MESSAGES_SENT"))
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.current").value(100))
                .andExpect(jsonPath("$.upgrade_url").value("/api/v1/billing/plans"));
    }

    @Test
    @DisplayName("AccessDeniedException maps to 403")
    void accessDenied_maps403() throws Exception {
        mockMvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("AuthenticationException maps to 401")
    void authenticationException_maps401() throws Exception {
        mockMvc.perform(get("/test/auth-exception"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Unhandled exception maps to 500 with generic message")
    void unhandledException_maps500_noStackTrace() throws Exception {
        mockMvc.perform(get("/test/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.detail", not(containsString("something broke"))));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException returns field error map")
    void validationException_returnsFieldMap() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").exists());
    }

    // Inner controller for throwing test exceptions
    @RestController
    @RequestMapping("/test")
    static class TestController {
        @GetMapping("/email-exists")
        void emailExists() { throw new EmailAlreadyExistsException(); }

        @GetMapping("/tenant-not-found")
        void tenantNotFound() { throw new TenantNotFoundException(UUID.randomUUID()); }

        @GetMapping("/quota-exceeded")
        void quotaExceeded() { throw new QuotaExceededException("MESSAGES_SENT", 100, 100); }

        @GetMapping("/access-denied")
        void accessDenied() { throw new AccessDeniedException("forbidden"); }

        @GetMapping("/auth-exception")
        void authException() { throw new BadCredentialsException("bad creds"); }

        @GetMapping("/unhandled")
        void unhandled() { throw new RuntimeException("something broke"); }

        @PostMapping("/validate")
        void validate(@RequestBody @jakarta.validation.Valid ValidRequest req) {}

        record ValidRequest(@jakarta.validation.constraints.NotBlank String name) {}
    }
}
