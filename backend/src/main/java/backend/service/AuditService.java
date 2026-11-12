package backend.service;

import backend.entity.AuditLog;
import backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository logs;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String actor, String action, String details, String ip) {
        AuditLog l = new AuditLog();
        l.setActor(actor == null ? "anonymous" : actor);
        l.setAction(action);
        l.setDetails(details != null && details.length() > 500 ? details.substring(0, 500) : details);
        l.setIpAddress(ip);
        logs.save(l);
    }
}