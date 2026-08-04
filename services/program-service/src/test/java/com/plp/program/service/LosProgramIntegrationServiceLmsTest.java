package com.plp.program.service;

import com.plp.program.model.dto.integration.LosProgramUpsertRequest;
import com.plp.program.model.entity.Program;
import com.plp.program.model.enums.ProductType;
import com.plp.program.repository.AnchorRepository;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramRepository;
import com.plp.program.service.audit.LosSyncAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class LosProgramIntegrationServiceLmsTest {

    @Mock
    ProgramRepository programRepository;
    @Mock
    ProgramService programService;
    @Mock
    AnchorRepository anchorRepository;
    @Mock
    SubProgramRepository subProgramRepository;
    @Mock
    LosSyncAuditService losSyncAuditService;
    @Mock
    ProgramFieldDefinitionService programFieldDefinitionService;

    @InjectMocks
    LosProgramIntegrationService integrationService;

    @Test
    void upsert_persistsLmsFieldsOnCreate() {
        LosProgramUpsertRequest req = new LosProgramUpsertRequest();
        req.setSourceSystem("LOS");
        req.setLosProgramId("los-1");
        req.setProgramCode("PRG-LMS-1");
        req.setProgramName("Test");
        req.setProductType(ProductType.INVOICE_DISCOUNTING);
        req.setLenderId(UUID.randomUUID());
        req.setProgramLimit(new BigDecimal("1000"));
        req.setMaxBorrowerLimit(new BigDecimal("500"));
        req.setLmsEntryIn("YES");
        req.setEncoreProductCode("INV01");

        when(programRepository.findBySourceSystemAndLosProgramId(any(), any())).thenReturn(Optional.empty());
        when(programRepository.findByProgramCode(any())).thenReturn(Optional.empty());
        when(programService.createProgram(any())).thenAnswer(inv -> {
            Program p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        var response = integrationService.upsert(req);

        assertThat(response.isCreated()).isTrue();
        verify(programService).createProgram(org.mockito.ArgumentMatchers.argThat(prog ->
                "YES".equals(prog.getLmsEntryIn()) && "INV01".equals(prog.getEncoreProductCode())));
    }

    @Test
    void upsert_mergesCustomFieldsIntoConfig() {
        LosProgramUpsertRequest req = new LosProgramUpsertRequest();
        req.setSourceSystem("LOS");
        req.setLosProgramId("los-cf");
        req.setProgramCode("PRG-CF-1");
        req.setProgramName("Test CF");
        req.setProductType(ProductType.INVOICE_DISCOUNTING);
        req.setLenderId(UUID.randomUUID());
        req.setProgramLimit(new BigDecimal("1000"));
        req.setMaxBorrowerLimit(new BigDecimal("500"));
        req.setCustomFields(java.util.Map.of(
                "maxInvoiceVintageDays", 30,
                "tenureDays", 60,
                "customRegion", "NORTH"));

        when(programRepository.findBySourceSystemAndLosProgramId(any(), any())).thenReturn(Optional.empty());
        when(programRepository.findByProgramCode(any())).thenReturn(Optional.empty());
        when(programFieldDefinitionService.validateConfigAgainstDefinitions(any(), any()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(programService.createProgram(any())).thenAnswer(inv -> {
            Program p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        integrationService.upsert(req);

        verify(programService).createProgram(org.mockito.ArgumentMatchers.argThat(prog -> {
            assertThat(prog.getMaxTenureDays()).isEqualTo(60);
            assertThat(prog.getConfig())
                    .containsEntry("maxInvoiceAgeDays", 30)
                    .containsEntry("customRegion", "NORTH");
            return true;
        }));
    }
}
