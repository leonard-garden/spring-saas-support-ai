package com.leonardtrinh.supportsaas.invitation;

import com.leonardtrinh.supportsaas.auth.AuthResponse;
import com.leonardtrinh.supportsaas.auth.BusinessNotFoundException;
import com.leonardtrinh.supportsaas.auth.JwtClaims;
import com.leonardtrinh.supportsaas.auth.JwtService;
import com.leonardtrinh.supportsaas.auth.PlanMisconfiguredException;
import com.leonardtrinh.supportsaas.billing.Plan;
import com.leonardtrinh.supportsaas.billing.PlanRepository;
import com.leonardtrinh.supportsaas.billing.QuotaExceededException;
import com.leonardtrinh.supportsaas.billing.Subscription;
import com.leonardtrinh.supportsaas.billing.SubscriptionRepository;
import com.leonardtrinh.supportsaas.email.AsyncEmailSender;
import com.leonardtrinh.supportsaas.member.InvalidRolePromotionException;
import com.leonardtrinh.supportsaas.member.InvitationExpiredException;
import com.leonardtrinh.supportsaas.member.Member;
import com.leonardtrinh.supportsaas.member.MemberRepository;
import com.leonardtrinh.supportsaas.member.Role;
import com.leonardtrinh.supportsaas.tenant.BusinessRepository;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InvitationServiceImpl implements InvitationService {

    private final InvitationRepository invitationRepository;
    private final MemberRepository memberRepository;
    private final BusinessRepository businessRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final AsyncEmailSender asyncEmailSender;

    public InvitationServiceImpl(
            InvitationRepository invitationRepository,
            MemberRepository memberRepository,
            BusinessRepository businessRepository,
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            TokenGenerator tokenGenerator,
            AsyncEmailSender asyncEmailSender) {
        this.invitationRepository = invitationRepository;
        this.memberRepository = memberRepository;
        this.businessRepository = businessRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.asyncEmailSender = asyncEmailSender;
    }

    @Override
    @Transactional
    public InvitationResponse invite(InviteRequest request, JwtClaims caller) {
        // Caller must be ADMIN or OWNER
        Role callerRole = Role.valueOf(caller.role());
        if (callerRole != Role.ADMIN && callerRole != Role.OWNER) {
            throw new AccessDeniedException("Only ADMIN or OWNER can invite members");
        }

        // Cannot invite as OWNER
        if (request.role() == Role.OWNER) {
            throw new InvalidRolePromotionException();
        }

        UUID tenantId = caller.tenantId();

        // Check if email already a member in this tenant
        if (memberRepository.existsByEmailInTenant(tenantId, request.email())) {
            throw new MemberAlreadyExistsException(request.email());
        }

        // Check if pending invitation already exists
        if (invitationRepository.existsPendingByBusinessIdAndEmail(tenantId, request.email())) {
            throw new MemberAlreadyExistsException(request.email());
        }

        // Quota check — use active subscription plan (trial/active), not business.planId
        Plan plan = resolveActivePlan(tenantId);
        long memberCount = memberRepository.countByBusinessId(tenantId);
        if (plan.getMaxMembers() > 0 && memberCount >= plan.getMaxMembers()) {
            throw new QuotaExceededException("members", plan.getMaxMembers(), memberCount);
        }

        // Generate token
        String rawToken = tokenGenerator.generateRawToken();
        String tokenHash = tokenGenerator.hash(rawToken);

        // Create invitation
        Invitation invitation = new Invitation();
        invitation.setBusinessId(tenantId);
        invitation.setEmail(request.email());
        invitation.setRole(request.role());
        invitation.setTokenHash(tokenHash);
        invitation.setExpiresAt(Instant.now().plus(72, ChronoUnit.HOURS));
        invitation.setInvitedBy(caller.memberId());
        Invitation saved = invitationRepository.save(invitation);

        // Send email async
        asyncEmailSender.sendInvitationEmail(request.email(), rawToken);

        return new InvitationResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),
                saved.getExpiresAt(),
                saved.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public AuthResponse accept(AcceptInvitationRequest request) {
        String tokenHash = tokenGenerator.hash(request.token());

        Invitation invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvitationExpiredException(request.token()));

        // Validate: not expired and not already accepted
        if (invitation.getAcceptedAt() != null || invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new InvitationExpiredException(request.token());
        }

        UUID businessId = invitation.getBusinessId();
        TenantContext.setTenantId(businessId);
        try {
            // Pessimistic lock to prevent concurrent-accept race on quota
            businessRepository.findByIdWithLock(businessId)
                    .orElseThrow(() -> new BusinessNotFoundException(businessId));

            // Re-validate quota at accept time using active subscription plan
            Plan plan = resolveActivePlan(businessId);
            long memberCount = memberRepository.countByBusinessId(businessId);
            if (plan.getMaxMembers() > 0 && memberCount >= plan.getMaxMembers()) {
                throw new QuotaExceededException("members", plan.getMaxMembers(), memberCount);
            }

            // Create member
            Member member = new Member();
            member.setBusinessId(businessId);
            member.setEmail(invitation.getEmail());
            member.setPasswordHash(passwordEncoder.encode(request.password()));
            member.setRole(invitation.getRole());
            member.setEmailVerified(true);
            member = memberRepository.save(member);

            // Mark invitation accepted
            invitation.setAcceptedAt(Instant.now());
            invitationRepository.save(invitation);

            // Generate tokens
            String accessToken = jwtService.generateAccessToken(member);
            String refreshToken = jwtService.generateRefreshToken(member);

            return new AuthResponse(accessToken, refreshToken, businessId, member.getId());
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InvitationResponse> listPending(Pageable pageable, JwtClaims caller) {
        Role callerRole = Role.valueOf(caller.role());
        if (callerRole != Role.ADMIN && callerRole != Role.OWNER) {
            throw new AccessDeniedException("Only ADMIN or OWNER can list invitations");
        }
        return invitationRepository.findAllPending(Instant.now(), pageable)
                .map(i -> new InvitationResponse(
                        i.getId(), i.getEmail(), i.getRole(), i.getExpiresAt(), i.getCreatedAt()));
    }

    @Override
    @Transactional
    public InvitationResponse resend(UUID id, JwtClaims caller) {
        Role callerRole = Role.valueOf(caller.role());
        if (callerRole != Role.ADMIN && callerRole != Role.OWNER) {
            throw new AccessDeniedException("Only ADMIN or OWNER can resend invitations");
        }

        // findById uses the Hibernate tenant filter — returns empty if invitation belongs to another tenant
        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> new InvitationNotFoundException(id));

        if (invitation.getAcceptedAt() != null) {
            throw new InvitationNotFoundException(id); // already accepted — treat as not found
        }

        // Generate new token and reset expiry
        String rawToken = tokenGenerator.generateRawToken();
        invitation.setTokenHash(tokenGenerator.hash(rawToken));
        invitation.setExpiresAt(Instant.now().plus(72, ChronoUnit.HOURS));
        Invitation saved = invitationRepository.save(invitation);

        asyncEmailSender.sendInvitationEmail(saved.getEmail(), rawToken);

        return new InvitationResponse(
                saved.getId(), saved.getEmail(), saved.getRole(),
                saved.getExpiresAt(), saved.getCreatedAt());
    }

    /**
     * Resolves the effective plan for quota checks by looking up the active or trialing
     * subscription rather than business.planId (which always points to the free plan).
     */
    private Plan resolveActivePlan(UUID businessId) {
        return subscriptionRepository.findActiveByBusinessId(businessId)
                .map(Subscription::getPlanId)
                .flatMap(planRepository::findById)
                .orElseGet(() -> {
                    // Fallback: use business.planId (free plan)
                    return businessRepository.findById(businessId)
                            .flatMap(b -> planRepository.findById(b.getPlanId()))
                            .orElseThrow(() -> new PlanMisconfiguredException("business=" + businessId));
                });
    }
}
