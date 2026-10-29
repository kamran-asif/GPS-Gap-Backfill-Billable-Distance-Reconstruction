package com.moveinsync.distance.repository;

import com.moveinsync.distance.entity.TripAdjustmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripAdjustmentRepository extends JpaRepository<TripAdjustmentEntity, Long> {
    List<TripAdjustmentEntity> findAllByTripIdAndTenantIdOrderByCreatedAtDesc(String tripId, String tenantId);

    List<TripAdjustmentEntity> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);
}
