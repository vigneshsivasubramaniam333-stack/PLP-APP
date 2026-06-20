package com.plp.iam.dev;

import com.plp.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevUserResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DevPreservedUserEmails devPreservedUserEmails;

    private DevUserResetService service;

    @BeforeEach
    void setUp() {
        service = new DevUserResetService(userRepository, devPreservedUserEmails);
    }

    @Test
    void deleteAllUsersExceptPreserved_deletesNonSeedUsers() {
        List<String> preserved = List.of("admin@credinnov.com", "borrower@credinnov.com");
        when(devPreservedUserEmails.preservedEmailsLower()).thenReturn(preserved);
        when(userRepository.deleteAllExceptEmails(preserved)).thenReturn(3);

        int removed = service.deleteAllUsersExceptPreserved();

        assertThat(removed).isEqualTo(3);
        verify(userRepository).deleteAllExceptEmails(preserved);
    }

    @Test
    void deleteAllUsersExceptPreserved_whenNoPreserved_returnsZero() {
        when(devPreservedUserEmails.preservedEmailsLower()).thenReturn(List.of());

        assertThat(service.deleteAllUsersExceptPreserved()).isZero();
    }
}
