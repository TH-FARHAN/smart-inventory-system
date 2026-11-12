package backend.config;

import backend.entity.User;
import backend.enums.Role;
import backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Value("${admin.email:}")    private String email;
    @Value("${admin.password:}") private String password;

    @Override
    public void run(String... args) {
        if (email.isBlank() || password.length() < 8) {
            log.warn("Admin not created: set ADMIN_EMAIL and ADMIN_PASSWORD (min 8 chars)");
            return;
        }
        if (users.existsByEmail(email)) return;
        User admin = new User();
        admin.setName("Administrator");
        admin.setEmail(email);
        admin.setPassword(encoder.encode(password));
        admin.setRole(Role.ADMIN);
        users.save(admin);
        log.info("Admin user created");
    }
}