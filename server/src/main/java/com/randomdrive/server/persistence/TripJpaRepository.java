package com.randomdrive.server.persistence;

import com.randomdrive.server.dto.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** trip 테이블. TripStore만 쓴다. */
public interface TripJpaRepository extends JpaRepository<TripEntity, String> {

    Optional<TripEntity> findByShareToken(String shareToken);

    boolean existsByShareToken(String shareToken);

    List<TripEntity> findByFinishedTrueAndStatusIn(Collection<TripStatus> statuses);
}
