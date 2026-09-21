package com.coldstore.freezer.repository;

import com.coldstore.freezer.entity.Inspection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InspectionRepository extends JpaRepository<Inspection, Long> {
    List<Inspection> findByCheckDate(LocalDate checkDate);
    Optional<Inspection> findByCellIdAndCheckDate(Long cellId, LocalDate checkDate);
    boolean existsByCellIdAndCheckDate(Long cellId, LocalDate checkDate);

    /** 该库间是否还有未闭环的异常巡检（出库硬闸口径；不join库间，库间软删后照样查得到） */
    boolean existsByCellIdAndResultAndClosed(Long cellId, String result, Integer closed);

    List<Inspection> findByResultAndClosed(String result, Integer closed);
    List<Inspection> findByCellIdAndResultAndClosed(Long cellId, String result, Integer closed);

    /** 行级锁取巡检单：两人同时闭环同一张异常单时在此串行，只许落成一次 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inspection i where i.id = :id")
    Optional<Inspection> findLockedById(@Param("id") Long id);
}
