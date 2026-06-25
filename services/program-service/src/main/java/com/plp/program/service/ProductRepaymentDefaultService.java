package com.plp.program.service;

import com.plp.program.model.dto.ProductRepaymentDefaultDto;
import com.plp.program.model.dto.ProductRepaymentDefaultUpdateDto;
import com.plp.program.model.entity.ProductRepaymentDefault;
import com.plp.program.model.enums.ProductType;
import com.plp.program.repository.ProductRepaymentDefaultRepository;
import com.plp.program.validation.RepaymentMechanismValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductRepaymentDefaultService {

    private final ProductRepaymentDefaultRepository repository;

    @Transactional(readOnly = true)
    public List<ProductRepaymentDefaultDto> listAll() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(row -> row.getProductType().name()))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductRepaymentDefaultDto get(ProductType productType) {
        return toDto(findOrDefault(productType));
    }

  /** Resolved mechanism for runtime (disabled or missing row → SMART_COLLECT). */
    @Transactional(readOnly = true)
    public String resolveMechanism(ProductType productType) {
        ProductRepaymentDefault row = repository.findById(productType).orElse(null);
        if (row == null || !Boolean.TRUE.equals(row.getEnabled())) {
            return "SMART_COLLECT";
        }
        return row.getRepaymentMechanism() != null ? row.getRepaymentMechanism() : "SMART_COLLECT";
    }

    @Transactional
    public ProductRepaymentDefaultDto update(
            ProductType productType, ProductRepaymentDefaultUpdateDto dto, String updatedBy) {
        ProductRepaymentDefault row = findOrDefault(productType);
        if (dto.getRepaymentMechanism() != null) {
            row.setRepaymentMechanism(RepaymentMechanismValidator.normalize(dto.getRepaymentMechanism()));
        }
        if (dto.getPgProviderCode() != null) {
            String code = dto.getPgProviderCode().isBlank() ? null : dto.getPgProviderCode().trim().toUpperCase();
            row.setPgProviderCode(code);
        }
        if (dto.getEnabled() != null) {
            row.setEnabled(dto.getEnabled());
        }
        if (updatedBy != null && !updatedBy.isBlank()) {
            row.setUpdatedBy(updatedBy.trim());
        }
        return toDto(repository.save(row));
    }

    private ProductRepaymentDefault findOrDefault(ProductType productType) {
        return repository.findById(productType).orElseGet(() -> ProductRepaymentDefault.builder()
                .productType(productType)
                .repaymentMechanism("SMART_COLLECT")
                .enabled(true)
                .build());
    }

    private ProductRepaymentDefaultDto toDto(ProductRepaymentDefault row) {
        return ProductRepaymentDefaultDto.builder()
                .productType(row.getProductType())
                .repaymentMechanism(row.getRepaymentMechanism())
                .pgProviderCode(row.getPgProviderCode())
                .enabled(row.getEnabled())
                .updatedAt(row.getUpdatedAt())
                .updatedBy(row.getUpdatedBy())
                .build();
    }
}
