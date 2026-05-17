package com.leonardtrinh.supportsaas.member;

import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.auth.JwtService;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Page<MemberResponse> listAll(Pageable pageable) {
        return memberRepository.findAll(pageable)
                .map(this::toResponse);
    }

    @Override
    public MemberResponse getById(UUID id) {
        // em.find() bypasses Hibernate filters — explicit tenant check required
        Member member = memberRepository.findById(id)
                .filter(m -> m.getBusinessId().equals(TenantContext.getTenantId()))
                .orElseThrow(() -> new MemberNotFoundException(id));
        return toResponse(member);
    }

    @Override
    @Transactional
    public void delete(UUID targetMemberId, JwtClaims caller) {
        if (targetMemberId.equals(caller.memberId())) {
            throw new SelfModificationException("delete");
        }
        // em.find() bypasses Hibernate filters — explicit tenant check required
        Member member = memberRepository.findById(targetMemberId)
                .filter(m -> m.getBusinessId().equals(TenantContext.getTenantId()))
                .orElseThrow(() -> new MemberNotFoundException(targetMemberId));

        // revokeAllRefreshTokens uses a raw UPDATE by member_id; tenant safety guaranteed by
        // the businessId check above confirming the member belongs to the caller's tenant.
        jwtService.revokeAllRefreshTokens(member.getId());
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
        // em.find() bypasses Hibernate filters — explicit tenant check required
        Member member = memberRepository.findById(targetMemberId)
                .filter(m -> m.getBusinessId().equals(TenantContext.getTenantId()))
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
