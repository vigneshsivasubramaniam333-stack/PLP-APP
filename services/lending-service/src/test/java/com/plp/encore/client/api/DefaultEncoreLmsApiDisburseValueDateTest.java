package com.plp.encore.client.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plp.encore.client.config.EncoreClientProperties;
import com.plp.encore.client.http.EncoreHttpTransport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultEncoreLmsApiDisburseValueDateTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void resolveDisburseValueDateMillis_preservesRequestedTreasuryDate() {
        EncoreClientProperties properties = new EncoreClientProperties();
        EncoreHttpTransport transport = mock(EncoreHttpTransport.class);
        when(transport.isConfigured()).thenReturn(true);
        when(transport.httpGet(eq(properties.getApi().getFindWorkingDate()), any())).thenReturn("2026-07-20");

        DefaultEncoreLmsApi api = new DefaultEncoreLmsApi(properties, transport, new ObjectMapper());
        long millis = api.resolveDisburseValueDateMillis(LocalDate.of(2026, 7, 20));

        assertEquals(DefaultEncoreLmsApi.toEncoreCalendarDateMillis(LocalDate.of(2026, 7, 20)), millis);
    }

    @Test
    void resolveDisburseValueDateMillis_backDatedTreasuryDate_usesRequestedDateNotBankDate() {
        EncoreClientProperties properties = new EncoreClientProperties();
        EncoreHttpTransport transport = mock(EncoreHttpTransport.class);
        when(transport.isConfigured()).thenReturn(true);
        when(transport.httpGet(eq(properties.getApi().getFindWorkingDate()), any())).thenReturn("2026-07-20");

        DefaultEncoreLmsApi api = new DefaultEncoreLmsApi(properties, transport, new ObjectMapper());

        long millis = api.resolveDisburseValueDateMillis(LocalDate.of(2026, 5, 11));
        long expected = DefaultEncoreLmsApi.toEncoreCalendarDateMillis(LocalDate.of(2026, 5, 11));
        assertEquals(expected, millis);
    }

    @Test
    void toEncoreCalendarDateMillis_usesNoonIstNotMidnight() {
        long millis = DefaultEncoreLmsApi.toEncoreCalendarDateMillis(LocalDate.of(2026, 7, 20));
        long midnightIst = LocalDate.of(2026, 7, 20).atStartOfDay(IST).toInstant().toEpochMilli();
        assertEquals(
                LocalDate.of(2026, 7, 20).atTime(12, 0).atZone(IST).toInstant().toEpochMilli(),
                millis);
        org.junit.jupiter.api.Assertions.assertTrue(millis > midnightIst);
    }
}
