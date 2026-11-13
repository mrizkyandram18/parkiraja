package com.parkiraja.service;

import com.parkiraja.entity.ParkingHistory;
import com.parkiraja.entity.VehicleType;
import com.parkiraja.exception.ParkingHistoryException;
import com.parkiraja.repository.ParkingHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParkingHistoryWriterTest {

    @TempDir
    Path tempDir;

    @Mock
    private ParkingHistoryRepository parkingHistoryRepository;

    private Path testCsvFile;
    private ParkingHistoryWriter writer;

    @BeforeEach
    void setUp() {
        testCsvFile = tempDir.resolve("history/test_parking_history.csv");
        writer = new ParkingHistoryWriter(testCsvFile.toString(), parkingHistoryRepository);
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

        ParkingHistory history = new ParkingHistory("B 1234 XYZ", VehicleType.CAR, entryTime, exitTime, 10000L);
        writer.writeAll(List.of(history));

        assertThat(Files.exists(testCsvFile)).isTrue();
        String fileContent = Files.readString(testCsvFile);
        assertThat(fileContent).contains("\r\n"); // RFC 4180 CRLF
        List<String> lines = Files.readAllLines(testCsvFile);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo("license_plate,vehicle_type,entry_time,exit_time,parking_fee");
        assertThat(lines.get(1)).isEqualTo("B 1234 XYZ,1,2026-11-13T10:00,2026-11-13T12:00,10000");
    }

    @Test
    void shouldWriteMultipleTransactionsWithoutDuplicatingHeader() throws IOException {
        LocalDateTime entryTime1 = LocalDateTime.of(2026, 11, 13, 10, 0, 0);
        LocalDateTime exitTime1 = LocalDateTime.of(2026, 11, 13, 11, 0, 0);
        LocalDateTime entryTime2 = LocalDateTime.of(2026, 11, 13, 11, 30, 0);
        LocalDateTime exitTime2 = LocalDateTime.of(2026, 11, 13, 13, 0, 0);

        ParkingHistory history1 = new ParkingHistory("B 1111 AA", VehicleType.CAR, entryTime1, exitTime1, 5000L);
        ParkingHistory history2 = new ParkingHistory("D 2222 BB", VehicleType.MOTORCYCLE, entryTime2, exitTime2, 6000L);

        writer.writeAll(List.of(history1, history2));

        List<String> lines = Files.readAllLines(testCsvFile);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("license_plate,vehicle_type,entry_time,exit_time,parking_fee");
        assertThat(lines.get(1)).isEqualTo("B 1111 AA,1,2026-11-13T10:00,2026-11-13T11:00,5000");
        assertThat(lines.get(2)).isEqualTo("D 2222 BB,2,2026-11-13T11:30,2026-11-13T13:00,6000");
    }

    @Test
    void shouldHandleNullOrEmptyHistoryList() throws IOException {
        writer.writeAll(null);
        assertThat(Files.exists(testCsvFile)).isTrue();
        List<String> lines = Files.readAllLines(testCsvFile);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("license_plate,vehicle_type,entry_time,exit_time,parking_fee");

        writer.writeAll(List.of());
        lines = Files.readAllLines(testCsvFile);
        assertThat(lines).hasSize(1);
    }

    @Test
    void shouldSyncFromDatabaseAndSupportOnApplicationReady() throws IOException {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 13, 9, 0, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 11, 13, 10, 0, 0);
        ParkingHistory history = new ParkingHistory("B 9999 SYNC", VehicleType.CAR, entry, exit, 5000L);

        when(parkingHistoryRepository.findAllByOrderByIdAsc()).thenReturn(List.of(history));

        // Test onApplicationReady()
        writer.onApplicationReady();

        verify(parkingHistoryRepository).findAllByOrderByIdAsc();
        assertThat(Files.exists(testCsvFile)).isTrue();
        List<String> lines = Files.readAllLines(testCsvFile);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).contains("B 9999 SYNC");

        // Repeated sync does not duplicate rows
        writer.syncFromDatabase();
        lines = Files.readAllLines(testCsvFile);
        assertThat(lines).hasSize(2);
    }

    @Test
    void shouldEscapeSpecialCharactersInLicensePlate() throws IOException {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 13, 10, 0, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 11, 13, 11, 0, 0);

        ParkingHistory h1 = new ParkingHistory("B,1234,XYZ", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h2 = new ParkingHistory("B \"999\" ABC", VehicleType.MOTORCYCLE, entry, exit, 3000L);
        ParkingHistory h3 = new ParkingHistory("B 5555\nXYZ", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h4 = new ParkingHistory("B 7777\rXYZ", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h5 = new ParkingHistory(null, VehicleType.CAR, null, null, 5000L);

        writer.writeAll(List.of(h1, h2, h3, h4, h5));

        String content = Files.readString(testCsvFile);
        assertThat(content).contains("\"B,1234,XYZ\"");
        assertThat(content).contains("\"B \"\"999\"\" ABC\"");
        assertThat(content).contains("\"B 5555\nXYZ\"");
        assertThat(content).contains("\"B 7777\rXYZ\"");
        assertThat(writer.escapeCsv(null)).isEqualTo("");
    }

    @Test
    void shouldPreventCsvFormulaInjectionOnAllDangerousPrefixes() throws IOException {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 13, 10, 0, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 11, 13, 11, 0, 0);

        ParkingHistory h1 = new ParkingHistory("=CMD|' /C CALC'!A0", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h2 = new ParkingHistory("+62812345", VehicleType.MOTORCYCLE, entry, exit, 3000L);
        ParkingHistory h3 = new ParkingHistory("-5000", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h4 = new ParkingHistory("@SUM(1,2)", VehicleType.MOTORCYCLE, entry, exit, 3000L);

        writer.writeAll(List.of(h1, h2, h3, h4));

        String content = Files.readString(testCsvFile);
        // Verify formula injection protection: single quote prefix prepended at CSV serialization
        assertThat(content).contains("'=CMD|' /C CALC'!A0,1,2026-11-13T10:00,2026-11-13T11:00,5000");
        assertThat(content).contains("'+62812345,2,2026-11-13T10:00,2026-11-13T11:00,3000");
        assertThat(content).contains("'-5000,1,2026-11-13T10:00,2026-11-13T11:00,5000");
        assertThat(content).contains("\"'@SUM(1,2)\",2,2026-11-13T10:00,2026-11-13T11:00,3000");

        // Verify entities themselves were NOT modified in memory/database
        assertThat(h1.getLicensePlate()).isEqualTo("=CMD|' /C CALC'!A0");
        assertThat(h2.getLicensePlate()).isEqualTo("+62812345");
        assertThat(h3.getLicensePlate()).isEqualTo("-5000");
        assertThat(h4.getLicensePlate()).isEqualTo("@SUM(1,2)");
    }

    @Test
    void shouldPreventCsvFormulaInjectionWithLeadingWhitespaceAndControlCharacters() throws IOException {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 13, 10, 0, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 11, 13, 11, 0, 0);

        ParkingHistory h1 = new ParkingHistory("   =1+1", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h2 = new ParkingHistory("\t+1+1", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h3 = new ParkingHistory("\r=1+1", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h4 = new ParkingHistory("\n=1+1", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h5 = new ParkingHistory("\0=1+1", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h6 = new ParkingHistory("\tFOO", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h7 = new ParkingHistory("\rFOO", VehicleType.CAR, entry, exit, 5000L);

        writer.writeAll(List.of(h1, h2, h3, h4, h5, h6, h7));

        String content = Files.readString(testCsvFile);
        assertThat(content).contains("'   =1+1");
        assertThat(content).contains("'\t+1+1");
        assertThat(content).contains("\"'\r=1+1\"");
        assertThat(content).contains("\"'\n=1+1\"");
        assertThat(content).contains("'\0=1+1");
        assertThat(content).contains("'\tFOO");
        assertThat(content).contains("\"'\rFOO\"");
    }

    @Test
    void shouldEscapeAndSanitizeCombinedFormulaAndSpecialCharacters() throws IOException {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 13, 10, 0, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 11, 13, 11, 0, 0);

        ParkingHistory h1 = new ParkingHistory("=SUM(1,2),10", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h2 = new ParkingHistory("=CMD(\"calc\")", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h3 = new ParkingHistory("=A1+\r\nB1", VehicleType.CAR, entry, exit, 5000L);

        writer.writeAll(List.of(h1, h2, h3));

        String content = Files.readString(testCsvFile);
        // Formula prefixed with ' AND quoted/escaped properly for RFC 4180
        assertThat(content).contains("\"'=SUM(1,2),10\"");
        assertThat(content).contains("\"'=CMD(\"\"calc\"\")\"");
        assertThat(content).contains("\"'=A1+\r\nB1\"");
    }

    @Test
    void shouldLeaveNormalLicensePlatesUnprefixed() throws IOException {
        LocalDateTime entry = LocalDateTime.of(2026, 11, 13, 10, 0, 0);
        LocalDateTime exit = LocalDateTime.of(2026, 11, 13, 11, 0, 0);

        ParkingHistory h1 = new ParkingHistory("B 1234 XYZ", VehicleType.CAR, entry, exit, 5000L);
        ParkingHistory h2 = new ParkingHistory("DK 9999 AA", VehicleType.MOTORCYCLE, entry, exit, 3000L);
        ParkingHistory h3 = new ParkingHistory("   B 1234 XYZ", VehicleType.CAR, entry, exit, 5000L);

        writer.writeAll(List.of(h1, h2, h3));

        String content = Files.readString(testCsvFile);
        assertThat(content).contains("B 1234 XYZ,1,2026-11-13T10:00,2026-11-13T11:00,5000");
        assertThat(content).contains("DK 9999 AA,2,2026-11-13T10:00,2026-11-13T11:00,3000");
        assertThat(content).contains("   B 1234 XYZ,1,2026-11-13T10:00,2026-11-13T11:00,5000");
    }

    @Test
    void shouldSanitizeFormulaDirectly() {
        assertThat(writer.sanitizeFormula(null)).isNull();
        assertThat(writer.sanitizeFormula("")).isEqualTo("");
        assertThat(writer.sanitizeFormula("   ")).isEqualTo("   ");
        assertThat(writer.sanitizeFormula("B 1234 XYZ")).isEqualTo("B 1234 XYZ");
        assertThat(writer.sanitizeFormula("=cmd")).isEqualTo("'=cmd");
        assertThat(writer.sanitizeFormula("+cmd")).isEqualTo("'+cmd");
        assertThat(writer.sanitizeFormula("-cmd")).isEqualTo("'-cmd");
        assertThat(writer.sanitizeFormula("@cmd")).isEqualTo("'@cmd");
        assertThat(writer.sanitizeFormula("\tcmd")).isEqualTo("'\tcmd");
        assertThat(writer.sanitizeFormula("\rcmd")).isEqualTo("'\rcmd");
        assertThat(writer.sanitizeFormula("  =cmd")).isEqualTo("'  =cmd");
        assertThat(writer.sanitizeFormula("  +cmd")).isEqualTo("'  +cmd");
        assertThat(writer.sanitizeFormula("  -cmd")).isEqualTo("'  -cmd");
        assertThat(writer.sanitizeFormula("  @cmd")).isEqualTo("'  @cmd");
        assertThat(writer.sanitizeFormula("  plain")).isEqualTo("  plain");
    }

    @Test
    void shouldThrowParkingHistoryExceptionOnIoError() {
        Path directoryAsFile = tempDir.resolve("dir_as_file");
        try {
            Files.createDirectories(directoryAsFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ParkingHistoryWriter faultyWriter = new ParkingHistoryWriter(directoryAsFile.toString(), parkingHistoryRepository);
        assertThrows(ParkingHistoryException.class, () -> faultyWriter.writeAll(List.of()));
    }

    @Test
    void shouldReturnCsvFilePath() {
        assertThat(writer.getCsvFilePath()).isEqualTo(testCsvFile);
    }

    @Test
    void shouldCatchAndLogParkingHistoryExceptionOnApplicationReady() {
        Path directoryAsFile = tempDir.resolve("dir_as_file_ready");
        try {
            Files.createDirectories(directoryAsFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        when(parkingHistoryRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                new ParkingHistory("B 1111 ERR", VehicleType.CAR, LocalDateTime.now(), LocalDateTime.now(), 5000L)
        ));

        ParkingHistoryWriter faultyWriter = new ParkingHistoryWriter(directoryAsFile.toString(), parkingHistoryRepository);
        // Must catch ParkingHistoryException and not throw
        faultyWriter.onApplicationReady();
        verify(parkingHistoryRepository).findAllByOrderByIdAsc();
    }

    @Test
    void shouldPropagateDatabaseExceptionOnApplicationReady() {
        when(parkingHistoryRepository.findAllByOrderByIdAsc())
                .thenThrow(new RuntimeException("Database connection failure"));

        assertThrows(RuntimeException.class, () -> writer.onApplicationReady());
        verify(parkingHistoryRepository).findAllByOrderByIdAsc();
    }
}
