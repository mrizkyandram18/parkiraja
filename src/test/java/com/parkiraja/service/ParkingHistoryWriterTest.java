package com.parkiraja.service;

import com.parkiraja.exception.ParkingHistoryException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParkingHistoryWriterTest {

    @TempDir
    Path tempDir;

    private Path testCsvFile;
    private ParkingHistoryWriter writer;

    @BeforeEach
    void setUp() {
        testCsvFile = tempDir.resolve("history/test_parking_history.csv");
        writer = new ParkingHistoryWriter(testCsvFile.toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(testCsvFile)) {
            Files.deleteIfExists(testCsvFile);
        }
    }

    @Test
    void shouldCreateParentDirectoryAndHeaderOnFirstWrite() throws IOException {
        LocalDateTime entryTime = LocalDateTime.of(2026, 11, 13, 10, 0, 0);
        LocalDateTime exitTime = LocalDateTime.of(2026, 11, 13, 12, 0, 0);

        writer.append("B 1234 XYZ", 1, entryTime, exitTime, 10000L);

        assertThat(Files.exists(testCsvFile)).isTrue();
        List<String> lines = Files.readAllLines(testCsvFile);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo("license_plate,vehicle_type,entry_time,exit_time,parking_fee");
        assertThat(lines.get(1)).isEqualTo("B 1234 XYZ,1,2026-11-13T10:00,2026-11-13T12:00,10000");
    }

    @Test
    void shouldAppendMultipleTransactionsWithoutDuplicatingHeader() throws IOException {
        LocalDateTime entryTime1 = LocalDateTime.of(2026, 11, 13, 10, 0, 0);
        LocalDateTime exitTime1 = LocalDateTime.of(2026, 11, 13, 11, 0, 0);
        LocalDateTime entryTime2 = LocalDateTime.of(2026, 11, 13, 11, 30, 0);
        LocalDateTime exitTime2 = LocalDateTime.of(2026, 11, 13, 13, 0, 0);

        writer.append("B 1111 AA", 1, entryTime1, exitTime1, 5000L);
        writer.append("D 2222 BB", 2, entryTime2, exitTime2, 6000L);

        List<String> lines = Files.readAllLines(testCsvFile);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("license_plate,vehicle_type,entry_time,exit_time,parking_fee");
        assertThat(lines.get(1)).isEqualTo("B 1111 AA,1,2026-11-13T10:00,2026-11-13T11:00,5000");
        assertThat(lines.get(2)).isEqualTo("D 2222 BB,2,2026-11-13T11:30,2026-11-13T13:00,6000");
    }

    @Test
    void shouldEscapeSpecialCharactersInLicensePlate() throws IOException {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 13, 10, 0, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 11, 13, 11, 0, 0);

        // Plate with comma
        writer.append("B,1234,XYZ", 1, entry, exit, 5000L);
        // Plate with double quotes
        writer.append("B \"999\" ABC", 2, entry, exit, 3000L);
        // Plate with newline
        writer.append("B 5555\nXYZ", 1, entry, exit, 5000L);
        // Plate with carriage return
        writer.append("B 7777\rXYZ", 1, entry, exit, 5000L);
        // Null plate
        writer.append(null, 1, entry, exit, 5000L);

        List<String> lines = Files.readAllLines(testCsvFile);
        // Header + lines
        String content = Files.readString(testCsvFile);
        assertThat(content).contains("\"B,1234,XYZ\"");
        assertThat(content).contains("\"B \"\"999\"\" ABC\"");
        assertThat(content).contains("\"B 5555\nXYZ\"");
        assertThat(content).contains("\"B 7777\rXYZ\"");
    }

    @Test
    void shouldThrowParkingHistoryExceptionOnIoError() {
        // Point writer to a directory instead of a file, causing IOException on write
        Path directoryAsFile = tempDir.resolve("dir_as_file");
        try {
            Files.createDirectories(directoryAsFile);
        } catch (IOException ignored) {
        }
        ParkingHistoryWriter faultyWriter = new ParkingHistoryWriter(directoryAsFile.toString());

        LocalDateTime now = LocalDateTime.now();
        assertThrows(ParkingHistoryException.class, () ->
                faultyWriter.append("B 1234 XYZ", 1, now, now, 5000L)
        );
    }

    @Test
    void shouldReturnConfiguredCsvFilePath() {
        assertThat(writer.getCsvFilePath()).isEqualTo(testCsvFile);
    }
}
