    package backend.controller;

    import backend.dto.AuditLogResponse;
    import backend.repository.AuditLogRepository;
    import lombok.RequiredArgsConstructor;
    import org.springframework.security.access.prepost.PreAuthorize;
    import org.springframework.web.bind.annotation.GetMapping;
    import org.springframework.web.bind.annotation.RequestMapping;
    import org.springframework.web.bind.annotation.RestController;
    import java.util.List;

    @RestController
    @RequestMapping("/api/admin/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiredArgsConstructor
    public class AuditLogController {
        private final AuditLogRepository logs;

        @GetMapping
        public List<AuditLogResponse> latest() {
            return logs.findTop100ByOrderByCreatedAtDesc().stream().map(AuditLogResponse::from).toList();
        }
    }