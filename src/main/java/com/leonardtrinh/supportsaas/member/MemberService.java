package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.auth.JwtClaims;

import java.util.List;
import java.util.UUID;

public interface MemberService {

    List<MemberResponse> listAll();

    MemberResponse getById(UUID id);

    void delete(UUID targetMemberId, JwtClaims caller);

    MemberResponse changeRole(UUID targetMemberId, UpdateRoleRequest request, JwtClaims caller);
}
