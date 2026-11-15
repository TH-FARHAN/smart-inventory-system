package backend.controller;

import backend.entity.User;
import backend.repository.UserRepository;
import backend.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/account/photo")
@RequiredArgsConstructor
public class ProfilePhotoController {
    private static final int MAX_CHARS = 150_000;

    private final UserRepository users;
    private final AuditService audit;

    public record PhotoRequest(@NotBlank(message = "Photo is required") String photo) {}

    @GetMapping
    public Map<String, String> get(Authentication auth) {
        return Collections.singletonMap("photo", me(auth).getPhoto());
    }

    @PutMapping
    @Transactional
    public Map<String, String> save(@Valid @RequestBody PhotoRequest req, Authentication auth,
                                    HttpServletRequest http) {
        String photo = req.photo().trim();
        validate(photo);
        me(auth).setPhoto(photo);
        record(auth, "PROFILE_PHOTO_CHANGED", http);
        return Collections.singletonMap("photo", photo);
    }

    @DeleteMapping
    @Transactional
    public Map<String, String> remove(Authentication auth, HttpServletRequest http) {
        me(auth).setPhoto(null);
        record(auth, "PROFILE_PHOTO_REMOVED", http);
        return Collections.singletonMap("photo", null);
    }

    private void validate(String photo) {
        if (!photo.startsWith("data:image/jpeg;base64,") && !photo.startsWith("data:image/png;base64,"))
            throw bad("Only JPEG or PNG photos are allowed.");
        if (photo.length() > MAX_CHARS)
            throw bad("The photo is too large. Choose a smaller one.");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(photo.substring(photo.indexOf(',') + 1));
        } catch (IllegalArgumentException e) {
            throw bad("The photo data is not valid.");
        }
        boolean jpeg = bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
        boolean png = bytes.length > 4 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G';
        if (!jpeg && !png) throw bad("The file is not a real image.");
    }

    private ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private User me(Authentication auth) {
        return users.findByEmail(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private void record(Authentication auth, String action, HttpServletRequest http) {
        try {
            audit.log(auth.getName(), action, null, http.getRemoteAddr());
        } catch (Exception ignored) {
            // an audit failure must never block the request
        }
    }
}