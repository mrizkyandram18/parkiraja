package com.parkiraja.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VehicleTypeTest {

    @Test
    void shouldMapCodesCorrectly() {
        assertThat(VehicleType.CAR.getCode()).isEqualTo(1);
        assertThat(VehicleType.MOTORCYCLE.getCode()).isEqualTo(2);

        assertThat(VehicleType.CAR.getHourlyRate()).isEqualTo(5000L);
        assertThat(VehicleType.MOTORCYCLE.getHourlyRate()).isEqualTo(3000L);

        assertThat(VehicleType.fromCode(1)).isEqualTo(VehicleType.CAR);
        assertThat(VehicleType.fromCode(2)).isEqualTo(VehicleType.MOTORCYCLE);
    }

    @Test
    void shouldThrowExceptionForInvalidCode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> VehicleType.fromCode(99)
        );
        assertThat(exception.getMessage()).contains("Invalid vehicle type code: 99");
    }
}
