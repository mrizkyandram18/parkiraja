package com.parkiraja.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ParkingSessionTest {

    @Test
    void shouldTestConstructorsAndGettersSetters() {
        ParkingSession session = new ParkingSession();
        LocalDateTime now = LocalDateTime.now();

        session.setId(10L);
        session.setLicensePlate("B 1234 XYZ");
        session.setVehicleType(VehicleType.CAR);
        session.setEntryTime(now);

        assertThat(session.getId()).isEqualTo(10L);
        assertThat(session.getLicensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(session.getVehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(session.getEntryTime()).isEqualTo(now);

        ParkingSession parameterized = new ParkingSession("D 5678 ABC", VehicleType.MOTORCYCLE, now);
        assertThat(parameterized.getLicensePlate()).isEqualTo("D 5678 ABC");
        assertThat(parameterized.getVehicleType()).isEqualTo(VehicleType.MOTORCYCLE);
        assertThat(parameterized.getEntryTime()).isEqualTo(now);
    }
}
