package com.moveinsync.distance.repository;

import com.moveinsync.distance.entity.TripEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<TripEntity, Long> {
    Optional<TripEntity> findByTripIdAndTenantIdAndVersion(String tripId, String tenantId, int version);

    Optional<TripEntity> findTopByTripIdAndTenantIdOrderByVersionDesc(String tripId, String tenantId);

    List<TripEntity> findAllByTenantId(String tenantId);

    List<TripEntity> findAllByStatusAndTenantId(String status, String tenantId);

    boolean existsByTripIdAndTenantId(String tripId, String tenantId);
}
