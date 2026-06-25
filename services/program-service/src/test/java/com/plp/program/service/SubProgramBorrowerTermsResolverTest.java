package com.plp.program.service;

import com.plp.program.model.entity.Program;
import com.plp.program.model.entity.SubProgram;
import com.plp.program.model.entity.SubProgramBorrower;
import com.plp.program.model.enums.PaymentMethodMode;
import com.plp.program.model.enums.ProductType;
import com.plp.program.repository.ProgramRepository;
import com.plp.program.repository.SubProgramBorrowerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubProgramBorrowerTermsResolverTest {

    @Mock
    SubProgramService subProgramService;
    @Mock
    SubProgramBorrowerRepository subProgramBorrowerRepository;
    @Mock
    ProgramRepository programRepository;
    @Mock
    ProductRepaymentDefaultService repaymentDefaultService;

    @InjectMocks
    SubProgramBorrowerTermsResolver resolver;

    @Test
    void customMode_usesEnrollmentPaymentMethod() {
        UUID subProgramId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();

        SubProgram subProgram = SubProgram.builder().id(subProgramId).programId(programId).build();
        SubProgramBorrower membership = SubProgramBorrower.builder()
                .paymentMethodMode(PaymentMethodMode.CUSTOM)
                .paymentMethod("PAYU_PG")
                .build();
        Program program = Program.builder().id(programId).productType(ProductType.INVOICE_DISCOUNTING).build();

        when(subProgramService.getSubProgram(subProgramId)).thenReturn(subProgram);
        when(subProgramBorrowerRepository.findBySubProgramIdAndBorrowerId(subProgramId, borrowerId))
                .thenReturn(Optional.of(membership));
        when(programRepository.findById(programId)).thenReturn(Optional.of(program));

        var terms = resolver.resolveEffectiveTerms(subProgramId, borrowerId);

        assertThat(terms.getPaymentMethod()).isEqualTo("PAYU_PG");
        assertThat(terms.getPaymentMethodMode()).isEqualTo("CUSTOM");
        verifyNoInteractions(repaymentDefaultService);
    }

    @Test
    void globalMode_usesProductRepaymentDefault() {
        UUID subProgramId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();

        SubProgram subProgram = SubProgram.builder().id(subProgramId).programId(programId).build();
        SubProgramBorrower membership = SubProgramBorrower.builder()
                .paymentMethodMode(PaymentMethodMode.GLOBAL)
                .paymentMethod("SMART_COLLECT")
                .build();
        Program program = Program.builder().id(programId).productType(ProductType.INVOICE_DISCOUNTING).build();

        when(subProgramService.getSubProgram(subProgramId)).thenReturn(subProgram);
        when(subProgramBorrowerRepository.findBySubProgramIdAndBorrowerId(subProgramId, borrowerId))
                .thenReturn(Optional.of(membership));
        when(programRepository.findById(programId)).thenReturn(Optional.of(program));
        when(repaymentDefaultService.resolveMechanism(ProductType.INVOICE_DISCOUNTING)).thenReturn("PAYU_PG");

        var terms = resolver.resolveEffectiveTerms(subProgramId, borrowerId);

        assertThat(terms.getPaymentMethod()).isEqualTo("PAYU_PG");
        assertThat(terms.getPaymentMethodMode()).isEqualTo("GLOBAL");
        verify(repaymentDefaultService).resolveMechanism(ProductType.INVOICE_DISCOUNTING);
    }

    @Test
    void globalMode_missingDefault_fallsBackToSmartCollect() {
        UUID subProgramId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();

        SubProgram subProgram = SubProgram.builder().id(subProgramId).programId(programId).build();
        SubProgramBorrower membership = SubProgramBorrower.builder()
                .paymentMethodMode(PaymentMethodMode.GLOBAL)
                .build();
        Program program = Program.builder().id(programId).productType(ProductType.PAY_DAY_LOAN).build();

        when(subProgramService.getSubProgram(subProgramId)).thenReturn(subProgram);
        when(subProgramBorrowerRepository.findBySubProgramIdAndBorrowerId(subProgramId, borrowerId))
                .thenReturn(Optional.of(membership));
        when(programRepository.findById(programId)).thenReturn(Optional.of(program));
        when(repaymentDefaultService.resolveMechanism(ProductType.PAY_DAY_LOAN)).thenReturn("SMART_COLLECT");

        var terms = resolver.resolveEffectiveTerms(subProgramId, borrowerId);

        assertThat(terms.getPaymentMethod()).isEqualTo("SMART_COLLECT");
    }

    @Test
    void customMode_nullPaymentMethod_fallsBackToSmartCollect() {
        UUID subProgramId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();

        SubProgram subProgram = SubProgram.builder().id(subProgramId).programId(programId).build();
        SubProgramBorrower membership = SubProgramBorrower.builder()
                .paymentMethodMode(PaymentMethodMode.CUSTOM)
                .paymentMethod(null)
                .build();
        Program program = Program.builder().id(programId).productType(ProductType.INVOICE_DISCOUNTING).build();

        when(subProgramService.getSubProgram(subProgramId)).thenReturn(subProgram);
        when(subProgramBorrowerRepository.findBySubProgramIdAndBorrowerId(subProgramId, borrowerId))
                .thenReturn(Optional.of(membership));
        when(programRepository.findById(programId)).thenReturn(Optional.of(program));

        var terms = resolver.resolveEffectiveTerms(subProgramId, borrowerId);

        assertThat(terms.getPaymentMethod()).isEqualTo("SMART_COLLECT");
        verifyNoInteractions(repaymentDefaultService);
    }

    @Test
    void globalPayDayLoan_customSmartCollectOverride_notUsedWhenGlobal() {
        UUID subProgramId = UUID.randomUUID();
        UUID borrowerId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();

        SubProgram subProgram = SubProgram.builder().id(subProgramId).programId(programId).build();
        SubProgramBorrower membership = SubProgramBorrower.builder()
                .paymentMethodMode(PaymentMethodMode.GLOBAL)
                .paymentMethod("SMART_COLLECT")
                .build();
        Program program = Program.builder().id(programId).productType(ProductType.INVOICE_DISCOUNTING).build();

        when(subProgramService.getSubProgram(subProgramId)).thenReturn(subProgram);
        when(subProgramBorrowerRepository.findBySubProgramIdAndBorrowerId(subProgramId, borrowerId))
                .thenReturn(Optional.of(membership));
        when(programRepository.findById(programId)).thenReturn(Optional.of(program));
        when(repaymentDefaultService.resolveMechanism(ProductType.INVOICE_DISCOUNTING)).thenReturn("PAYU_PG");

        var terms = resolver.resolveEffectiveTerms(subProgramId, borrowerId);

        assertThat(terms.getPaymentMethod()).isEqualTo("PAYU_PG");
    }
}
