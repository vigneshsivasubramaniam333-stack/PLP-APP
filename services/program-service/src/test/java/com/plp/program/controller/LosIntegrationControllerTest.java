package com.plp.program.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plp.program.model.dto.integration.*;
import com.plp.program.model.enums.BorrowerProgramMappingStatus;
import com.plp.program.model.enums.ProductType;
import com.plp.program.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LosIntegrationController.class)
class LosIntegrationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    LosIntegrationService losIntegrationService;

    @MockBean
    LosAnchorIntegrationService losAnchorIntegrationService;

    @MockBean
    LosProgramIntegrationService losProgramIntegrationService;

    @MockBean
    LosSubProgramIntegrationService losSubProgramIntegrationService;

    @MockBean
    LosBorrowerIntegrationService losBorrowerIntegrationService;

    @MockBean
    LosSubProgramBorrowerLinkIntegrationService losSubProgramBorrowerLinkIntegrationService;

    @MockBean
    LosBorrowerProgramMappingIntegrationService losBorrowerProgramMappingIntegrationService;

    @Test
    void programBorrowerLink_returns400WhenPayloadIncomplete() throws Exception {
        mockMvc.perform(
                        post("/api/v1/integrations/los/program-borrower-link")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void programBorrowerLink_returns200WhenPayloadValid() throws Exception {
        UUID lenderId = UUID.randomUUID();
        UUID anchorId = UUID.randomUUID();

        when(losIntegrationService.linkProgramBorrower(any()))
                .thenReturn(
                        LosProgramBorrowerLinkResponse.builder()
                                .plpBorrowerId(UUID.randomUUID())
                                .plpProgramId(UUID.randomUUID())
                                .plpSubProgramId(UUID.randomUUID())
                                .plpBorrowerProgramMappingId(UUID.randomUUID())
                                .mappingStatus(BorrowerProgramMappingStatus.PENDING_APPROVAL)
                                .created(true)
                                .updated(false)
                                .build());

        Map<String, Object> body = aggregateLinkBody(lenderId, anchorId);

        mockMvc.perform(
                        post("/api/v1/integrations/los/program-borrower-link")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void anchors_returns400WhenPayloadIncomplete() throws Exception {
        mockMvc.perform(
                        post("/api/v1/integrations/los/anchors")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anchors_returns200WhenPayloadValid() throws Exception {
        UUID anchorUuid = UUID.randomUUID();
        when(losAnchorIntegrationService.syncAnchor(any()))
                .thenReturn(
                        LosAnchorSyncResponse.builder()
                                .plpAnchorId(anchorUuid)
                                .anchorCode("CROMPTON")
                                .created(true)
                                .updated(false)
                                .build());

        Map<String, Object> anchorBody = new LinkedHashMap<>();
        anchorBody.put("name", "Crompton Greaves Consumer Electricals Ltd");
        anchorBody.put("code", "CROMPTON");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceSystem", "LOS");
        body.put("losAnchorId", "LOS-ANCHOR-001");
        body.put("anchor", anchorBody);

        mockMvc.perform(
                        post("/api/v1/integrations/los/anchors")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void programs_returns400WhenIncomplete() throws Exception {
        mockMvc.perform(
                        post("/api/v1/integrations/los/programs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void programs_returns200WhenValid() throws Exception {
        UUID pid = UUID.randomUUID();
        when(losProgramIntegrationService.upsert(any()))
                .thenReturn(
                        LosProgramUpsertResponse.builder()
                                .plpProgramId(pid)
                                .programCode("PRG-1")
                                .created(false)
                                .updated(true)
                                .build());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceSystem", "LOS");
        body.put("programCode", "PRG-1");
        body.put("programName", "P1");
        body.put("productType", ProductType.PAY_DAY_LOAN.name());
        body.put("lenderId", UUID.randomUUID().toString());
        body.put("programLimit", new BigDecimal("1000000.00"));
        body.put("maxBorrowerLimit", new BigDecimal("100000.00"));

        mockMvc.perform(
                        post("/api/v1/integrations/los/programs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void subPrograms_returns400WhenBothProgramKeysMissing() throws Exception {
        Map<String, Object> body = minimalSubProgramPayload();
        body.remove("plpProgramId");
        // programCode also absent

        mockMvc.perform(
                        post("/api/v1/integrations/los/sub-programs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void subPrograms_returns200WhenValid() throws Exception {
        UUID sid = UUID.randomUUID();
        when(losSubProgramIntegrationService.upsert(any()))
                .thenReturn(
                        LosSubProgramUpsertResponse.builder()
                                .plpSubProgramId(sid)
                                .subProgramCode("SP-1")
                                .created(true)
                                .updated(false)
                                .build());

        Map<String, Object> body = minimalSubProgramPayload();
        body.put("plpProgramId", UUID.randomUUID().toString());

        mockMvc.perform(
                        post("/api/v1/integrations/los/sub-programs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void borrowers_returns400WhenIncomplete() throws Exception {
        mockMvc.perform(
                        post("/api/v1/integrations/los/borrowers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sourceSystem\":\"LOS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void borrowers_returns200WhenValid() throws Exception {
        when(losBorrowerIntegrationService.upsert(any()))
                .thenReturn(
                        LosBorrowerUpsertResponse.builder()
                                .plpBorrowerId(UUID.randomUUID())
                                .borrowerCode("BR-1")
                                .created(true)
                                .updated(false)
                                .build());

        Map<String, Object> borrower = new LinkedHashMap<>();
        borrower.put("name", "N1");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceSystem", "LOS");
        body.put("losBorrowerId", "LB-1");
        body.put("plpProgramId", UUID.randomUUID().toString());
        body.put("plpAnchorId", UUID.randomUUID().toString());
        body.put("borrower", borrower);

        mockMvc.perform(
                        post("/api/v1/integrations/los/borrowers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void subProgramBorrowerLinks_returns400WhenIncomplete() throws Exception {
        mockMvc.perform(
                        post("/api/v1/integrations/los/sub-program-borrower-links")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void subProgramBorrowerLinks_returns200WhenValid() throws Exception {
        when(losSubProgramBorrowerLinkIntegrationService.link(any()))
                .thenReturn(
                        LosSubProgramBorrowerLinkResponse.builder()
                                .plpSubProgramBorrowerId(UUID.randomUUID())
                                .status("PENDING_APPROVAL")
                                .created(true)
                                .updated(false)
                                .build());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceSystem", "LOS");
        body.put("subProgramId", UUID.randomUUID().toString());
        body.put("borrowerId", UUID.randomUUID().toString());
        body.put("borrowerLimit", new BigDecimal("10000.00"));

        mockMvc.perform(
                        post("/api/v1/integrations/los/sub-program-borrower-links")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void borrowerProgramMappings_returns400WhenIncomplete() throws Exception {
        mockMvc.perform(
                        post("/api/v1/integrations/los/borrower-program-mappings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void borrowerProgramMappings_returns200WhenValid() throws Exception {
        when(losBorrowerProgramMappingIntegrationService.upsert(any()))
                .thenReturn(
                        LosBorrowerProgramMappingUpsertResponse.builder()
                                .plpBorrowerProgramMappingId(UUID.randomUUID())
                                .mappingStatus(BorrowerProgramMappingStatus.PENDING_APPROVAL)
                                .created(true)
                                .updated(false)
                                .build());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceSystem", "LOS");
        body.put("losApplicationId", "APP-9");
        body.put("losBorrowerId", "B-9");
        body.put("plpBorrowerId", UUID.randomUUID().toString());
        body.put("plpProgramId", UUID.randomUUID().toString());
        body.put("plpSubProgramId", UUID.randomUUID().toString());
        body.put("approvedLimit", new BigDecimal("5000.00"));
        body.put("validFrom", LocalDate.of(2026, 4, 1).toString());
        body.put("validTo", LocalDate.of(2027, 4, 1).toString());

        mockMvc.perform(
                        post("/api/v1/integrations/los/borrower-program-mappings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    private static Map<String, Object> aggregateLinkBody(UUID lenderId, UUID anchorId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceSystem", "LOS-A");
        body.put("losApplicationId", "APP-42");
        body.put("losBorrowerId", "BOR-9");
        body.put("approvedLimit", new BigDecimal("25000.00"));
        body.put("validFrom", LocalDate.of(2026, 3, 1).toString());
        body.put("validTo", LocalDate.of(2027, 3, 1).toString());

        Map<String, Object> borrower = new LinkedHashMap<>();
        borrower.put("name", "Jane Doe");
        body.put("borrower", borrower);

        Map<String, Object> program = new LinkedHashMap<>();
        program.put("programCode", "PRG-TEST");
        program.put("programName", "Test Program");
        program.put("productType", ProductType.PAY_DAY_LOAN.name());
        program.put("lenderId", lenderId.toString());
        program.put("programLimit", new BigDecimal("900000.00"));
        program.put("maxBorrowerLimit", new BigDecimal("90000.00"));
        body.put("program", program);

        Map<String, Object> subProgram = new LinkedHashMap<>();
        subProgram.put("code", "SP-TEST");
        subProgram.put("name", "Test Sub");
        subProgram.put("anchorId", anchorId.toString());
        subProgram.put("flowType", "PAY_LOAN");
        subProgram.put("anchorRole", "EMPLOYER");
        subProgram.put("borrowerRole", "EMPLOYEE");
        subProgram.put("subProgramLimit", new BigDecimal("450000.00"));
        body.put("subProgram", subProgram);

        return body;
    }

    private static Map<String, Object> minimalSubProgramPayload() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceSystem", "LOS");
        body.put("anchorId", UUID.randomUUID().toString());
        body.put("lenderId", UUID.randomUUID().toString());
        body.put("subProgramCode", "SP-1");
        body.put("name", "Sub");
        body.put("flowType", "PAY_LOAN");
        body.put("anchorRole", "EMPLOYER");
        body.put("borrowerRole", "EMPLOYEE");
        body.put("subProgramLimit", new BigDecimal("100000.00"));
        return body;
    }
}
