package com.plp.program.service;

import com.plp.program.model.dto.integration.*;
import com.plp.program.model.entity.BorrowerProgramMapping;
import com.plp.program.model.enums.BorrowerProgramMappingStatus;
import com.plp.program.model.enums.ProductType;
import com.plp.program.repository.BorrowerProgramMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LosIntegrationServiceTest {

    @Mock
    BorrowerProgramMappingRepository borrowerProgramMappingRepository;

    @Mock
    LosProgramIntegrationService losProgramIntegrationService;

    @Mock
    LosSubProgramIntegrationService losSubProgramIntegrationService;

    @Mock
    LosBorrowerIntegrationService losBorrowerIntegrationService;

    @Mock
    LosSubProgramBorrowerLinkIntegrationService losSubProgramBorrowerLinkIntegrationService;

    @Mock
    LosBorrowerProgramMappingIntegrationService losBorrowerProgramMappingIntegrationService;

    @InjectMocks
    LosIntegrationService losIntegrationService;

    @Test
    void linkProgramBorrower_returnsExistingMapping_andSkipsProgramCreation() {
        UUID mappingId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID subProgramId = UUID.randomUUID();

        BorrowerProgramMapping existing =
                BorrowerProgramMapping.builder()
                        .id(mappingId)
                        .sourceSystem("LOS-A")
                        .losApplicationId("APP-1")
                        .losBorrowerId("B-1")
                        .borrowerId(borrowerId)
                        .programId(programId)
                        .subProgramId(subProgramId)
                        .approvedLimit(new BigDecimal("10000.00"))
                        .validFrom(LocalDate.of(2026, 1, 1))
                        .validTo(LocalDate.of(2027, 1, 1))
                        .status(BorrowerProgramMappingStatus.PENDING_APPROVAL)
                        .build();

        when(borrowerProgramMappingRepository.findBySourceSystemAndLosApplicationId("LOS-A", "APP-1"))
                .thenReturn(Optional.of(existing));

        LosProgramBorrowerLinkResponse response =
                losIntegrationService.linkProgramBorrower(sampleRequest(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(response.getPlpBorrowerProgramMappingId()).isEqualTo(mappingId);
        assertThat(response.getMappingStatus()).isEqualTo(BorrowerProgramMappingStatus.PENDING_APPROVAL);
        assertThat(response.isCreated()).isFalse();

        verify(losProgramIntegrationService, never()).upsert(any());
        verify(losBorrowerIntegrationService, never()).upsert(any());
    }

    @Test
    void linkProgramBorrower_callsGranularServices_untilMappingCreated() {
        UUID lenderId = UUID.randomUUID();
        UUID anchorId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID subProgramId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        when(borrowerProgramMappingRepository.findBySourceSystemAndLosApplicationId(eq("LOS-A"), eq("APP-1")))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        when(losProgramIntegrationService.upsert(any())).thenAnswer(
                inv ->
                        LosProgramUpsertResponse.builder()
                                .plpProgramId(programId)
                                .programCode("PRG-DUMMY")
                                .created(true)
                                .updated(false)
                                .build());

        when(losSubProgramIntegrationService.upsert(any())).thenAnswer(
                inv ->
                        LosSubProgramUpsertResponse.builder()
                                .plpSubProgramId(subProgramId)
                                .subProgramCode("SP-DUMMY")
                                .created(true)
                                .updated(false)
                                .build());

        when(losBorrowerIntegrationService.upsert(any())).thenAnswer(
                inv ->
                        LosBorrowerUpsertResponse.builder()
                                .plpBorrowerId(borrowerId)
                                .borrowerCode("BR-X")
                                .created(true)
                                .updated(false)
                                .build());

        when(losBorrowerProgramMappingIntegrationService.upsert(any()))
                .thenReturn(
                        LosBorrowerProgramMappingUpsertResponse.builder()
                                .plpBorrowerProgramMappingId(mappingId)
                                .mappingStatus(BorrowerProgramMappingStatus.PENDING_APPROVAL)
                                .created(true)
                                .updated(false)
                                .build());

        BorrowerProgramMapping persisted =
                BorrowerProgramMapping.builder()
                        .id(mappingId)
                        .borrowerId(borrowerId)
                        .programId(programId)
                        .subProgramId(subProgramId)
                        .losApplicationId("APP-1")
                        .losBorrowerId("B-1")
                        .status(BorrowerProgramMappingStatus.PENDING_APPROVAL)
                        .approvedLimit(new BigDecimal("50000.00"))
                        .validFrom(LocalDate.of(2026, 2, 1))
                        .validTo(LocalDate.of(2027, 2, 1))
                        .sourceSystem("LOS-A")
                        .build();

        when(borrowerProgramMappingRepository.findById(mappingId)).thenReturn(Optional.of(persisted));

        LosProgramBorrowerLinkResponse response =
                losIntegrationService.linkProgramBorrower(sampleRequest(lenderId, anchorId));

        assertThat(response.isCreated()).isTrue();
        assertThat(response.getPlpBorrowerProgramMappingId()).isEqualTo(mappingId);

        verify(losProgramIntegrationService).upsert(any(LosProgramUpsertRequest.class));
        verify(losSubProgramIntegrationService).upsert(any(LosSubProgramUpsertRequest.class));
        verify(losBorrowerIntegrationService).upsert(any(LosBorrowerUpsertRequest.class));
        verify(losSubProgramBorrowerLinkIntegrationService).link(any(LosSubProgramBorrowerLinkRequest.class));
        verify(losBorrowerProgramMappingIntegrationService).upsert(any(LosBorrowerProgramMappingUpsertRequest.class));
    }

    private static LosProgramBorrowerLinkRequest sampleRequest(UUID lenderId, UUID anchorId) {
        LosProgramBorrowerLinkRequest req = new LosProgramBorrowerLinkRequest();
        req.setSourceSystem("LOS-A");
        req.setLosApplicationId("APP-1");
        req.setLosBorrowerId("B-1");
        req.setApprovedLimit(new BigDecimal("50000.00"));
        req.setValidFrom(LocalDate.of(2026, 2, 1));
        req.setValidTo(LocalDate.of(2027, 2, 1));

        LosProgramBorrowerLinkRequest.LosBorrowerDetailsDto b = new LosProgramBorrowerLinkRequest.LosBorrowerDetailsDto();
        b.setName("Test Borrower");
        req.setBorrower(b);

        LosProgramBorrowerLinkRequest.LosProgramDetailsDto p = new LosProgramBorrowerLinkRequest.LosProgramDetailsDto();
        p.setProgramCode("PRG-DUMMY");
        p.setProgramName("Dummy Program");
        p.setProductType(ProductType.PAY_DAY_LOAN);
        p.setLenderId(lenderId);
        p.setProgramLimit(new BigDecimal("1000000.00"));
        p.setMaxBorrowerLimit(new BigDecimal("100000.00"));
        req.setProgram(p);

        LosProgramBorrowerLinkRequest.LosSubProgramDetailsDto sp =
                new LosProgramBorrowerLinkRequest.LosSubProgramDetailsDto();
        sp.setCode("SP-DUMMY");
        sp.setName("Dummy Sub");
        sp.setAnchorId(anchorId);
        sp.setFlowType("PAY_LOAN");
        sp.setAnchorRole("EMPLOYER");
        sp.setBorrowerRole("EMPLOYEE");
        sp.setSubProgramLimit(new BigDecimal("500000.00"));
        req.setSubProgram(sp);

        return req;
    }
}
