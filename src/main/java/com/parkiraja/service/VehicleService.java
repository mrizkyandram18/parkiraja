package com.parkiraja.service;

import com.parkiraja.dto.CheckInRequest;
import com.parkiraja.dto.CheckOutRequest;
import com.parkiraja.dto.CheckOutResponse;
import com.parkiraja.dto.VehicleResponse;
import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.entity.ParkingHistory;
import com.parkiraja.entity.ParkingReservation;
import com.parkiraja.entity.ParkingSession;
import com.parkiraja.entity.VehicleType;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.exception.DuplicateVehicleException;
import com.parkiraja.exception.ParkingLotFullException;
import com.parkiraja.exception.VehicleNotFoundException;
import com.parkiraja.repository.ParkingCapacityRepository;
import com.parkiraja.repository.ParkingHistoryRepository;
import com.parkiraja.repository.ParkingReservationRepository;
import com.parkiraja.repository.ParkingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
public class VehicleService {

    private final ParkingCapacityRepository parkingCapacityRepository;
    private final ParkingSessionRepository parkingSessionRepository;
    private final ParkingReservationRepository parkingReservationRepository;
    private final ParkingHistoryRepository parkingHistoryRepository;
    private final ParkingHistoryWriter parkingHistoryWriter;

    public VehicleService(ParkingCapacityRepository parkingCapacityRepository,
                          ParkingSessionRepository parkingSessionRepository,
                          ParkingReservationRepository parkingReservationRepository,
                          ParkingHistoryRepository parkingHistoryRepository,
                          ParkingHistoryWriter parkingHistoryWriter) {
        this.parkingCapacityRepository = parkingCapacityRepository;
        this.parkingSessionRepository = parkingSessionRepository;
        this.parkingReservationRepository = parkingReservationRepository;
        this.parkingHistoryRepository = parkingHistoryRepository;
        this.parkingHistoryWriter = parkingHistoryWriter;
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

    @Transactional
    public CheckOutResponse checkOut(CheckOutRequest request) {
        if (request == null || request.licensePlate() == null || request.licensePlate().trim().isBlank()) {
            throw new VehicleNotFoundException("Vehicle is not actively parked");
        }
        String normalizedPlate = request.licensePlate().trim().toUpperCase(Locale.ROOT);

        parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)
                .orElseThrow(() -> new CapacityNotFoundException("Parking capacity has not been configured"));

        ParkingSession session = parkingSessionRepository.findByLicensePlate(normalizedPlate)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle with license plate " + normalizedPlate + " is not actively parked"));

        LocalDateTime exitTime = LocalDateTime.now();
        long durationHours = calculateDurationHours(session.getEntryTime(), exitTime);
        long hourlyRate = session.getVehicleType().getHourlyRate();
        long totalFee = durationHours * hourlyRate;

        ParkingHistory history = new ParkingHistory(
                session.getLicensePlate(),
                session.getVehicleType(),
                session.getEntryTime(),
                exitTime,
                totalFee
        );
        parkingHistoryRepository.save(history);

        parkingSessionRepository.delete(session);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    parkingHistoryWriter.syncFromDatabase();
                }
            });
        } else {
            parkingHistoryWriter.syncFromDatabase();
        }

        return new CheckOutResponse(
                session.getLicensePlate(),
                session.getVehicleType().getCode(),
                session.getEntryTime(),
                exitTime,
                durationHours,
                hourlyRate,
                totalFee
        );
    }

    public long calculateDurationHours(LocalDateTime entryTime, LocalDateTime exitTime) {
        if (entryTime == null || exitTime == null) {
            return 1L;
        }
        long totalSeconds = Duration.between(entryTime, exitTime).getSeconds();
        if (totalSeconds <= 0) {
            return 1L;
        }
        return (totalSeconds + 3599) / 3600;
    }
}
