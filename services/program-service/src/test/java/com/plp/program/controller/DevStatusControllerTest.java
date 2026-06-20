package com.plp.program.controller;

import com.plp.program.dev.DevResetGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DevStatusController.class)
class DevStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DevResetGuard devResetGuard;

    @Test
    void status_reportsDevResetEnabled() throws Exception {
        when(devResetGuard.isDevResetEnabled()).thenReturn(true);
        when(devResetGuard.getActiveProfilesDisplay()).thenReturn("local");

        mockMvc.perform(get("/api/v1/dev/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devResetEnabled").value(true))
                .andExpect(jsonPath("$.profile").value("local"));
    }
}
