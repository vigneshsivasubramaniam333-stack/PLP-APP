package com.plp.iam.service;

import com.plp.iam.model.dto.UserDto;
import com.plp.iam.model.entity.User;
import com.plp.iam.model.enums.UserRole;
import com.plp.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;

    public List<UserDto> listUsers(String roleParam, String linkedEntityTypeParam) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();

        if (roleParam != null && !roleParam.isBlank()) {
            UserRole role;
            try {
                role = UserRole.valueOf(roleParam.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid role: " + roleParam);
            }
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }

        if (linkedEntityTypeParam != null && !linkedEntityTypeParam.isBlank()) {
            String let = linkedEntityTypeParam.trim().toUpperCase();
            if ("LENDER".equals(let)) {
                spec = spec.and((root, query, cb) -> cb.or(
                        cb.isNull(root.get("linkedEntityType")),
                        cb.equal(cb.trim(root.get("linkedEntityType")), "")));
            } else if ("ANCHOR".equals(let) || "BORROWER".equals(let)) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("linkedEntityType"), let));
            } else {
                throw new IllegalArgumentException("linkedEntityType must be ANCHOR, BORROWER, or LENDER");
            }
        }

        return userRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(UserDto::fromEntity)
                .toList();
    }
}
