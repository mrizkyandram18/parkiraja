package com.parkiraja.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckOutRequest(
        @NotBlank(message = "License plate must not be blank")
        String licensePlate
) {
}
