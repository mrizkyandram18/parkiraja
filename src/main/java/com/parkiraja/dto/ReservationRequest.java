package com.parkiraja.dto;

import jakarta.validation.constraints.NotBlank;

public record ReservationRequest(
        @NotBlank(message = "License plate must not be blank")
        String licensePlate
) {
}
