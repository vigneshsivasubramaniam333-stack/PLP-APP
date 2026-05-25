package com.plp.program.service.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plp.program.model.entity.LosSyncAudit;
import com.plp.program.model.enums.LosSyncAuditStatus;
import com.plp.program.repository.LosSyncAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LosSyncAuditService {

    private static final int MAX_ERROR_LEN = 8000;

    private final LosSyncAuditRepository losSyncAuditRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            String resourceType,
            UUID resourceId,
            String sourceSystem,
            String externalId,
            Object request,
            Object response) {
        persist(
                LosSyncAudit.builder()
                        .resourceType(resourceType)
                        .resourceId(resourceId)
                        .sourceSystem(truncate(sourceSystem, 50))
                        .externalId(truncate(externalId, 200))
                        .requestPayload(toPayload(request))
                        .responsePayload(toPayload(response))
                        .status(LosSyncAuditStatus.SUCCESS)
                        .errorMessage(null)
                        .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String resourceType,
            UUID resourceId,
            String sourceSystem,
            String externalId,
            Object request,
            String errorMessage) {
        persist(
                LosSyncAudit.builder()
                        .resourceType(resourceType)
                        .resourceId(resourceId)
                        .sourceSystem(truncate(sourceSystem, 50))
                        .externalId(truncate(externalId, 200))
                        .requestPayload(toPayload(request))
                        .responsePayload(null)
                        .status(LosSyncAuditStatus.FAILED)
                        .errorMessage(truncate(errorMessage, MAX_ERROR_LEN))
                        .build());
    }

    private void persist(LosSyncAudit row) {
        try {
            losSyncAuditRepository.save(row);
        } catch (Exception e) {
            log.warn("Failed to persist LOS sync audit: {}", e.getMessage());
        }
    }

    private Map<String, Object> toPayload(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, new TypeReference<>() {});
        } catch (IllegalArgumentException ex) {
            return Map.of("_serializationError", ex.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
