package com.plp.program.audit;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditEventsQueryService {

    private final AuditEventRepository auditEventRepository;

    public Page<AuditEventResponse> search(
            Pageable pageable,
            String eventType,
            String entityType,
            String entityId,
            String status,
            String performedByRole,
            LocalDate fromDate,
            LocalDate toDate) {
        Specification<AuditEvent> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    if (eventType != null && !eventType.isBlank()) {
                        predicates.add(cb.equal(cb.upper(root.get("eventType")), eventType.trim().toUpperCase()));
                    }
                    if (entityType != null && !entityType.isBlank()) {
                        predicates.add(cb.equal(cb.upper(root.get("entityType")), entityType.trim().toUpperCase()));
                    }
                    if (entityId != null && !entityId.isBlank()) {
                        predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
                    }
                    if (status != null && !status.isBlank()) {
                        predicates.add(cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase()));
                    }
                    if (performedByRole != null && !performedByRole.isBlank()) {
                        String pattern = "%" + performedByRole.trim().toUpperCase() + "%";
                        predicates.add(cb.like(cb.upper(root.get("performedByRole")), pattern));
                    }
                    if (fromDate != null) {
                        Instant start = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
                    }
                    if (toDate != null) {
                        Instant endExclusive = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                        predicates.add(cb.lessThan(root.get("createdAt"), endExclusive));
                    }
                    if (predicates.isEmpty()) {
                        return cb.conjunction();
                    }
                    return cb.and(predicates.toArray(Predicate[]::new));
                };
        return auditEventRepository.findAll(spec, pageable).map(AuditEventResponse::fromEntity);
    }
}
