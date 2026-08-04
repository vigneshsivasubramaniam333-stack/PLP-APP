package com.plp.program.service;

import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.ProgramApprovalConfig;
import com.plp.program.model.enums.ProgramStatus;
import com.plp.program.repository.ProgramApprovalConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramApprovalServiceTest {

    @Mock
    ProgramService programService;

    @Mock
    ProgramApprovalConfigRepository configRepository;

    @InjectMocks
    ProgramApprovalService service;

    private final UUID programId = UUID.randomUUID();
    private final String l1Roles = "CREDIT_ANALYST";
    private final String l2Roles = "CREDIT_MANAGER";

    @BeforeEach
    void setUpConfig() {
        when(configRepository.findAll()).thenReturn(List.of(ProgramApprovalConfig.builder()
                .l1Role("CREDIT_ANALYST")
                .l2Role("CREDIT_MANAGER")
                .enabled(true)
                .build()));
    }

    @Test
    void l2SendBackReturnsProgramToDraftForL1Rework() {
        Program program = Program.builder()
                .id(programId)
                .programCode("PRG-1")
                .status(ProgramStatus.PENDING_L2)
                .build();
        when(programService.getProgram(programId)).thenReturn(program);
        when(programService.saveProgram(program)).thenReturn(program);

        Program updated = service.sendBack(programId, "Fix limit", l2Roles, "l2-user");

        assertThat(updated.getStatus()).isEqualTo(ProgramStatus.DRAFT);
        assertThat(updated.getApprovalRemarks()).isEqualTo("Fix limit");
        assertThat(updated.getSubmittedAt()).isNull();
        assertThat(updated.getSubmittedBy()).isNull();
    }

    @Test
    void l1SendBackToRmIsNotAllowedFromPendingL2() {
        Program program = Program.builder()
                .id(programId)
                .programCode("PRG-2")
                .status(ProgramStatus.PENDING_L2)
                .build();
        when(programService.getProgram(programId)).thenReturn(program);

        assertThatThrownBy(() -> service.sendBackToRm(programId, "Back to RM", l1Roles, "l1-user"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only L2 approver");
    }

    @Test
    void l2SendBackToRmFromPendingL2SetsSentBack() {
        Program program = Program.builder()
                .id(programId)
                .programCode("PRG-3")
                .status(ProgramStatus.PENDING_L2)
                .build();
        when(programService.getProgram(programId)).thenReturn(program);
        when(programService.saveProgram(program)).thenReturn(program);

        Program updated = service.sendBackToRm(programId, "Revise limit with RM", l2Roles, "l2-user");

        assertThat(updated.getStatus()).isEqualTo(ProgramStatus.SENT_BACK);
        assertThat(updated.getApprovalRemarks()).isEqualTo("Revise limit with RM");
        assertThat(updated.getSubmittedAt()).isNull();
        assertThat(updated.getSubmittedBy()).isNull();
    }

    @Test
    void l2SendBackToRmRequiresRemarks() {
        Program program = Program.builder()
                .id(programId)
                .programCode("PRG-4")
                .status(ProgramStatus.PENDING_L2)
                .build();
        when(programService.getProgram(programId)).thenReturn(program);

        assertThatThrownBy(() -> service.sendBackToRm(programId, "  ", l2Roles, "l2-user"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Remarks are required");
    }

    @Test
    void l1SendBackToRmFromDraftStillWorks() {
        Program program = Program.builder()
                .id(programId)
                .programCode("PRG-5")
                .status(ProgramStatus.DRAFT)
                .build();
        when(programService.getProgram(programId)).thenReturn(program);
        when(programService.saveProgram(program)).thenReturn(program);

        Program updated = service.sendBackToRm(programId, null, l1Roles, "l1-user");

        assertThat(updated.getStatus()).isEqualTo(ProgramStatus.SENT_BACK);
    }
}
