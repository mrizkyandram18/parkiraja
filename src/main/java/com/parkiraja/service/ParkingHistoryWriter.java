package com.parkiraja.service;

import com.parkiraja.exception.ParkingHistoryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

@Component
public class ParkingHistoryWriter {

    private static final String CSV_HEADER = "license_plate,vehicle_type,entry_time,exit_time,parking_fee\n";

    private final Path csvFilePath;

    public ParkingHistoryWriter(@Value("${parking.history.csv.path:data/parking_history.csv}") String csvPath) {
        this.csvFilePath = Path.of(csvPath);
    }

    public synchronized void append(String licensePlate, int vehicleType, LocalDateTime entryTime, LocalDateTime exitTime, long parkingFee) {
        try {
            Path parent = csvFilePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            boolean fileExists = Files.exists(csvFilePath) && Files.size(csvFilePath) > 0;
            if (!fileExists) {
                Files.writeString(
                        csvFilePath,
                        CSV_HEADER,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE
                );
            }

            String record = String.format("%s,%d,%s,%s,%d\n",
                    escapeCsv(licensePlate),
                    vehicleType,
                    entryTime.toString(),
                    exitTime.toString(),
                    parkingFee
            );

            Files.writeString(
                    csvFilePath,
                    record,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new ParkingHistoryException("Failed to record parking history to CSV", e);
        }
    }

    public Path getCsvFilePath() {
        return csvFilePath;
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
