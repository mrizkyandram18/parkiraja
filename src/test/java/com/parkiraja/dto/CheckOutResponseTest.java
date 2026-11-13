package com.parkiraja.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CheckOutResponseTest {

    @Test
    void shouldCreateRecord() {
        LocalDateTime entryTime = LocalDateTime.now().minusHours(2);
        LocalDateTime exitTime = LocalDateTime.now();
        CheckOutResponse response = new CheckOutResponse(
                "B 1234 XYZ",
                1,
                entryTime,
                exitTime,
                2,
                5000L,
                10000L
        );

        assertThat(response.licensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(response.vehicleType()).isEqualTo(1);
        assertThat(response.entryTime()).isEqualTo(entryTime);
        assertThat(response.exitTime()).isEqualTo(exitTime);
        assertThat(response.durationHours()).isEqualTo(2);
        assertThat(response.hourlyRate()).isEqualTo(5000L);
        assertThat(response.totalFee()).isEqualTo(10000L);
    }
}
