package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.auth.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final JwtService jwtService;

    public MemberServiceImpl(MemberRepository memberRepository, JwtService jwtService) {
        this.memberRepository = memberRepository;
        this.jwtService = jwtService;
    }

    @Override
    public List<MemberResponse> listAll() {
        return memberRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public MemberResponse getById(UUID id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new MemberNotFoundException(id));
        return toResponse(member);
    }

    @Override
    @Transactional
    public void delete(UUID targetMemberId, JwtClaims caller) {
        if (targetMemberId.equals(caller.memberId())) {
            throw new SelfModificationException("delete");
        }
        // Verify member exists in this tenant (Hibernate filter scopes by tenant — cross-tenant IDs return empty)
        memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new MemberNotFoundException(targetMemberId));

        // revokeAllRefreshTokens uses a raw UPDATE by member_id; tenant safety guaranteed by the
        // findById check above which already confirmed the member belongs to the caller's tenant.
        jwtService.revokeAllRefreshTokens(targetMemberId);
        memberRepository.deleteById(targetMemberId);
    }

    @Override
    @Transactional
    public MemberResponse changeRole(UUID targetMemberId, UpdateRoleRequest request, JwtClaims caller) {
        if (targetMemberId.equals(caller.memberId())) {
            throw new SelfModificationException("change role");
        }
        if (request.role() == Role.OWNER) {
            throw new InvalidRolePromotionException();
        }
        Member member = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new MemberNotFoundException(targetMemberId));
        member.setRole(request.role());
        Member saved = memberRepository.save(member);
        return toResponse(saved);
    }

    private MemberResponse toResponse(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getRole().name(),
                member.getCreatedAt()
        );
    }
}
