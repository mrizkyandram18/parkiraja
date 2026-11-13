package com.parkiraja.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationResponseTest {

    @Test
    void shouldCreateRecord() {
        LocalDateTime now = LocalDateTime.now();
        ReservationResponse response = new ReservationResponse("B 5678 ABC", now);
        assertThat(response.licensePlate()).isEqualTo("B 5678 ABC");
        assertThat(response.reservedAt()).isEqualTo(now);
    }
}
