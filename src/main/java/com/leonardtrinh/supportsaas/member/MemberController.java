package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import static com.leonardtrinh.supportsaas.member.Role.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<List<MemberResponse>> listAll() {
        requireAdminOrOwner();
        return ApiResponse.ok(memberService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<MemberResponse> getById(@PathVariable UUID id) {
        requireAdminOrOwner();
        return ApiResponse.ok(memberService.getById(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        JwtClaims caller = caller();
        requireOwner(caller);
        memberService.delete(id, caller);
    }

    @PatchMapping("/{id}/role")
    public ApiResponse<MemberResponse> changeRole(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateRoleRequest request) {
        JwtClaims caller = caller();
        requireOwner(caller);
        return ApiResponse.ok(memberService.changeRole(id, request, caller));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private JwtClaims caller() {
        return (JwtClaims) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private void requireAdminOrOwner() {
        Role role = Role.valueOf(caller().role());
        if (role != ADMIN && role != OWNER) {
            throw new AccessDeniedException("ADMIN or OWNER role required");
        }
    }

    private void requireOwner(JwtClaims caller) {
        if (Role.valueOf(caller.role()) != OWNER) {
            throw new AccessDeniedException("OWNER role required");
        }
    }
}
