package com.parkiraja.service;

import com.parkiraja.dto.ReservationRequest;
import com.parkiraja.dto.ReservationResponse;
import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.entity.ParkingReservation;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.exception.DuplicateReservationException;
import com.parkiraja.exception.DuplicateVehicleException;
import com.parkiraja.exception.ParkingLotFullException;
import com.parkiraja.exception.ReservationNotFoundException;
import com.parkiraja.repository.ParkingCapacityRepository;
import com.parkiraja.repository.ParkingReservationRepository;
import com.parkiraja.repository.ParkingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class ReservationService {

    private final ParkingCapacityRepository parkingCapacityRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingReservationRepository parkingReservationRepository;

    public ReservationService(ParkingCapacityRepository parkingCapacityRepository,
                              ParkingSessionRepository parkingSessionRepository,
                              ParkingReservationRepository parkingReservationRepository) {
        this.parkingCapacityRepository = parkingCapacityRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.parkingReservationRepository = parkingReservationRepository;
    }

    @Transactional
    public ReservationResponse createReservation(ReservationRequest request) {
        String normalizedPlate = request.licensePlate().trim().toUpperCase(Locale.ROOT);

        ParkingCapacity capacity = parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)
                .orElseThrow(() -> new CapacityNotFoundException("Parking capacity has not been configured"));

        long activeVehicles = parkingSessionRepository.count();
        long activeReservations = parkingReservationRepository.count();

        if (activeVehicles + activeReservations >= capacity.getTotalCapacity()) {
            throw new ParkingLotFullException("Parking lot is full");
        }

        if (parkingSessionRepository.existsByLicensePlate(normalizedPlate)) {
            throw new DuplicateVehicleException("Vehicle with license plate " + normalizedPlate + " is already actively parked");
        }

        if (parkingReservationRepository.existsByLicensePlate(normalizedPlate)) {
            throw new DuplicateReservationException("Vehicle with license plate " + normalizedPlate + " already has an active reservation");
        }

        ParkingReservation reservation = new ParkingReservation(normalizedPlate, LocalDateTime.now());
        ParkingReservation saved = parkingReservationRepository.save(reservation);

        return new ReservationResponse(saved.getLicensePlate(), saved.getReservedAt());
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(String licensePlate) {
        if (licensePlate == null || licensePlate.trim().isBlank()) {
            throw new ReservationNotFoundException("Reservation not found");
        }
        String normalizedPlate = licensePlate.trim().toUpperCase(Locale.ROOT);
        return parkingReservationRepository.findByLicensePlate(normalizedPlate)
                .map(reservation -> new ReservationResponse(reservation.getLicensePlate(), reservation.getReservedAt()))
                .orElseThrow(() -> new ReservationNotFoundException("No active reservation found for license plate " + normalizedPlate));
    }
}
