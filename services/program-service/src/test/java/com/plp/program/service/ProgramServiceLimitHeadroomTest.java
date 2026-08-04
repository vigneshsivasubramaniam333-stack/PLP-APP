package com.plp.program.service;

import com.plp.program.model.entity.Program;
import com.plp.program.repository.BorrowerLimitRepository;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramServiceLimitHeadroomTest {

    @Mock
    ProgramRepository programRepository;
    @Mock
    BorrowerLimitRepository borrowerLimitRepository;
    @Mock
    SubProgramRepository subProgramRepository;
    @Mock
    ProgramFieldDefinitionService programFieldDefinitionService;

    @InjectMocks
    ProgramService programService;

    @Test
    void getProgramEnriched_aggregatesSubProgramUtilization() {
        UUID programId = UUID.randomUUID();
        Program program = Program.builder()
                .id(programId)
                .programLimit(new BigDecimal("1000000"))
                .build();

        when(programRepository.findById(programId)).thenReturn(Optional.of(program));
        when(subProgramRepository.countByProgramId(programId)).thenReturn(2L);
        when(subProgramRepository.sumUtilizedByProgramId(programId)).thenReturn(new BigDecimal("350000"));

        Program enriched = programService.getProgramEnriched(programId);

        assertThat(enriched.getUtilizedLimit()).isEqualByComparingTo("350000");
        assertThat(enriched.getAvailableLimit()).isEqualByComparingTo("650000");
        verifyNoInteractions(borrowerLimitRepository);
    }

    @Test
    void listPrograms_fallsBackToBorrowerLimitsWhenNoSubPrograms() {
        UUID programId = UUID.randomUUID();
        Program program = Program.builder()
                .id(programId)
                .programLimit(new BigDecimal("500000"))
                .build();

        when(programRepository.findAll()).thenReturn(List.of(program));
        when(subProgramRepository.countByProgramId(programId)).thenReturn(0L);
        when(borrowerLimitRepository.sumUtilizedByProgramId(programId)).thenReturn(new BigDecimal("120000"));

        List<Program> programs = programService.listPrograms();

        assertThat(programs).hasSize(1);
        assertThat(programs.get(0).getUtilizedLimit()).isEqualByComparingTo("120000");
        assertThat(programs.get(0).getAvailableLimit()).isEqualByComparingTo("380000");
        verify(borrowerLimitRepository).sumUtilizedByProgramId(programId);
    }
}
