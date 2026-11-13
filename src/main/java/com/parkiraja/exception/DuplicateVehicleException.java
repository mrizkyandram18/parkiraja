package com.parkiraja.exception;

public class DuplicateVehicleException extends RuntimeException {

    public DuplicateVehicleException(String message) {
        super(message);
    }
}
