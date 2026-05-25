package com.plp.iam.dev;

import com.plp.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DevUserResetService {

    public static final String PRESERVED_ADMIN_EMAIL = "admin@plp.com";

    private final UserRepository userRepository;

    @Transactional
    public void deleteAllUsersExceptPlatformAdmin() {
        userRepository.deleteAllExceptEmail(PRESERVED_ADMIN_EMAIL);
    }
}
