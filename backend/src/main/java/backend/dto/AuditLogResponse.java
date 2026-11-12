package backend.dto;

import backend.entity.AuditLog;
import java.time.LocalDateTime;

public record AuditLogResponse(Long id, String actor, String action, String details,
                               String ipAddress, LocalDateTime createdAt) {
    public static AuditLogResponse from(AuditLog l) {
        return new AuditLogResponse(l.getId(), l.getActor(), l.getAction(), l.getDetails(),
                l.getIpAddress(), l.getCreatedAt());
    }
}