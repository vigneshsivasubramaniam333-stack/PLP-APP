package com.plp.program.model.entity;

import com.plp.program.model.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "product_repayment_defaults", schema = "plp_program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRepaymentDefault {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 30)
    private ProductType productType;

    @Column(name = "repayment_mechanism", nullable = false, length = 30)
    @Builder.Default
    private String repaymentMechanism = "SMART_COLLECT";

    @Column(name = "pg_provider_code", length = 30)
    private String pgProviderCode;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
