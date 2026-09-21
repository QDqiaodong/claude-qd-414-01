package com.coldstore.freezer.repository;

import com.coldstore.freezer.entity.Cell;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CellRepository extends JpaRepository<Cell, Long> {
    Optional<Cell> findByCode(String code);
    boolean existsByCode(String code);

    /** 行级锁取库间，预占确认/占窗开立时串行化同一库间的竞争 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cell c where c.id = :id and c.deleted = 0")
    Optional<Cell> findActiveByIdForUpdate(@Param("id") Long id);
}
