package com.parkiraja.service;

import com.parkiraja.dto.CheckInRequest;
import com.parkiraja.dto.VehicleResponse;
import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.entity.ParkingReservation;
import com.parkiraja.entity.ParkingSession;
import com.parkiraja.entity.VehicleType;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.exception.DuplicateVehicleException;
import com.parkiraja.exception.ParkingLotFullException;
import com.parkiraja.exception.VehicleNotFoundException;
import com.parkiraja.repository.ParkingCapacityRepository;
import com.parkiraja.repository.ParkingReservationRepository;
import com.parkiraja.repository.ParkingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class VehicleService {

    private final ParkingCapacityRepository parkingCapacityRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingReservationRepository parkingReservationRepository;

    public VehicleService(ParkingCapacityRepository parkingCapacityRepository,
                          ParkingSessionRepository parkingSessionRepository,
                          ParkingReservationRepository parkingReservationRepository) {
        this.parkingCapacityRepository = parkingCapacityRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.parkingReservationRepository = parkingReservationRepository;
    }

    @Transactional
    public VehicleResponse checkIn(CheckInRequest request) {
        String normalizedPlate = request.licensePlate().trim().toUpperCase(Locale.ROOT);
        VehicleType vehicleType = VehicleType.fromCode(request.vehicleType());

        ParkingCapacity capacity = parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)
                .orElseThrow(() -> new CapacityNotFoundException("Parking capacity has not been configured"));

        if (parkingSessionRepository.existsByLicensePlate(normalizedPlate)) {
            throw new DuplicateVehicleException("Vehicle with license plate " + normalizedPlate + " is already actively parked");
        }

        Optional<ParkingReservation> matchingReservation = parkingReservationRepository.findByLicensePlate(normalizedPlate);

        if (matchingReservation.isEmpty()) {
            long activeCount = parkingSessionRepository.count();
            long reservedCount = parkingReservationRepository.count();
            if (activeCount + reservedCount >= capacity.getTotalCapacity()) {
                throw new ParkingLotFullException("Parking lot is full");
            }
        }

        matchingReservation.ifPresent(parkingReservationRepository::delete);

        ParkingSession session = new ParkingSession(normalizedPlate, vehicleType, LocalDateTime.now());
        ParkingSession saved = parkingSessionRepository.save(session);

        return new VehicleResponse(
                saved.getLicensePlate(),
                saved.getVehicleType().getCode(),
                saved.getEntryTime()
        );
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(String licensePlate) {
        if (licensePlate == null || licensePlate.trim().isBlank()) {
            throw new VehicleNotFoundException("Vehicle is not actively parked");
        }
        String normalizedPlate = licensePlate.trim().toUpperCase(Locale.ROOT);
        return parkingSessionRepository.findByLicensePlate(normalizedPlate)
                .map(session -> new VehicleResponse(
                        session.getLicensePlate(),
                        session.getVehicleType().getCode(),
                        session.getEntryTime()
                ))
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle with license plate " + normalizedPlate + " is not actively parked"));
    }
}
