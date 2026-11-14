package backend.controller;

import backend.entity.User;
import backend.repository.UserRepository;
import backend.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public record ChangePasswordRequest(
            @NotBlank(message = "Current password is required") String currentPassword,
            @NotBlank(message = "New password is required")
            @Size(min = 8, message = "Password must be at least 8 characters") String newPassword) {}

    @PostMapping("/change-password")
    public Map<String, String> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                              Authentication auth, HttpServletRequest http) {
        User u = users.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!encoder.matches(req.currentPassword(), u.getPassword())) {
            record(u.getEmail(), "PASSWORD_CHANGE_FAILED", "Wrong current password", http);
            // 400, not 401: the web app logs the user out on 401
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New password must be different from the current one");
        }
        u.setPassword(encoder.encode(req.newPassword()));
        users.save(u);
        record(u.getEmail(), "PASSWORD_CHANGED", "User changed their own password", http);
        return Map.of("message", "Password changed");
    }

    private void record(String actor, String action, String details, HttpServletRequest http) {
        try {
            audit.log(actor, action, details, http.getRemoteAddr());
        } catch (Exception ignored) {
            // an audit failure must never block the request
        }
    }
}