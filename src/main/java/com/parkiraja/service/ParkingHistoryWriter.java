package com.parkiraja.service;

import com.parkiraja.entity.ParkingHistory;
import com.parkiraja.exception.ParkingHistoryException;
import com.parkiraja.repository.ParkingHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ParkingHistoryWriter {

    private static final Logger log = LoggerFactory.getLogger(ParkingHistoryWriter.class);
    private static final String CSV_HEADER = "license_plate,vehicle_type,entry_time,exit_time,parking_fee\r\n";

    private final Path csvFilePath;
    private final ParkingHistoryRepository parkingHistoryRepository;

    public ParkingHistoryWriter(@Value("${parking.history.csv.path:data/parking_history.csv}") String csvPath,
                                ParkingHistoryRepository parkingHistoryRepository) {
        this.csvFilePath = Path.of(csvPath);
        this.parkingHistoryRepository = parkingHistoryRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            syncFromDatabase();
        } catch (ParkingHistoryException e) {
            log.error("Failed to synchronize parking history CSV during application startup", e);
        }
    }

    public synchronized void syncFromDatabase() {
        List<ParkingHistory> historyList = parkingHistoryRepository.findAllByOrderByIdAsc();
        writeAll(historyList);
    }

    public synchronized void writeAll(List<ParkingHistory> historyList) {
        try {
            Path parent = csvFilePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            StringBuilder sb = new StringBuilder();
            sb.append(CSV_HEADER);
            if (historyList != null) {
                for (ParkingHistory history : historyList) {
                    sb.append(formatRecord(
                            history.getLicensePlate(),
                            history.getVehicleType().getCode(),
                            history.getEntryTime(),
                            history.getExitTime(),
                            history.getTotalFee()
                    ));
                }
            }

            Files.writeString(
                    csvFilePath,
                    sb.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            throw new ParkingHistoryException("Failed to record parking history to CSV", e);
        }
    }

    public Path getCsvFilePath() {
        return csvFilePath;
    }

    private String formatRecord(String licensePlate, int vehicleType, LocalDateTime entryTime, LocalDateTime exitTime, long parkingFee) {
        return String.format("%s,%d,%s,%s,%d\r\n",
                escapeCsv(licensePlate),
                vehicleType,
                entryTime != null ? entryTime.toString() : "",
                exitTime != null ? exitTime.toString() : "",
                parkingFee
        );
    }

    public String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = sanitizeFormula(value);
        if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n") || sanitized.contains("\r")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    public String sanitizeFormula(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (isFormulaTrigger(first) || first == '\t' || first == '\r') {
            return "'" + value;
        }
        int i = 0;
        int len = value.length();
        while (i < len && isWhitespaceOrControl(value.charAt(i))) {
            i++;
        }
        if (i > 0 && i < len && isFormulaTrigger(value.charAt(i))) {
            return "'" + value;
        }
        return value;
    }

    private boolean isFormulaTrigger(char ch) {
        return ch == '=' || ch == '+' || ch == '-' || ch == '@';
    }

    private boolean isWhitespaceOrControl(char ch) {
        return ch <= ' ' || Character.isWhitespace(ch) || Character.isISOControl(ch);
    }
}
