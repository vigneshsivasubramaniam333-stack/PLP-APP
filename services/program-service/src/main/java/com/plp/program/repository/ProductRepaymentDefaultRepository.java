package com.plp.program.repository;

import com.plp.program.model.entity.ProductRepaymentDefault;
import com.plp.program.model.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepaymentDefaultRepository extends JpaRepository<ProductRepaymentDefault, ProductType> {
}
