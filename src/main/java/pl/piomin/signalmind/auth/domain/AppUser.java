package pl.piomin.signalmind.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
public class AppUser implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── SM-34: Onboarding fields ─────────────────────────────────────────────

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "disclaimer_accepted", nullable = false)
    private boolean disclaimerAccepted = false;

    @Column(name = "disclaimer_accepted_at")
    private Instant disclaimerAcceptedAt;

    @Column(name = "telegram_chat_id", length = 50)
    private String telegramChatId;

    @Column(name = "telegram_verified", nullable = false)
    private boolean telegramVerified = false;

    @Column(name = "onboarding_step", nullable = false)
    private int onboardingStep = 0;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verification_token", length = 255)
    private String emailVerificationToken;

    @Column(name = "email_verification_sent_at")
    private Instant emailVerificationSentAt;

    // ── Constructors ─────────────────────────────────────────────────────────

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = Instant.now();
    }

    // ── SM-34: Onboarding helper methods ─────────────────────────────────────

    /**
     * Marks email as verified and advances onboarding to at least step 1.
     * Clears the verification token so it cannot be reused.
     */
    public void verifyEmail() {
        this.emailVerified = true;
        this.emailVerificationToken = null;
        this.onboardingStep = Math.max(this.onboardingStep, 1);
    }

    /**
     * Records disclaimer acceptance and advances onboarding to at least step 2.
     *
     * @param at the timestamp at which the user accepted the disclaimer
     */
    public void acceptDisclaimer(Instant at) {
        this.disclaimerAccepted = true;
        this.disclaimerAcceptedAt = at;
        this.onboardingStep = Math.max(this.onboardingStep, 2);
    }

    /**
     * Connects the user's Telegram chat and advances onboarding to at least step 3.
     *
     * @param chatId the Telegram chat ID verified by a test message
     */
    public void connectTelegram(String chatId) {
        this.telegramChatId = chatId;
        this.telegramVerified = true;
        this.onboardingStep = Math.max(this.onboardingStep, 3);
    }

    /**
     * Returns {@code true} when all three onboarding gates are cleared:
     * email verified, disclaimer accepted, and Telegram connected.
     */
    public boolean isOnboardingComplete() {
        return emailVerified && disclaimerAccepted && telegramVerified;
    }

    // ── UserDetails implementation ──────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isDisclaimerAccepted() {
        return disclaimerAccepted;
    }

    public Instant getDisclaimerAcceptedAt() {
        return disclaimerAcceptedAt;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public boolean isTelegramVerified() {
        return telegramVerified;
    }

    public int getOnboardingStep() {
        return onboardingStep;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getEmailVerificationToken() {
        return emailVerificationToken;
    }

    public Instant getEmailVerificationSentAt() {
        return emailVerificationSentAt;
    }

    // ── Setters ─────────────────────────────────────────────────────────────

    public void setName(String name) {
        this.name = name;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setDisclaimerAccepted(boolean disclaimerAccepted) {
        this.disclaimerAccepted = disclaimerAccepted;
    }

    public void setDisclaimerAcceptedAt(Instant disclaimerAcceptedAt) {
        this.disclaimerAcceptedAt = disclaimerAcceptedAt;
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public void setTelegramVerified(boolean telegramVerified) {
        this.telegramVerified = telegramVerified;
    }

    public void setOnboardingStep(int onboardingStep) {
        this.onboardingStep = onboardingStep;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public void setEmailVerificationToken(String emailVerificationToken) {
        this.emailVerificationToken = emailVerificationToken;
    }

    public void setEmailVerificationSentAt(Instant emailVerificationSentAt) {
        this.emailVerificationSentAt = emailVerificationSentAt;
    }
}
