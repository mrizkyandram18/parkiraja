package com.parkiraja.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CapacityRequest(
        @NotNull(message = "Total capacity is required")
        @Min(value = 1, message = "Total capacity must be greater than zero")
        Integer totalCapacity
) {
}
