package com.plp.program.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "program_approval_config", schema = "plp_program")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramApprovalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "l1_role", nullable = false, length = 50)
    @Builder.Default
    private String l1Role = "CREDIT_ANALYST";

    @Column(name = "l2_role", nullable = false, length = 50)
    @Builder.Default
    private String l2Role = "CREDIT_MANAGER";

    @Builder.Default
    private boolean enabled = true;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
