package backend.controller;

import backend.entity.User;
import backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;

/** Admin only: every user's profile photo, keyed by user id, so the Users screens can show faces. */
@RestController
@RequestMapping("/api/admin/user-photos")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserPhotosController {
    private final UserRepository users;

    @GetMapping
    public Map<Long, String> all() {
        Map<Long, String> out = new LinkedHashMap<>();
        for (User u : users.findAll()) {
            if (u.getPhoto() != null && !u.getPhoto().isBlank()) out.put(u.getId(), u.getPhoto());
        }
        return out;
    }
}