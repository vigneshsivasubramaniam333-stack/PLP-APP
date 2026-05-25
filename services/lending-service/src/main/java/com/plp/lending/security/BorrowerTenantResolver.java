package com.plp.lending.security;

import com.plp.lending.audit.AuditBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Resolves the authenticated borrower's id from gateway-trusted headers for portal APIs.
 */
public final class BorrowerTenantResolver {

    private static final Logger log = LoggerFactory.getLogger(BorrowerTenantResolver.class);

    private BorrowerTenantResolver() {}

    /**
     * @param queryBorrowerId optional borrower id from query/body; if present, must equal the header-linked borrower
     */
    public static UUID requireBorrowerScope(
            String linkedEntityType,
            String linkedEntityId,
            UUID queryBorrowerId,
            String userId) {
        if (linkedEntityType == null || linkedEntityType.isBlank()
                || !"BORROWER".equalsIgnoreCase(linkedEntityType.trim())) {
            String msg = "Borrower portal requires linkedEntityType BORROWER";
            AuditBridge.accessDenied("BORROWER", "", msg, userId, null, linkedEntityId, linkedEntityType);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
        }
        if (linkedEntityId == null || linkedEntityId.isBlank()) {
            String msg = "Missing X-Linked-Entity-Id";
            AuditBridge.accessDenied("BORROWER", "", msg, userId, null, linkedEntityId, linkedEntityType);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
        }
        final UUID borrowerId;
        try {
            borrowerId = UUID.fromString(linkedEntityId.trim());
        } catch (IllegalArgumentException e) {
            String msg = "Invalid X-Linked-Entity-Id";
            AuditBridge.accessDenied("BORROWER", "", msg, userId, null, linkedEntityId, linkedEntityType);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
        }
        if (queryBorrowerId != null && !queryBorrowerId.equals(borrowerId)) {
            String msg = "borrowerId does not match authenticated borrower";
            AuditBridge.accessDenied("BORROWER", borrowerId.toString(), msg, userId, null, linkedEntityId, linkedEntityType);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
        }
        log.info("Borrower scoped request: borrowerId={} userId={}", borrowerId, userId != null ? userId : "");
        return borrowerId;
    }

    public static boolean isBorrowerLinkedType(String linkedEntityType) {
        if (linkedEntityType == null || linkedEntityType.isBlank()) {
            return false;
        }
        return "BORROWER".equalsIgnoreCase(linkedEntityType.trim());
    }
}
