package com.plp.program.service;

import com.plp.program.model.dto.integration.LosSubProgramUpsertRequest;
import com.plp.program.model.dto.integration.LosSubProgramUpsertResponse;
import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.enums.ProductType;
import com.plp.program.repository.AnchorRepository;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramRepository;
import com.plp.program.service.audit.LosSyncAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LosSubProgramIntegrationServiceTest {

    @Mock
    SubProgramRepository subProgramRepository;
    @Mock
    ProgramRepository programRepository;
    @Mock
    SubProgramService subProgramService;
    @Mock
    AnchorRepository anchorRepository;
    @Mock
    LosSyncAuditService losSyncAuditService;

    @InjectMocks
    LosSubProgramIntegrationService service;

    @Test
    void upsert_persistsInterestRateFromRequest() {
        UUID programId = UUID.randomUUID();
        UUID anchorId = UUID.randomUUID();
        UUID lenderId = UUID.randomUUID();

        Program program =
                Program.builder()
                        .id(programId)
                        .lenderId(lenderId)
                        .defaultInterestRate(new BigDecimal("12.00"))
                        .maxTenureDays(90)
                        .productType(ProductType.INVOICE_DISCOUNTING)
                        .build();

        LosSubProgramUpsertRequest req = new LosSubProgramUpsertRequest();
        req.setSourceSystem("LOS");
        req.setLosSubProgramId("los-sp-1");
        req.setPlpProgramId(programId);
        req.setAnchorId(anchorId);
        req.setLenderId(lenderId);
        req.setSubProgramCode("PRG-SP");
        req.setName("Test SP");
        req.setFlowType("PURCHASE_BILL_DISCOUNTING");
        req.setAnchorRole("SELLER");
        req.setBorrowerRole("BUYER");
        req.setSubProgramLimit(new BigDecimal("300000"));
        req.setInterestRate(new BigDecimal("12.00"));
        req.setMaxTenureDays(90);

        when(programRepository.findById(programId)).thenReturn(Optional.of(program));
        when(anchorRepository.findById(anchorId)).thenReturn(Optional.of(com.plp.program.model.entity.Anchor.builder().id(anchorId).build()));
        when(subProgramRepository.findBySourceSystemAndLosSubProgramId("LOS", "los-sp-1")).thenReturn(Optional.empty());
        when(subProgramRepository.findByCode("PRG-SP")).thenReturn(Optional.empty());
        when(subProgramService.createSubProgram(any())).thenAnswer(inv -> {
            SubProgram sp = inv.getArgument(0);
            sp.setId(UUID.randomUUID());
            return sp;
        });

        LosSubProgramUpsertResponse resp = service.upsert(req);

        assertThat(resp.isCreated()).isTrue();
        ArgumentCaptor<SubProgram> captor = ArgumentCaptor.forClass(SubProgram.class);
        verify(subProgramService).createSubProgram(captor.capture());
        assertThat(captor.getValue().getInterestRate()).isEqualByComparingTo("12.00");
        assertThat(captor.getValue().getMaxTenureDays()).isEqualTo(90);
    }

    @Test
    void upsert_fallsBackToProgramDefaultInterestWhenRequestOmitsRate() {
        UUID programId = UUID.randomUUID();
        UUID anchorId = UUID.randomUUID();
        UUID lenderId = UUID.randomUUID();

        Program program =
                Program.builder()
                        .id(programId)
                        .lenderId(lenderId)
                        .defaultInterestRate(new BigDecimal("11.50"))
                        .productType(ProductType.INVOICE_DISCOUNTING)
                        .build();

        LosSubProgramUpsertRequest req = new LosSubProgramUpsertRequest();
        req.setSourceSystem("LOS");
        req.setLosSubProgramId("los-sp-2");
        req.setPlpProgramId(programId);
        req.setAnchorId(anchorId);
        req.setLenderId(lenderId);
        req.setSubProgramCode("PRG-SP2");
        req.setName("Test SP 2");
        req.setFlowType("PURCHASE_BILL_DISCOUNTING");
        req.setAnchorRole("SELLER");
        req.setBorrowerRole("BUYER");
        req.setSubProgramLimit(new BigDecimal("100000"));

        when(programRepository.findById(programId)).thenReturn(Optional.of(program));
        when(anchorRepository.findById(anchorId)).thenReturn(Optional.of(com.plp.program.model.entity.Anchor.builder().id(anchorId).build()));
        when(subProgramRepository.findBySourceSystemAndLosSubProgramId("LOS", "los-sp-2")).thenReturn(Optional.empty());
        when(subProgramRepository.findByCode("PRG-SP2")).thenReturn(Optional.empty());
        when(subProgramService.createSubProgram(any())).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(req);

        ArgumentCaptor<SubProgram> captor = ArgumentCaptor.forClass(SubProgram.class);
        verify(subProgramService).createSubProgram(captor.capture());
        assertThat(captor.getValue().getInterestRate()).isEqualByComparingTo("11.50");
    }
}
