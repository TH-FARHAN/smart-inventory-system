package backend.controller;

import backend.entity.User;
import backend.enums.Role;
import backend.repository.UserRepository;
import backend.service.AuditService;
import backend.service.EmailService;
import backend.service.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

/** Forgot password with a real email code. Administrator accounts only. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordResetController {
    private static final int OTP_MINUTES = 10;
    private static final int MAX_TRIES = 5;
    private static final int RESEND_SECONDS = 60;
    private static final String SENT = "If this is an administrator account, a code has been sent to its email.";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final EmailService mailer;
    private final LoginRateLimiter limiter;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public record ForgotRequest(
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email) {}

    public record ResetRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Enter the 6-digit code") @Size(min = 6, max = 6, message = "The code has 6 digits") String otp,
            @NotBlank(message = "Enter a new password")
            @Size(min = 8, max = 100, message = "Password must be at least 8 characters") String newPassword) {}

    @PostMapping("/forgot-password")
    public Map<String, String> forgot(@Valid @RequestBody ForgotRequest req, HttpServletRequest http) {
        String ip = clientIp(http);
        if (!limiter.allow("reset:" + ip))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait a minute.");
        if (!mailer.isConfigured())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Password reset by email is not set up on this server.");

        String address = req.email().trim();
        User u = users.findByEmailIgnoreCase(address)
                .filter(User::isActive)
                .filter(x -> x.getRole() == Role.ADMIN)
                .orElse(null);
        if (u == null) {
            record(address, "PASSWORD_RESET_DENIED", "Not an active administrator account", ip);
            return Map.of("message", SENT);
        }
        LocalDateTime now = LocalDateTime.now();
        if (u.getResetOtpSentAt() != null && u.getResetOtpSentAt().isAfter(now.minusSeconds(RESEND_SECONDS))) {
            return Map.of("message", SENT); // too soon: send nothing, say nothing
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        u.setResetOtpHash(encoder.encode(code));
        u.setResetOtpExpiresAt(now.plusMinutes(OTP_MINUTES));
        u.setResetOtpSentAt(now);
        u.setResetOtpAttempts(0);
        users.save(u);

        boolean sent = mailer.sendResetCode(u.getEmail(), u.getName(), code);
        if (!sent) {
            u.setResetOtpSentAt(null); // let them try again straight away
            users.save(u);
        }
        record(u.getEmail(), sent ? "PASSWORD_RESET_REQUESTED" : "PASSWORD_RESET_EMAIL_FAILED", null, ip);
        return Map.of("message", SENT);
    }

    // not @Transactional on purpose: a wrong try must stay saved even though we throw an error
    @PostMapping("/reset-password")
    public Map<String, String> reset(@Valid @RequestBody ResetRequest req, HttpServletRequest http) {
        String ip = clientIp(http);
        if (!limiter.allow("reset:" + ip))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait a minute.");

        String address = req.email().trim();
        User u = users.findByEmailIgnoreCase(address)
                .filter(User::isActive)
                .filter(x -> x.getRole() == Role.ADMIN)
                .orElse(null);
        if (u == null || u.getResetOtpHash() == null || u.getResetOtpExpiresAt() == null
                || u.getResetOtpExpiresAt().isBefore(LocalDateTime.now())) {
            record(address, "PASSWORD_RESET_FAILED", "No valid code", ip);
            throw invalid();
        }
        int tries = u.getResetOtpAttempts() == null ? 0 : u.getResetOtpAttempts();
        if (!encoder.matches(req.otp().trim(), u.getResetOtpHash())) {
            if (tries + 1 >= MAX_TRIES) clearOtp(u);
            else u.setResetOtpAttempts(tries + 1);
            users.save(u);
            record(u.getEmail(), "PASSWORD_RESET_FAILED", "Wrong code", ip);
            throw invalid();
        }
        u.setPassword(encoder.encode(req.newPassword()));
        clearOtp(u);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        users.save(u);
        record(u.getEmail(), "PASSWORD_RESET_SUCCESS", "Password changed with an email code", ip);
        return Map.of("message", "Password changed. You can sign in now.");
    }

    private void clearOtp(User u) {
        u.setResetOtpHash(null);
        u.setResetOtpExpiresAt(null);
        u.setResetOtpAttempts(0);
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired code.");
    }

    private void record(String actor, String action, String details, String ip) {
        try {
            String a = actor == null ? "anonymous" : (actor.length() > 100 ? actor.substring(0, 100) : actor);
            audit.log(a, action, details, ip);
        } catch (Exception ignored) {
            // an audit failure must never block the request
        }
    }

    private String clientIp(HttpServletRequest r) {
        String xff = r.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : r.getRemoteAddr();
    }
}