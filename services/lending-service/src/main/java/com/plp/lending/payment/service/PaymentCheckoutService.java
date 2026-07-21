package com.plp.lending.payment.service;

import com.plp.lending.integration.ProgramServiceAuthHeaders;
import com.plp.lending.integration.ProgramServiceEffectiveBorrowerTermsClient;
import com.plp.lending.model.entity.Loan;
import com.plp.lending.model.enums.LoanStatus;
import com.plp.lending.payment.PayuHashUtil;
import com.plp.lending.payment.config.PayuProperties;
import com.plp.lending.payment.model.PaymentCheckoutLine;
import com.plp.lending.payment.model.PaymentInProgress;
import com.plp.lending.payment.model.PaymentTransaction;
import com.plp.lending.payment.repository.PaymentCheckoutLineRepository;
import com.plp.lending.payment.repository.PaymentInProgressRepository;
import com.plp.lending.payment.repository.PaymentTransactionRepository;
import com.plp.lending.repository.LoanRepository;
import com.plp.lending.service.LoanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCheckoutService {

    public static final String STATUS_INITIALIZED = "INITIALIZED";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_REMOVED = "REMOVED";
    public static final String PAYMENT_METHOD_PAYU = "PAYU_PG";
    public static final String PIP_OPEN = "OPEN";

    private static final List<LoanStatus> REPAYABLE_LOAN_STATUSES =
            List.of(LoanStatus.DISBURSED, LoanStatus.REPAYMENT_DUE, LoanStatus.OVERDUE);

    private final PaymentCheckoutLineRepository checkoutLineRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentInProgressRepository pipRepository;
    private final LoanRepository loanRepository;
    private final LoanService loanService;
    private final ProgramServiceEffectiveBorrowerTermsClient termsClient;
    private final PayuProperties payuProperties;
    private final PayuVerificationService payuVerificationService;
    private final RestTemplate restTemplate;

    @Transactional(readOnly = true)
    public List<PaymentCheckoutLine> listCart(UUID borrowerId) {
        List<PaymentCheckoutLine> lines =
                checkoutLineRepository.findByBorrowerIdAndStatusOrderByCreatedAtDesc(borrowerId, STATUS_INITIALIZED);
        enrichInterestAmounts(lines);
        return lines;
    }

    @Transactional(readOnly = true)
    public long cartCount(UUID borrowerId) {
        return checkoutLineRepository.countByBorrowerIdAndStatus(borrowerId, STATUS_INITIALIZED);
    }

    @Transactional(readOnly = true)
    public String resolvePaymentMethodForBorrower(UUID borrowerId) {
        Map<String, Object> profile = fetchPaymentProfile(borrowerId);
        if (profile != null && PAYMENT_METHOD_PAYU.equals(str(profile.get("paymentMethod")))) {
            return PAYMENT_METHOD_PAYU;
        }
        List<Loan> loans = loanRepository.findByBorrowerId(borrowerId);
        for (Loan loan : loans) {
            if (loan.getSubProgramId() != null) {
                var terms = termsClient.fetchEffectiveTerms(loan.getSubProgramId(), borrowerId);
                if (terms.isPresent() && PAYMENT_METHOD_PAYU.equals(terms.get().paymentMethod())) {
                    return PAYMENT_METHOD_PAYU;
                }
            }
        }
        return "SMART_COLLECT";
    }

    @Transactional
    public PaymentCheckoutLine addInvoiceToCart(UUID borrowerId, UUID invoiceId, BigDecimal amountOverride) {
        Map<String, Object> invoice = fetchInvoice(invoiceId);
        UUID invBorrower = uuid(invoice.get("borrowerId"));
        if (!borrowerId.equals(invBorrower)) {
            throw new IllegalArgumentException("Invoice does not belong to this borrower");
        }
        Loan loan = findRepayableLoanForInvoice(borrowerId, invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("No repayable loan found for this invoice"));
        try {
            loanService.syncLmsOutstandingForList(List.of(loan));
            loan = loanRepository.findById(loan.getId()).orElse(loan);
        } catch (Exception e) {
            log.warn("LMS refresh before cart add skipped for {}: {}", loan.getLoanNumber(), e.getMessage());
        }
        UUID subProgramId = resolveSubProgramId(borrowerId, invoice, loan);
        UUID programId = uuid(invoice.get("programId"));
        if (programId == null && loan.getProgramId() != null) {
            programId = loan.getProgramId();
        }
        var terms = termsClient.fetchEffectiveTerms(subProgramId, borrowerId)
                .orElseThrow(() -> new IllegalArgumentException("Borrower is not enrolled on this sub-program"));
        if (!PAYMENT_METHOD_PAYU.equals(terms.paymentMethod())) {
            throw new IllegalArgumentException("PayU payment is not enabled for this borrower. Use Smart Collect repayment.");
        }
        if (pipRepository.existsByInvoiceIdAndPipStatus(invoiceId, PIP_OPEN)) {
            throw new IllegalArgumentException("A payment is already in progress for this invoice (PRUS)");
        }
        checkoutLineRepository.findByBorrowerIdAndInvoiceIdAndStatus(borrowerId, invoiceId, STATUS_INITIALIZED)
                .ifPresent(line -> {
                    throw new IllegalArgumentException("Invoice is already in the payment cart");
                });
        BigDecimal amount = amountOverride != null && amountOverride.compareTo(BigDecimal.ZERO) > 0
                ? amountOverride
                : resolveCartPayAmount(loan);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Repayment amount must be positive");
        }
        PaymentCheckoutLine line = PaymentCheckoutLine.builder()
                .borrowerId(borrowerId)
                .invoiceId(invoiceId)
                .loanId(loan.getId())
                .subProgramId(subProgramId)
                .programId(programId)
                .invoiceNumber(str(invoice.get("invoiceNumber")))
                .amountToPay(amount)
                .interestAmount(loan.getInterestAmount())
                .discountAmount(BigDecimal.ZERO)
                .status(STATUS_INITIALIZED)
                .build();
        PaymentCheckoutLine saved = checkoutLineRepository.save(line);
        saved.setInterestAmount(loan.getInterestAmount());
        return saved;
    }

    @Transactional
    public List<PaymentCheckoutLine> addBulk(UUID borrowerId, List<UUID> invoiceIds) {
        List<PaymentCheckoutLine> out = new ArrayList<>();
        for (UUID invoiceId : invoiceIds) {
            out.add(addInvoiceToCart(borrowerId, invoiceId, null));
        }
        return out;
    }

    @Transactional
    public void removeLine(UUID borrowerId, UUID lineId) {
        PaymentCheckoutLine line = checkoutLineRepository.findById(lineId)
                .orElseThrow(() -> new IllegalArgumentException("Cart line not found"));
        if (!borrowerId.equals(line.getBorrowerId())) {
            throw new IllegalArgumentException("Cart line does not belong to this borrower");
        }
        line.setStatus(STATUS_REMOVED);
        checkoutLineRepository.save(line);
    }

    @Transactional
    public void clearCart(UUID borrowerId) {
        for (PaymentCheckoutLine line : listCart(borrowerId)) {
            line.setStatus(STATUS_REMOVED);
            checkoutLineRepository.save(line);
        }
    }

    @Transactional
    public Map<String, Object> initiatePayu(UUID borrowerId, String portalSource) {
        List<PaymentCheckoutLine> lines = listCart(borrowerId);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Payment cart is empty");
        }
        if (payuProperties.getMerchantKey() == null || payuProperties.getMerchantKey().isBlank()) {
            throw new IllegalStateException("PayU merchant key is not configured");
        }
        BigDecimal total = lines.stream()
                .map(PaymentCheckoutLine::getAmountToPay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String txnRef = payuProperties.getTransactionIdSuffix() + "-" + borrowerId.toString().substring(0, 8)
                + "-" + Instant.now().toEpochMilli();
        PaymentTransaction txn = PaymentTransaction.builder()
                .pgTransactionRef(txnRef)
                .borrowerId(borrowerId)
                .totalAmount(total)
                .gateway("PAYU")
                .status("INITIATED")
                .portalSource(portalSource != null ? portalSource : "PLP")
                .build();
        txn = transactionRepository.save(txn);
        for (PaymentCheckoutLine line : lines) {
            line.setPgTransactionId(txn.getId());
            checkoutLineRepository.save(line);
        }
        Map<String, Object> borrower = fetchBorrower(borrowerId);
        String email = str(borrower.get("email"));
        if (email == null || email.isBlank()) {
            email = "borrower@example.com";
        }
        String name = str(borrower.get("name"));
        if (name == null || name.isBlank()) {
            name = "Borrower";
        }
        String amount = PayuHashUtil.formatAmount(total);
        String productinfo = "PLP Invoice Repayment";
        String hash = PayuHashUtil.forwardHash(
                payuProperties.getMerchantKey(),
                txnRef,
                amount,
                productinfo,
                name,
                email,
                borrowerId.toString(),
                payuProperties.getMerchantSalt());
        String callbackBase = payuProperties.getCallbackBaseUrl().replaceAll("/$", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("baseUrl", payuProperties.getGatewayUrl());
        result.put("key", payuProperties.getMerchantKey());
        result.put("txnid", txnRef);
        result.put("amount", amount);
        result.put("productinfo", productinfo);
        result.put("firstname", name);
        result.put("email", email);
        result.put("phone", str(borrower.get("phone")));
        result.put("udf1", borrowerId.toString());
        result.put("hash", hash);
        result.put("surl", callbackBase + "/api/v1/webhooks/payments/payu/success");
        result.put("furl", callbackBase + "/api/v1/webhooks/payments/payu/failure");
        result.put("transactionId", txn.getId().toString());
        return result;
    }

    @Transactional
    public String handlePayuSuccess(Map<String, String> params) {
        String txnid = params.get("txnid");
        String status = params.get("status");
        String hash = params.get("hash");
        PaymentTransaction txn = transactionRepository.findByPgTransactionRef(txnid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown transaction: " + txnid));
        if ("SUCCESS".equals(txn.getStatus())) {
            return redirectUrl(txn, true);
        }
        if (status == null || !"success".equalsIgnoreCase(status.trim())) {
            log.warn("PayU success callback with non-success status {} for txn {}", status, txnid);
            return handlePayuFailure(params);
        }
        Map<String, String> normalized = PayuHashUtil.normalizeParams(params);
        String merchantKey = payuProperties.getMerchantKey();
        String expectedHash = PayuHashUtil.reverseHashFromCallback(
                normalized, payuProperties.getMerchantSalt(), merchantKey);
        boolean hashValid = PayuHashUtil.reverseHashMatches(
                params, payuProperties.getMerchantSalt(), merchantKey, hash);
        if (!hashValid) {
            log.warn(
                    "PayU hash mismatch for txn {} (status={}). received={} expected={} callbackKeys={}",
                    txnid,
                    status,
                    hash,
                    expectedHash,
                    normalized.keySet());
            hashValid = payuVerificationService.isSuccessful(txnid);
            if (hashValid) {
                log.info("PayU verify_payment confirmed success for txn {} despite callback hash mismatch", txnid);
            }
        }
        if (!hashValid) {
            return handlePayuFailure(params);
        }
        txn.setStatus("SUCCESS");
        txn.setPayuMihpayid(params.get("mihpayid"));
        txn.setRawCallbackJson(new LinkedHashMap<>(params));
        transactionRepository.save(txn);
        List<PaymentCheckoutLine> lines = checkoutLineRepository.findByPgTransactionId(txn.getId());
        for (PaymentCheckoutLine line : lines) {
            line.setStatus(STATUS_PAID);
            checkoutLineRepository.save(line);
            PaymentInProgress pip = PaymentInProgress.builder()
                    .pgTransactionId(txn.getId())
                    .checkoutLineId(line.getId())
                    .invoiceId(line.getInvoiceId())
                    .loanId(line.getLoanId())
                    .borrowerId(line.getBorrowerId())
                    .principalAmount(line.getAmountToPay())
                    .discountAmount(line.getDiscountAmount())
                    .pipStatus(PIP_OPEN)
                    .build();
            pipRepository.save(pip);
            adjustInvoicePip(line.getInvoiceId(), line.getAmountToPay(), line.getDiscountAmount());
        }
        return redirectUrl(txn, true);
    }

    @Transactional
    public String handlePayuFailure(Map<String, String> params) {
        String txnid = params.get("txnid");
        if (txnid == null) {
            return payuProperties.getPlpBorrowerUiUrl() + "/payments/result?status=failure";
        }
        transactionRepository.findByPgTransactionRef(txnid).ifPresent(txn -> {
            txn.setStatus("FAILED");
            txn.setRawCallbackJson(new LinkedHashMap<>(params));
            transactionRepository.save(txn);
            for (PaymentCheckoutLine line : checkoutLineRepository.findByPgTransactionId(txn.getId())) {
                line.setStatus(STATUS_INITIALIZED);
                line.setPgTransactionId(null);
                checkoutLineRepository.save(line);
            }
        });
        PaymentTransaction txn = transactionRepository.findByPgTransactionRef(txnid).orElse(null);
        return redirectUrl(txn, false);
    }

    private String redirectUrl(PaymentTransaction txn, boolean success) {
        boolean los = txn != null && "LOS".equalsIgnoreCase(txn.getPortalSource());
        String base = los ? payuProperties.getLosBorrowerUiUrl() : payuProperties.getPlpBorrowerUiUrl();
        base = base.replaceAll("/$", "");
        String status = success ? "success" : "failure";
        String txnRef = txn != null ? txn.getPgTransactionRef() : "";
        if (los) {
            return base + "/invoice-discounting/payments/result?status=" + status + "&txnId=" + txnRef;
        }
        return base + "/payments/result?status=" + status + "&txnId=" + txnRef;
    }

    private UUID resolveSubProgramId(UUID borrowerId, Map<String, Object> invoice, Loan loan) {
        UUID fromInvoice = uuid(invoice.get("subProgramId"));
        if (fromInvoice != null) {
            return fromInvoice;
        }
        if (loan.getSubProgramId() != null) {
            return loan.getSubProgramId();
        }
        UUID programId = uuid(invoice.get("programId"));
        if (programId == null) {
            programId = loan.getProgramId();
        }
        UUID anchorId = uuid(invoice.get("anchorId"));
        if (anchorId == null) {
            anchorId = loan.getAnchorId();
        }
        Map<String, Object> profile = fetchPaymentProfile(borrowerId);
        if (profile != null) {
            Object enrollmentsObj = profile.get("enrollments");
            if (enrollmentsObj instanceof List<?> rows) {
                UUID fromProfile = matchEnrollmentSubProgram(rows, programId, anchorId, true);
                if (fromProfile != null) {
                    return fromProfile;
                }
                fromProfile = matchEnrollmentSubProgram(rows, programId, null, false);
                if (fromProfile != null) {
                    return fromProfile;
                }
                List<UUID> active = enrollmentSubProgramIds(rows);
                if (active.size() == 1) {
                    return active.get(0);
                }
                if (programId == null && !active.isEmpty()) {
                    return active.get(0);
                }
            }
        }
        throw new IllegalArgumentException("Invoice is not linked to a sub-program");
    }

    private UUID matchEnrollmentSubProgram(
            List<?> rows, UUID programId, UUID anchorId, boolean requireProgramMatch) {
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> m)) {
                continue;
            }
            UUID spProgram = uuid(m.get("programId"));
            UUID spAnchor = uuid(m.get("anchorId"));
            UUID spId = uuid(m.get("subProgramId"));
            if (spId == null) {
                continue;
            }
            if (requireProgramMatch) {
                if (programId == null || !programId.equals(spProgram)) {
                    continue;
                }
                if (anchorId != null && spAnchor != null && !anchorId.equals(spAnchor)) {
                    continue;
                }
            } else if (programId != null && !programId.equals(spProgram)) {
                continue;
            }
            return spId;
        }
        return null;
    }

    private List<UUID> enrollmentSubProgramIds(List<?> rows) {
        List<UUID> ids = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> m)) {
                continue;
            }
            UUID spId = uuid(m.get("subProgramId"));
            if (spId != null) {
                ids.add(spId);
            }
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchPaymentProfile(UUID borrowerId) {
        try {
            Map<String, Object> resp = restTemplate.exchange(
                            "http://program-service/api/v1/borrowers/{id}/payment-profile",
                            HttpMethod.GET,
                            new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders()),
                            Map.class,
                            borrowerId)
                    .getBody();
            if (resp == null || !"SUCCESS".equals(resp.get("status"))) {
                return null;
            }
            Object data = resp.get("data");
            if (data instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
        } catch (Exception e) {
            log.warn("Failed to load borrower payment profile {}: {}", borrowerId, e.getMessage());
        }
        return null;
    }

    private Optional<Loan> findRepayableLoanForInvoice(UUID borrowerId, UUID invoiceId) {
        return loanRepository.findByBorrowerId(borrowerId).stream()
                .filter(l -> invoiceId.equals(l.getInvoiceId()))
                .filter(l -> REPAYABLE_LOAN_STATUSES.contains(l.getStatus()))
                .findFirst();
    }

    /**
     * Prefer live LMS payoff (payOffAndDueAmount); fall back to stored outstanding / total repayable.
     */
    private BigDecimal resolveCartPayAmount(Loan loan) {
        try {
            BigDecimal livePayoff = loanService.getPayoffAmount(loan.getId());
            if (livePayoff != null && livePayoff.compareTo(BigDecimal.ZERO) > 0) {
                return livePayoff;
            }
        } catch (Exception e) {
            log.warn("Live payoff unavailable for cart line loan {}: {}", loan.getLoanNumber(), e.getMessage());
        }
        if (loan.getOutstandingAmount() != null && loan.getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0) {
            return loan.getOutstandingAmount();
        }
        return loan.getTotalRepayable();
    }

    private void enrichInterestAmounts(List<PaymentCheckoutLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Set<UUID> loanIds = new HashSet<>();
        for (PaymentCheckoutLine line : lines) {
            if (line.getLoanId() != null) {
                loanIds.add(line.getLoanId());
            }
        }
        if (loanIds.isEmpty()) {
            return;
        }
        Map<UUID, BigDecimal> interestByLoan = new HashMap<>();
        for (Loan loan : loanRepository.findAllById(loanIds)) {
            if (loan.getInterestAmount() != null) {
                interestByLoan.put(loan.getId(), loan.getInterestAmount());
            }
        }
        for (PaymentCheckoutLine line : lines) {
            if (line.getLoanId() != null) {
                line.setInterestAmount(interestByLoan.get(line.getLoanId()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchInvoice(UUID invoiceId) {
        Map<String, Object> resp = restTemplate.exchange(
                        "http://program-service/api/v1/invoices/{id}",
                        HttpMethod.GET,
                        new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders()),
                        Map.class,
                        invoiceId)
                .getBody();
        if (resp == null) {
            throw new IllegalArgumentException("Invoice not found");
        }
        if (resp.get("id") != null) {
            return resp;
        }
        Object data = resp.get("data");
        if (data instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IllegalArgumentException("Invoice not found: " + invoiceId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchBorrower(UUID borrowerId) {
        Map<String, Object> resp = restTemplate.exchange(
                        "http://program-service/api/v1/borrowers/{id}",
                        HttpMethod.GET,
                        new HttpEntity<>(ProgramServiceAuthHeaders.trustedInternalHeaders()),
                        Map.class,
                        borrowerId)
                .getBody();
        if (resp != null && "SUCCESS".equals(resp.get("status")) && resp.get("data") instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of("name", "Borrower", "email", "borrower@example.com");
    }

    private void adjustInvoicePip(UUID invoiceId, BigDecimal principalAdd, BigDecimal discountAdd) {
        Map<String, Object> body = Map.of(
                "principalAdd", principalAdd,
                "discountAdd", discountAdd != null ? discountAdd : BigDecimal.ZERO);
        restTemplate.exchange(
                "http://program-service/api/v1/invoices/{id}/pip-adjust",
                HttpMethod.POST,
                new HttpEntity<>(body, ProgramServiceAuthHeaders.trustedInternalJsonHeaders()),
                Map.class,
                invoiceId);
    }

    private static UUID uuid(Object raw) {
        if (raw == null) {
            return null;
        }
        return UUID.fromString(raw.toString());
    }

    private static String str(Object raw) {
        return raw != null ? raw.toString() : null;
    }
}
