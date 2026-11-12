package backend.service;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {
    private record Entry(int answer, long expiresAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public Map<String, String> generate() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
        if (store.size() > 10000) store.clear();
        int a = random.nextInt(9) + 1;
        int b = random.nextInt(9) + 1;
        String id = UUID.randomUUID().toString();
        store.put(id, new Entry(a + b, now + 5 * 60 * 1000L));
        return Map.of("captchaId", id, "question", a + " + " + b + " = ?");
    }

    public boolean verify(String id, String answer) {
        if (id == null || answer == null) return false;
        Entry e = store.remove(id); // single use
        if (e == null || e.expiresAt() < System.currentTimeMillis()) return false;
        try {
            return Integer.parseInt(answer.trim()) == e.answer();
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}