package com.parkiraja.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ParkingReservationTest {

    @Test
    void shouldTestConstructorsAndGettersSetters() {
        ParkingReservation reservation = new ParkingReservation();
        LocalDateTime now = LocalDateTime.now();

        reservation.setId(5L);
        reservation.setLicensePlate("B 5678 ABC");
        reservation.setReservedAt(now);

        assertThat(reservation.getId()).isEqualTo(5L);
        assertThat(reservation.getLicensePlate()).isEqualTo("B 5678 ABC");
        assertThat(reservation.getReservedAt()).isEqualTo(now);

        ParkingReservation parameterized = new ParkingReservation("D 1234 XYZ", now);
        assertThat(parameterized.getLicensePlate()).isEqualTo("D 1234 XYZ");
        assertThat(parameterized.getReservedAt()).isEqualTo(now);
    }
}
