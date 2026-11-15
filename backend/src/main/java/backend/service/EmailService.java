package backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Sends email through Brevo's HTTPS API (Render's free plan blocks normal SMTP ports). */
@Slf4j
@Service
public class EmailService {
    @Value("${BREVO_API_KEY:}")
    private String apiKey;
    @Value("${BREVO_SENDER_EMAIL:}")
    private String senderEmail;
    @Value("${BREVO_SENDER_NAME:BxoStock}")
    private String senderName;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && senderEmail != null && !senderEmail.isBlank();
    }

    public boolean sendResetCode(String toEmail, String toName, String code) {
        if (!isConfigured()) return false;
        String html = "<div style=\"font-family:Arial,sans-serif;max-width:420px\">"
                + "<h2 style=\"color:#ff4d1c\">BxoStock</h2>"
                + "<p>Your password reset code is:</p>"
                + "<p style=\"font-size:32px;letter-spacing:8px;font-weight:bold\">" + code + "</p>"
                + "<p>This code expires in 10 minutes. If you did not ask for it, you can ignore this email.</p>"
                + "</div>";
        String json = "{\"sender\":{\"name\":\"" + esc(senderName) + "\",\"email\":\"" + esc(senderEmail) + "\"},"
                + "\"to\":[{\"email\":\"" + esc(toEmail) + "\",\"name\":\"" + esc(toName) + "\"}],"
                + "\"subject\":\"BxoStock password reset code\","
                + "\"htmlContent\":\"" + esc(html) + "\"}";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", apiKey)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) return true;
            log.warn("Brevo rejected the email: status {} body {}", res.statusCode(), res.body());
            return false;
        } catch (Exception e) {
            log.warn("Could not reach Brevo: {}", e.toString());
            return false;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}