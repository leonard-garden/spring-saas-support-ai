package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.common.ApiResponse;
import com.leonardtrinh.supportsaas.common.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import static com.leonardtrinh.supportsaas.member.Role.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Members", description = "Member management — list, remove, and change roles")
@SecurityRequirement(name = "Bearer")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @Operation(summary = "List all members in the tenant (paginated)")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member page returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role")
    })
    public ApiResponse<PagedResponse<MemberResponse>> listAll(
            @PageableDefault(size = 10) Pageable pageable) {
        requireAdminOrOwner();
        return ApiResponse.ok(PagedResponse.from(memberService.listAll(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get member by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found")
    })
    public ApiResponse<MemberResponse> getById(@PathVariable UUID id) {
        requireAdminOrOwner();
        return ApiResponse.ok(memberService.getById(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member from the tenant")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Member removed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found")
    })
    public void delete(@PathVariable UUID id) {
        JwtClaims caller = caller();
        requireOwner(caller);
        memberService.delete(id, caller);
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change a member's role")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient role"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found")
    })
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
