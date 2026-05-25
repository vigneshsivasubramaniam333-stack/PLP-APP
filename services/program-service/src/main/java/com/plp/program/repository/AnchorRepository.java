package com.plp.program.repository;

import com.plp.program.model.entity.Anchor;
import com.plp.program.model.enums.AnchorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnchorRepository extends JpaRepository<Anchor, UUID> {

    Optional<Anchor> findByAnchorCode(String anchorCode);

    List<Anchor> findByStatus(AnchorStatus status);

    Optional<Anchor> findByGstin(String gstin);

    boolean existsByGstin(String gstin);

    boolean existsByAnchorCode(String anchorCode);

    Optional<Anchor> findBySourceSystemAndLosAnchorId(String sourceSystem, String losAnchorId);
}
