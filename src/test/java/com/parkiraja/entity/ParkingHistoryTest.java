package com.parkiraja.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ParkingHistoryTest {

    @Test
    void shouldCreateAndMutateParkingHistory() {
        LocalDateTime entryTime = LocalDateTime.of(2026, 11, 13, 10, 0);
        LocalDateTime exitTime = LocalDateTime.of(2026, 11, 13, 12, 0);

        ParkingHistory history = new ParkingHistory("B 1234 XYZ", VehicleType.CAR, entryTime, exitTime, 10000L);
        history.setId(1L);

        assertThat(history.getId()).isEqualTo(1L);
        assertThat(history.getLicensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(history.getVehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(history.getEntryTime()).isEqualTo(entryTime);
        assertThat(history.getExitTime()).isEqualTo(exitTime);
        assertThat(history.getTotalFee()).isEqualTo(10000L);

        // Mutators and default constructor
        ParkingHistory empty = new ParkingHistory();
        empty.setId(2L);
        empty.setLicensePlate("D 5678 MOT");
        empty.setVehicleType(VehicleType.MOTORCYCLE);
        empty.setEntryTime(entryTime.plusHours(1));
        empty.setExitTime(exitTime.plusHours(1));
        empty.setTotalFee(6000L);

        assertThat(empty.getId()).isEqualTo(2L);
        assertThat(empty.getLicensePlate()).isEqualTo("D 5678 MOT");
        assertThat(empty.getVehicleType()).isEqualTo(VehicleType.MOTORCYCLE);
        assertThat(empty.getEntryTime()).isEqualTo(entryTime.plusHours(1));
        assertThat(empty.getExitTime()).isEqualTo(exitTime.plusHours(1));
        assertThat(empty.getTotalFee()).isEqualTo(6000L);
    }
}
