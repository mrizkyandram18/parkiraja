package com.parkiraja.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CheckInRequest(
        @NotBlank(message = "License plate must not be blank")
        String licensePlate,

        @NotNull(message = "Vehicle type is required")
        @Min(value = 1, message = "Vehicle type must be 1 (Car) or 2 (Motorcycle)")
        @Max(value = 2, message = "Vehicle type must be 1 (Car) or 2 (Motorcycle)")
        Integer vehicleType
) {
}
