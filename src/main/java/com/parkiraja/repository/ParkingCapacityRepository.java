package com.parkiraja.repository;

import com.parkiraja.entity.ParkingCapacity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingCapacityRepository extends JpaRepository<ParkingCapacity, Long> {
}
