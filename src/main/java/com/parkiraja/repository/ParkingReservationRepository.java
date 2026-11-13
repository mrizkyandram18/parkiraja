package com.parkiraja.repository;

import com.parkiraja.entity.ParkingReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParkingReservationRepository extends JpaRepository<ParkingReservation, Long> {

    Optional<ParkingReservation> findByLicensePlate(String licensePlate);

    boolean existsByLicensePlate(String licensePlate);
}
