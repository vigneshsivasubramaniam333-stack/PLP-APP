package com.plp.program.repository;

import com.plp.program.model.entity.EmployeeSalaryData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeSalaryDataRepository extends JpaRepository<EmployeeSalaryData, UUID> {

    List<EmployeeSalaryData> findByAnchorIdAndPayPeriod(UUID anchorId, String payPeriod);

    List<EmployeeSalaryData> findByAnchorIdOrderByPeriodToDesc(UUID anchorId);

    List<EmployeeSalaryData> findByAnchorId(UUID anchorId);

    List<EmployeeSalaryData> findByBorrowerId(UUID borrowerId);

    Optional<EmployeeSalaryData> findByBorrowerIdAndPayPeriod(UUID borrowerId, String payPeriod);

    List<EmployeeSalaryData> findByProgramId(UUID programId);

    Optional<EmployeeSalaryData> findTopByBorrowerIdOrderByPayPeriodDesc(UUID borrowerId);

    /**
     * True if another slip for this borrower has a calendar range overlapping [periodFrom, periodTo] (inclusive).
     */
    @Query(
            """
            SELECT COUNT(e)
            FROM EmployeeSalaryData e
            WHERE e.borrowerId = :borrowerId
            AND (:excludeId IS NULL OR e.id <> :excludeId)
            AND e.periodFrom <= :periodTo AND e.periodTo >= :periodFrom""")
    long countBorrowerPeriodOverlap(
            @Param("borrowerId") UUID borrowerId,
            @Param("excludeId") UUID excludeId,
            @Param("periodFrom") LocalDate periodFrom,
            @Param("periodTo") LocalDate periodTo);

    @Query(value = "SELECT nextval('plp_program.salary_slip_number_seq')", nativeQuery = true)
    long nextSalarySlipNumberSequence();
}
