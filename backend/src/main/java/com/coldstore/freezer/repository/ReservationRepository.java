package com.coldstore.freezer.repository;

import com.coldstore.freezer.entity.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findAllByOrderByIdDesc();

    List<Reservation> findByCellIdOrderByIdDesc(Long cellId);

    /** 某库间已确认尚未核销的预占逐笔清单：容量变更驳回时点明是哪一笔承诺顶到了新容量之上 */
    List<Reservation> findByCellIdAndStatusOrderByIdAsc(Long cellId, String status);

    /** 该库间已确认尚未核销的预占箱数合计 */
    @Query("select coalesce(sum(r.qty), 0) from Reservation r " +
            "where r.cellId = :cellId and r.status = '已确认'")
    Integer sumConfirmedQtyByCellId(@Param("cellId") Long cellId);

    /**
     * 可用于顶一笔待入批次的预占：已确认、未核销，库间/货品一致、箱数完全对得上。
     * 同一库间同一货品可能有多张，按确认先后（确认时刻、id）取最早一张。
     */
    @Query("select r from Reservation r where r.cellId = :cellId and r.cargo = :cargo " +
            "and r.status = '已确认' and r.qty = :qty " +
            "order by r.confirmedAt asc, r.id asc")
    List<Reservation> findUsable(@Param("cellId") Long cellId,
                                 @Param("cargo") String cargo,
                                 @Param("qty") Integer qty);

    /** 行级锁取预占：入库核销的串行点，防止一张预占顶两笔批次 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findLockedById(@Param("id") Long id);
}
