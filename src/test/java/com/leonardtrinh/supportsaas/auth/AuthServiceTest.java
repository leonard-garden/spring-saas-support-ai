package com.leonardtrinh.supportsaas.auth;

import com.leonardtrinh.supportsaas.billing.Plan;
import com.leonardtrinh.supportsaas.billing.PlanRepository;
import com.leonardtrinh.supportsaas.billing.Subscription;
import com.leonardtrinh.supportsaas.billing.SubscriptionService;
import com.leonardtrinh.supportsaas.billing.SubscriptionStatus;
import com.leonardtrinh.supportsaas.email.AsyncEmailSender;
import com.leonardtrinh.supportsaas.knowledgebase.KnowledgeBaseService;
import com.leonardtrinh.supportsaas.member.Member;
import com.leonardtrinh.supportsaas.member.MemberRepository;
import com.leonardtrinh.supportsaas.tenant.Business;
import com.leonardtrinh.supportsaas.tenant.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private AsyncEmailSender emailSender;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                memberRepository,
                businessRepository,
                planRepository,
                subscriptionService,
                jwtService,
                passwordEncoder,
                auditLogger,
                passwordResetTokenRepository,
                emailVerificationTokenRepository,
                emailSender,
                knowledgeBaseService);

        // Spring transaction synchronization is not active in unit tests — initialise it
        TransactionSynchronizationManager.initSynchronization();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Plan freePlan() {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(plan, "slug", "free");
        return plan;
    }

    private Business savedBusiness(UUID id) {
        Business business = new Business();
        ReflectionTestUtils.setField(business, "id", id);
        business.setName("Acme Corp");
        business.setSlug("acme-corp");
        return business;
    }

    private Member savedMember(UUID memberId, UUID businessId) {
        Member member = new Member();
        ReflectionTestUtils.setField(member, "id", memberId);
        member.setBusinessId(businessId);
        member.setEmail("user@example.com");
        return member;
    }

    private Subscription trialSubscription(UUID businessId) {
        Subscription sub = new Subscription();
        sub.setBusinessId(businessId);
        sub.setStatus(SubscriptionStatus.TRIALING);
        sub.setTrialEndsAt(Instant.now().plus(14, ChronoUnit.DAYS));
        return sub;
    }

    // ---------------------------------------------------------------
    // signup — trial creation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("signup: calls subscriptionService.createTrial() with the new business id")
    void signup_callsCreateTrialWithBusinessId() {
        UUID businessId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        Plan free = freePlan();
        Business business = savedBusiness(businessId);
        Member member = savedMember(memberId, businessId);

        when(memberRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(planRepository.findBySlug("free")).thenReturn(Optional.of(free));
        when(businessRepository.save(any(Business.class))).thenReturn(business);
        when(memberRepository.save(any(Member.class))).thenReturn(member);
        when(subscriptionService.createTrial(businessId)).thenReturn(trialSubscription(businessId));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(emailVerificationTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(member)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(member)).thenReturn("refresh-token");

        SignupRequest request = new SignupRequest("Acme Corp", "user@example.com", "password");
        AuthResponse response = authService.signup(request);

        verify(subscriptionService).createTrial(businessId);
        assertThat(response.businessId()).isEqualTo(businessId);
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("signup: does not call subscriptionService when email already exists")
    void signup_duplicateEmail_neverCallsCreateTrial() {
        when(memberRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("Acme Corp", "user@example.com", "password")))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(subscriptionService, never()).createTrial(any());
    }

    @Test
    @DisplayName("signup: does not call subscriptionService when free plan is missing")
    void signup_missingFreePlan_neverCallsCreateTrial() {
        when(memberRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(planRepository.findBySlug("free")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("Acme Corp", "user@example.com", "password")))
                .isInstanceOf(PlanMisconfiguredException.class);

        verify(subscriptionService, never()).createTrial(any());
    }
}
