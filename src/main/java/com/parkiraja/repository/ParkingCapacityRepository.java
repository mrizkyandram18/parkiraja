package com.parkiraja.repository;

import com.parkiraja.entity.ParkingCapacity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ParkingCapacityRepository extends JpaRepository<ParkingCapacity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ParkingCapacity c WHERE c.id = :id")
    Optional<ParkingCapacity> findByIdForUpdate(@Param("id") Long id);
}
