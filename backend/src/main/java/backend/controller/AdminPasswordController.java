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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPasswordController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public record ResetPasswordRequest(
            @NotBlank(message = "New password is required")
            @Size(min = 8, message = "Password must be at least 8 characters") String newPassword) {}

    @PatchMapping("/{id}/reset-password")
    @Transactional
    public Map<String, String> reset(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req,
                                     Authentication auth, HttpServletRequest http) {
        User u = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getEmail().equalsIgnoreCase(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use Change password on your Profile page for your own account");
        }
        u.setPassword(encoder.encode(req.newPassword()));
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        try {
            audit.log(auth.getName(), "PASSWORD_RESET", "Reset password for " + u.getEmail(), http.getRemoteAddr());
        } catch (Exception ignored) {
        }
        return Map.of("message", "Password reset");
    }
}