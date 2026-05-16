package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.auth.JwtClaims;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MemberService {

    Page<MemberResponse> listAll(Pageable pageable);

    MemberResponse getById(UUID id);

    void delete(UUID targetMemberId, JwtClaims caller);

    MemberResponse changeRole(UUID targetMemberId, UpdateRoleRequest request, JwtClaims caller);
}
