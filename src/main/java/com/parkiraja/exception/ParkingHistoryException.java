package com.parkiraja.exception;

public class ParkingHistoryException extends RuntimeException {

    public ParkingHistoryException(String message) {
        super(message);
    }

    public ParkingHistoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
