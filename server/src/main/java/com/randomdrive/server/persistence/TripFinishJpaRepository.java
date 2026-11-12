package com.randomdrive.server.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** trip_finish 테이블(주행 데이터 JSON). TripStore만 쓴다. */
public interface TripFinishJpaRepository extends JpaRepository<TripFinishEntity, String> {
}
