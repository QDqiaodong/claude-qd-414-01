package com.coldstore.freezer.repository;

import com.coldstore.freezer.entity.DefrostWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DefrostWindowRepository extends JpaRepository<DefrostWindow, Long> {

    List<DefrostWindow> findByCellIdOrderByStartAtAsc(Long cellId);

    /**
     * 同一库间内与 [startAt, endAt) 半开区间重叠的占窗。
     * 边界相接（一扇 endAt = 另一扇 startAt）不算重叠，允许前后脚续开。
     */
    @Query("select w from DefrostWindow w where w.cellId = :cellId " +
            "and w.startAt < :endAt and w.endAt > :startAt " +
            "order by w.startAt asc")
    List<DefrostWindow> findOverlapping(@Param("cellId") Long cellId,
                                        @Param("startAt") LocalDateTime startAt,
                                        @Param("endAt") LocalDateTime endAt);

    /** 该库间当前时刻正在进行中的占窗 */
    @Query("select w from DefrostWindow w where w.cellId = :cellId " +
            "and w.startAt <= :now and w.endAt > :now")
    List<DefrostWindow> findOngoing(@Param("cellId") Long cellId,
                                    @Param("now") LocalDateTime now);

    /**
     * 在 (since, now] 这段时间内已经开始（含进行中、已结束）的占窗：
     * startAt &lt; now 且 endAt &gt; since。
     * 用于判定一张预占开立/确认之后，库间是否已经历过化霜；
     * 尚未开始的未来占窗不算在内，不拦当前确认。
     */
    @Query("select w from DefrostWindow w where w.cellId = :cellId " +
            "and w.startAt < :now and w.endAt > :since")
    List<DefrostWindow> findOccurredSince(@Param("cellId") Long cellId,
                                          @Param("since") LocalDateTime since,
                                          @Param("now") LocalDateTime now);
}
