package com.plp.lending.payment.controller;

import com.plp.lending.security.BorrowerTenantResolver;
import com.plp.lending.security.LoanAccessGuard;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

final class PaymentAccessResolver {

    private PaymentAccessResolver() {}

    static UUID resolveBorrowerId(
            UUID borrowerIdParam,
            String rolesHeader,
            String linkedEntityId,
            String linkedEntityType,
            String userId) {
        Set<String> roles = LoanAccessGuard.parseRoles(rolesHeader);
        if (LoanAccessGuard.isBorrowerRole(roles)) {
            return BorrowerTenantResolver.requireBorrowerScope(
                    linkedEntityType, linkedEntityId, borrowerIdParam, userId);
        }
        if (LoanAccessGuard.isLenderRole(roles)) {
            if (borrowerIdParam == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "borrowerId is required");
            }
            return borrowerIdParam;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, LoanAccessGuard.MSG_ACCESS_DENIED);
    }
}
