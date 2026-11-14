package com.randomdrive.server.persistence;

import com.randomdrive.server.dto.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

/** trip_finish 테이블(주행 데이터 JSON). TripStore만 쓴다. */
public interface TripFinishJpaRepository extends JpaRepository<TripFinishEntity, String> {

    /** 상태가 [statuses]가 아닌 트립의 주행 데이터를 지운다. */
    @Modifying
    @Query("delete from TripFinishEntity f where f.tripId not in "
            + "(select t.id from TripEntity t where t.status in :statuses)")
    int deleteForTripsNotIn(@Param("statuses") Collection<TripStatus> statuses);
}
