package com.coldstore.freezer.repository;

import com.coldstore.freezer.entity.Batch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {
    List<Batch> findByCellId(Long cellId);
    List<Batch> findByStatus(String status);

    /** 某库间指定状态（在库/待入）的全部批次：容量变更驳回时逐笔列明是哪些承诺撑住了下限 */
    List<Batch> findByCellIdAndStatus(Long cellId, String status);

    /** 行级锁取批次：入库 / 撤销争抢同一笔批次时在此串行，状态与预占核销只许落成一个结果 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Batch b where b.id = :id")
    Optional<Batch> findLockedById(@Param("id") Long id);

    /** 该库间已在库箱数合计 */
    @Query("select coalesce(sum(b.qty), 0) from Batch b where b.cellId = :cellId and b.status = '在库'")
    Integer sumInStockQtyByCellId(@Param("cellId") Long cellId);

    /** 该库间仍待入箱数合计 */
    @Query("select coalesce(sum(b.qty), 0) from Batch b where b.cellId = :cellId and b.status = '待入'")
    Integer sumPendingQtyByCellId(@Param("cellId") Long cellId);
}
