package backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import backend.dto.CreateUserRequest;
import backend.dto.UserResponse;
import backend.entity.User;
import backend.repository.UserRepository;
import backend.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    @GetMapping
    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req,
                                               Authentication auth, HttpServletRequest http) {
        String email = req.email().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        User u = new User();
        u.setName(req.name().trim());
        u.setEmail(email);
        u.setPassword(encoder.encode(req.password()));
        u.setRole(req.role());
        users.save(u);
        record(auth, "USER_CREATED", "Created " + u.getRole() + " account " + email, http);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(u));
    }

    @PatchMapping("/{id}/deactivate")
    @Transactional
    public UserResponse deactivate(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        User u = find(id);
        if (u.getEmail().equalsIgnoreCase(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own account");
        }
        u.setActive(false);
        record(auth, "USER_DEACTIVATED", "Deactivated " + u.getEmail(), http);
        return UserResponse.from(u);
    }

    @PatchMapping("/{id}/activate")
    @Transactional
    public UserResponse activate(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        User u = find(id);
        u.setActive(true);
        u.setFailedAttempts(0);
        u.setLockedUntil(null);
        record(auth, "USER_ACTIVATED", "Activated and unlocked " + u.getEmail(), http);
        return UserResponse.from(u);
    }

    private void record(Authentication auth, String action, String details, HttpServletRequest http) {
        try {
            audit.log(auth.getName(), action, details, http.getRemoteAddr());
        } catch (Exception ignored) {
            // an audit failure must never block an admin action
        }
    }

    private User find(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}