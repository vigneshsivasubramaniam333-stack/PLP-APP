package com.plp.program.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoices", schema = "plp_program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String invoiceNumber;

    @Column(nullable = false)
    private UUID anchorId;

    @Column(nullable = false)
    private UUID borrowerId;

    @Column(nullable = false)
    private UUID programId;

    @Column(name = "sub_program_id")
    private UUID subProgramId;

    @Column(name = "flow_type", length = 40)
    private String flowType;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal invoiceAmount;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(length = 50)
    private String poNumber;

    private LocalDate poDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal poAmount;

    @Column(length = 50)
    private String grnNumber;

    private LocalDate grnDate;

    @Builder.Default
    private Boolean threeWayMatch = false;

    @Column(precision = 5, scale = 2)
    private BigDecimal marginPercent;

    @Column(precision = 15, scale = 2)
    private BigDecimal eligibleAmount;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal discountedAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal availableAmount;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "UPLOADED";

    @Builder.Default
    private Boolean verified = false;

    private Instant verifiedAt;

    private UUID verifiedBy;

    @Column(name = "uploaded_by_user_id")
    private UUID uploadedByUserId;

    @Builder.Default
    private Boolean anchorConfirmed = false;

    private Instant anchorConfirmedAt;

    @Builder.Default
    @Column(name = "borrower_accepted", nullable = false)
    private Boolean borrowerAccepted = false;

    @Column(name = "borrower_accepted_at")
    private Instant borrowerAcceptedAt;

    @Column(length = 20)
    @Builder.Default
    private String source = "MANUAL";

    @Column(length = 15)
    private String gstinSeller;

    @Column(length = 15)
    private String gstinBuyer;

    @Column(length = 100)
    private String paymentTerms;

    @Column(length = 500)
    private String description;

    @Column(name = "digital_invoice_file_name", length = 255)
    private String digitalInvoiceFileName;

    @Column(name = "digital_invoice_content_type", length = 100)
    private String digitalInvoiceContentType;

    /** Never serialized to API clients — use authenticated download endpoint. */
    @JsonIgnore
    @Column(name = "digital_invoice_storage_key", length = 500)
    private String digitalInvoiceStorageKey;

    @Column(name = "digital_invoice_uploaded_at")
    private Instant digitalInvoiceUploadedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
