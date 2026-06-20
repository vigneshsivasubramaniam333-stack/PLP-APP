package com.plp.iam.dev;

import com.plp.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DevUserResetService {

    private final UserRepository userRepository;
    private final DevPreservedUserEmails devPreservedUserEmails;

    @Transactional
    public int deleteAllUsersExceptPreserved() {
        var preserved = devPreservedUserEmails.preservedEmailsLower();
        if (preserved.isEmpty()) {
            return 0;
        }
        return userRepository.deleteAllExceptEmails(preserved);
    }
}
