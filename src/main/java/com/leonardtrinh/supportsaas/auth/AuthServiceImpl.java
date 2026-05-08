package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.billing.Plan;
import com.leonardtrinh.supportsaas.billing.PlanRepository;
import com.leonardtrinh.supportsaas.billing.Subscription;
import com.leonardtrinh.supportsaas.billing.SubscriptionRepository;
import com.leonardtrinh.supportsaas.billing.SubscriptionStatus;
import com.leonardtrinh.supportsaas.member.Member;
import com.leonardtrinh.supportsaas.member.MemberRepository;
import com.leonardtrinh.supportsaas.member.Role;
import com.leonardtrinh.supportsaas.tenant.Business;
import com.leonardtrinh.supportsaas.tenant.BusinessRepository;
import com.leonardtrinh.supportsaas.email.AsyncEmailSender;
import com.leonardtrinh.supportsaas.tenant.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private final MemberRepository memberRepository;
    private final BusinessRepository businessRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final AsyncEmailSender emailSender;

    public AuthServiceImpl(
            MemberRepository memberRepository,
            BusinessRepository businessRepository,
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            AuditLogger auditLogger,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            AsyncEmailSender emailSender) {
        this.memberRepository = memberRepository;
        this.businessRepository = businessRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.emailSender = emailSender;
    }

    @Override
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException();
        }

        Plan freePlan = planRepository.findBySlug("free")
                .orElseThrow(() -> new PlanMisconfiguredException("free"));
        Plan proPlan = planRepository.findBySlug("pro")
                .orElseThrow(() -> new PlanMisconfiguredException("pro"));

        Business business = new Business();
        business.setName(request.businessName());
        business.setSlug(slugify(request.businessName()));
        business.setPlanId(freePlan.getId());
        business = businessRepository.save(business);

        Member member = new Member();
        member.setBusinessId(business.getId());
        member.setEmail(request.email());
        member.setPasswordHash(passwordEncoder.encode(request.password()));
        member.setRole(Role.OWNER);
        member.setEmailVerified(false);
        member = memberRepository.save(member);

        Subscription subscription = new Subscription();
        subscription.setBusinessId(business.getId());
        subscription.setPlanId(proPlan.getId());
        subscription.setStatus(SubscriptionStatus.TRIALING);
        subscription.setTrialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);

        byte[] verifyBytes = new byte[32];
        new SecureRandom().nextBytes(verifyBytes);
        String verifyPlaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(verifyBytes);
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setMember(member);
        verificationToken.setTokenHash(TokenHasher.sha256Hex(verifyPlaintext));
        verificationToken.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        emailVerificationTokenRepository.save(verificationToken);

        String emailToSend = member.getEmail();
        String tokenToSend = verifyPlaintext;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emailSender.sendEmailVerificationAsync(emailToSend, tokenToSend);
            }
        });

        TenantContext.setTenantId(business.getId());
        try {
            String accessToken = jwtService.generateAccessToken(member);
            String refreshToken = jwtService.generateRefreshToken(member);
            return new AuthResponse(accessToken, refreshToken, business.getId(), member.getId());
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        Business business = businessRepository.findById(member.getBusinessId())
                .orElseThrow(() -> new BusinessNotFoundException(member.getBusinessId()));

        if (business.getSuspendedAt() != null) {
            throw new BusinessSuspendedException();
        }

        TenantContext.setTenantId(member.getBusinessId());
        try {
            String accessToken = jwtService.generateAccessToken(member);
            String refreshToken = jwtService.generateRefreshToken(member);
            auditLogger.logLoginAsync(member.getId(), member.getBusinessId());
            return new AuthResponse(accessToken, refreshToken, business.getId(), member.getId());
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        Member member = jwtService.validateRefreshToken(request.refreshToken());

        TenantContext.setTenantId(member.getBusinessId());
        try {
            String newRefreshToken = jwtService.rotateRefreshToken(request.refreshToken(), member);
            String newAccessToken = jwtService.generateAccessToken(member);
            return new AuthResponse(newAccessToken, newRefreshToken, member.getBusinessId(), member.getId());
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        jwtService.revokeRefreshToken(refreshToken);
    }

    @Override
    public MeResponse me(JwtClaims claims) {
        Business business = businessRepository.findById(claims.tenantId())
                .orElseThrow(() -> new BusinessNotFoundException(claims.tenantId()));
        Member member = memberRepository.findByEmail(claims.email())
                .orElseThrow(() -> new BusinessNotFoundException(claims.tenantId()));

        return new MeResponse(
                claims.memberId(),
                claims.email(),
                claims.role(),
                claims.tenantId(),
                business.getName(),
                member.isEmailVerified()
        );
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        memberRepository.findByEmail(request.email()).ifPresent(member -> {
            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            String plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

            passwordResetTokenRepository.invalidateAllActiveByMemberId(member.getId(), Instant.now());

            PasswordResetToken token = new PasswordResetToken();
            token.setMember(member);
            token.setTokenHash(TokenHasher.sha256Hex(plaintext));
            token.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
            passwordResetTokenRepository.save(token);

            String emailToSend = member.getEmail();
            String tokenToSend = plaintext;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emailSender.sendPasswordResetAsync(emailToSend, tokenToSend);
                }
            });
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String hash = TokenHasher.sha256Hex(request.token());
        PasswordResetToken token = passwordResetTokenRepository
                .findActiveByTokenHash(hash, Instant.now())
                .orElseThrow(() -> new InvalidResetTokenException("Reset token is invalid or expired"));

        Member member = token.getMember();
        member.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        memberRepository.save(member);

        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);
        passwordResetTokenRepository.invalidateAllActiveByMemberId(member.getId(), Instant.now());

        jwtService.revokeAllRefreshTokens(member.getId());
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        String hash = TokenHasher.sha256Hex(request.token());
        EmailVerificationToken token = emailVerificationTokenRepository
                .findActiveByTokenHash(hash, Instant.now())
                .orElseThrow(() -> new InvalidVerificationTokenException("Verification token is invalid or expired"));

        Member member = token.getMember();
        member.setEmailVerified(true);
        memberRepository.save(member);

        token.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(token);
        emailVerificationTokenRepository.invalidateAllActiveByMemberId(member.getId(), Instant.now());
    }

    private String slugify(String input) {
        String lower = input.toLowerCase();
        String slug = NON_ALPHANUMERIC.matcher(lower).replaceAll("-");
        // strip leading/trailing dashes
        slug = slug.replaceAll("^-+|-+$", "");
        return slug;
    }
}
