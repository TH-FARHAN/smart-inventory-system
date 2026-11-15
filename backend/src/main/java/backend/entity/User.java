package backend.entity;

import backend.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;          // BCrypt hash, never plain text

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private boolean active = true;
    private int failedAttempts = 0;
    private LocalDateTime lockedUntil;

    // password reset by email code (admin only)
    private String resetOtpHash;
    private LocalDateTime resetOtpExpiresAt;
    private LocalDateTime resetOtpSentAt;
    private Integer resetOtpAttempts;

    @Column(columnDefinition = "text")
    private String photo;   // small JPEG as a base64 data URL
    private LocalDateTime createdAt = LocalDateTime.now();
}