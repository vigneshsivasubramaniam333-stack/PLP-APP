package com.plp.lending.controller;

import com.plp.lending.audit.AuditEventResponse;
import com.plp.lending.audit.AuditEventsQueryService;
import com.plp.lending.security.LenderRoleAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/lending-audit")
@RequiredArgsConstructor
public class LendingAuditController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditEventsQueryService auditEventsQueryService;

    @GetMapping("/events")
    public ResponseEntity<Map<String, Object>> listEvents(
            @RequestHeader(value = LenderRoleAuthorization.HEADER_USER_ROLES, required = false)
                    String rolesHeader,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String performedByRole,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        LenderRoleAuthorization.requireLenderAuditAccess(rolesHeader);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Page<AuditEventResponse> result =
                auditEventsQueryService.search(
                        PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")),
                        eventType,
                        entityType,
                        entityId,
                        status,
                        performedByRole,
                        fromDate,
                        toDate);

        Map<String, Object> pageBody = new LinkedHashMap<>();
        pageBody.put("content", result.getContent());
        pageBody.put("totalElements", result.getTotalElements());
        pageBody.put("totalPages", result.getTotalPages());
        pageBody.put("number", result.getNumber());
        pageBody.put("size", result.getSize());
        pageBody.put("first", result.isFirst());
        pageBody.put("last", result.isLast());

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "data", pageBody));
    }
}
