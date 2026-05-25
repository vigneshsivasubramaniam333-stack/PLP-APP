package com.plp.program.service;

import com.plp.program.model.dto.integration.LosAnchorSyncRequest;
import com.plp.program.model.dto.integration.LosAnchorSyncResponse;
import com.plp.program.model.entity.Anchor;
import com.plp.program.model.enums.AnchorStatus;
import com.plp.program.repository.AnchorRepository;
import com.plp.program.service.audit.LosSyncAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LosAnchorIntegrationServiceTest {

    @Mock
    AnchorRepository anchorRepository;

    @Mock
    LosSyncAuditService losSyncAuditService;

    @Mock
    com.plp.program.integration.iam.IamUserProvisioner iamUserProvisioner;

    @InjectMocks
    LosAnchorIntegrationService losAnchorIntegrationService;

    @Test
    void sync_returnsExisting_byLosIdentity_updatesAndCreatedFalse() {
        UUID id = UUID.randomUUID();
        Anchor existing =
                Anchor.builder()
                        .id(id)
                        .anchorCode("CROMPTON")
                        .entityName("Old Name")
                        .entityType("CORPORATE")
                        .status(AnchorStatus.DRAFT)
                        .sourceSystem("LOS")
                        .losAnchorId("LOS-ANCHOR-001")
                        .build();

        when(anchorRepository.findBySourceSystemAndLosAnchorId("LOS", "LOS-ANCHOR-001"))
                .thenReturn(Optional.of(existing));

        LosAnchorSyncRequest req = sampleRequest();
        LosAnchorSyncResponse resp = losAnchorIntegrationService.syncAnchor(req);

        assertThat(resp.isCreated()).isFalse();
        assertThat(resp.getPlpAnchorId()).isEqualTo(id);
        assertThat(resp.getAnchorCode()).isEqualTo("CROMPTON");
        assertThat(existing.getEntityName()).isEqualTo("Crompton Greaves Consumer Electricals Ltd");

        verify(anchorRepository).save(existing);
    }

    @Test
    void sync_linksExistingAnchor_byCode_whenLosColumnsUnset() {
        UUID id = UUID.randomUUID();
        Anchor byCode =
                Anchor.builder()
                        .id(id)
                        .anchorCode("CROMPTON")
                        .entityName("Existing")
                        .entityType("CORPORATE")
                        .status(AnchorStatus.DRAFT)
                        .build();

        when(anchorRepository.findBySourceSystemAndLosAnchorId("LOS", "LOS-ANCHOR-001"))
                .thenReturn(Optional.empty());
        when(anchorRepository.findByAnchorCode("CROMPTON")).thenReturn(Optional.of(byCode));

        LosAnchorSyncResponse resp = losAnchorIntegrationService.syncAnchor(sampleRequest());

        assertThat(resp.isCreated()).isFalse();
        assertThat(byCode.getSourceSystem()).isEqualTo("LOS");
        assertThat(byCode.getLosAnchorId()).isEqualTo("LOS-ANCHOR-001");
        verify(anchorRepository).save(byCode);
    }

    @Test
    void sync_createsNew_whenNoMatches() {
        when(anchorRepository.findBySourceSystemAndLosAnchorId("LOS", "LOS-ANCHOR-001"))
                .thenReturn(Optional.empty());
        when(anchorRepository.findByAnchorCode("CROMPTON")).thenReturn(Optional.empty());

        when(anchorRepository.save(any(Anchor.class)))
                .thenAnswer(
                        inv -> {
                            Anchor a = inv.getArgument(0);
                            if (a.getId() == null) {
                                a.setId(UUID.randomUUID());
                            }
                            return a;
                        });

        LosAnchorSyncResponse resp = losAnchorIntegrationService.syncAnchor(sampleRequest());

        assertThat(resp.isCreated()).isTrue();
        assertThat(resp.getPlpAnchorId()).isNotNull();
        ArgumentCaptor<Anchor> captor = ArgumentCaptor.forClass(Anchor.class);
        verify(anchorRepository).save(captor.capture());
        Anchor captured = captor.getValue();
        assertThat(captured.getAnchorCode()).isEqualTo("CROMPTON");
        assertThat(captured.getEntityType()).isEqualTo("CORPORATE");
        assertThat(captured.getStatus()).isEqualTo(AnchorStatus.DRAFT);
        assertThat(captured.getContactEmail()).isEqualTo("finance@crompton.co.in");
        assertThat(captured.getContactPhone()).isEqualTo("9876543210");
        assertThat(captured.getAddress()).containsEntry("text", "Mumbai, Maharashtra");
    }

    @Test
    void sync_throwsWhenLosIdentityRequestsDifferentImmutableCode() {
        UUID id = UUID.randomUUID();
        Anchor existing =
                Anchor.builder()
                        .id(id)
                        .anchorCode("CROMPTON")
                        .entityName("Old")
                        .entityType("CORPORATE")
                        .status(AnchorStatus.DRAFT)
                        .sourceSystem("LOS")
                        .losAnchorId("LOS-ANCHOR-001")
                        .build();

        when(anchorRepository.findBySourceSystemAndLosAnchorId("LOS", "LOS-ANCHOR-001"))
                .thenReturn(Optional.of(existing));

        LosAnchorSyncRequest req = sampleRequest();
        req.getAnchor().setCode("OTHER");

        assertThatThrownBy(() -> losAnchorIntegrationService.syncAnchor(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("anchorCode cannot be changed");

        verify(anchorRepository, never()).save(any());
    }

    @Test
    void sync_throwsWhenCodeLinkedToDifferentLosIdentity() {
        UUID id = UUID.randomUUID();
        Anchor byCode =
                Anchor.builder()
                        .id(id)
                        .anchorCode("CROMPTON")
                        .entityName("Existing")
                        .entityType("CORPORATE")
                        .status(AnchorStatus.DRAFT)
                        .sourceSystem("LOS")
                        .losAnchorId("OTHER-LOS-ID")
                        .build();

        when(anchorRepository.findBySourceSystemAndLosAnchorId("LOS", "LOS-ANCHOR-001"))
                .thenReturn(Optional.empty());
        when(anchorRepository.findByAnchorCode("CROMPTON")).thenReturn(Optional.of(byCode));

        LosAnchorSyncRequest req = sampleRequest();

        assertThatThrownBy(() -> losAnchorIntegrationService.syncAnchor(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already linked to a different LOS identity");

        verify(anchorRepository, never()).save(any());
    }

    private static LosAnchorSyncRequest sampleRequest() {
        LosAnchorSyncRequest req = new LosAnchorSyncRequest();
        req.setSourceSystem("LOS");
        req.setLosAnchorId("LOS-ANCHOR-001");
        LosAnchorSyncRequest.LosAnchorPayload a = new LosAnchorSyncRequest.LosAnchorPayload();
        a.setName("Crompton Greaves Consumer Electricals Ltd");
        a.setCode("CROMPTON");
        a.setPan("ABCDE1234F");
        a.setGstin("27ABCDE1234F1Z5");
        a.setEmail("finance@crompton.co.in");
        a.setMobile("9876543210");
        a.setAddress("Mumbai, Maharashtra");
        req.setAnchor(a);
        return req;
    }
}
