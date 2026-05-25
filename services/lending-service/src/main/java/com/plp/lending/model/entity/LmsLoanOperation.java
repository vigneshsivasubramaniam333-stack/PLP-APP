package com.plp.lending.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lms_loan_operations", schema = "plp_lending")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LmsLoanOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    @Column(nullable = false, length = 20)
    private String operation;

    @Column(name = "encore_account_id", length = 50)
    private String encoreAccountId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "request_json", columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
