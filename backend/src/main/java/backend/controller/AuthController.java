package backend.controller;

import backend.dto.LoginRequest;
import backend.dto.UserResponse;
import backend.entity.User;
import backend.repository.UserRepository;
import backend.security.JwtService;
import backend.service.AuditService;
import backend.service.CaptchaService;
import backend.service.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final int CAPTCHA_AFTER = 3;
    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CaptchaService captcha;
    private final LoginRateLimiter limiter;
    private final AuditService audit;

    @GetMapping("/captcha")
    public Map<String, String> newCaptcha() {
        return captcha.generate();
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        String email = req.email().trim();
        if (!limiter.allow(clientIp(http))) {
            record(email, "LOGIN_RATE_LIMITED", "Too many requests from this IP", http);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Please wait a minute.");
        }
        User user = users.findByEmailIgnoreCase(email)
                .filter(User::isActive).orElse(null);
        if (user == null) {
            record(email, "LOGIN_FAILED", "Unknown or inactive account", http);
            throw invalid();
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            record(user.getEmail(), "LOGIN_BLOCKED", "Account is locked", http);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Account temporarily locked. Try again later.");
        }
        if (user.getFailedAttempts() >= CAPTCHA_AFTER) {
            if (req.captchaId() == null || req.captchaAnswer() == null) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Captcha required");
            }
            if (!captcha.verify(req.captchaId(), req.captchaAnswer())) {
                record(user.getEmail(), "CAPTCHA_FAILED", "Incorrect captcha answer", http);
                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Captcha incorrect");
            }
        }
        if (!encoder.matches(req.password(), user.getPassword())) {
            int attempts = user.getFailedAttempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                attempts = 0;
                record(user.getEmail(), "ACCOUNT_LOCKED",
                        "Locked for " + LOCK_MINUTES + " minutes after " + MAX_ATTEMPTS + " failed attempts", http);
            }
            user.setFailedAttempts(attempts);
            users.save(user);
            record(user.getEmail(), "LOGIN_FAILED", "Wrong password", http);
            throw invalid();
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
        record(user.getEmail(), "LOGIN_SUCCESS", "Role " + user.getRole().name(), http);

        return Map.of("token", jwt.generate(user), "tokenType", "Bearer",
                "name", user.getName(), "email", user.getEmail(), "role", user.getRole().name());
    }

    @GetMapping("/me")
    public UserResponse me(Authentication auth) {
        return users.findByEmail(auth.getName()).map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @PostMapping("/logout")
    public Map<String, String> logout(Authentication auth, HttpServletRequest http) {
        if (auth != null) record(auth.getName(), "LOGOUT", null, http);
        return Map.of("message", "Logged out");
    }

    private void record(String actor, String action, String details, HttpServletRequest http) {
        try {
            String a = actor == null ? "anonymous" : (actor.length() > 100 ? actor.substring(0, 100) : actor);
            audit.log(a, action, details, clientIp(http));
        } catch (Exception ignored) {
            // an audit failure must never block a login
        }
    }

    private String clientIp(HttpServletRequest r) {
        String xff = r.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : r.getRemoteAddr();
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}