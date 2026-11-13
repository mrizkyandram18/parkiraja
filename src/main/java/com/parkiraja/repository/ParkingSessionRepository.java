package com.parkiraja.repository;

import com.parkiraja.entity.ParkingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, Long> {

    Optional<ParkingSession> findByLicensePlate(String licensePlate);

    boolean existsByLicensePlate(String licensePlate);
}
