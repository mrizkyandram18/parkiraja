package com.parkiraja.controller;

import com.parkiraja.dto.ReservationRequest;
import com.parkiraja.dto.ReservationResponse;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.exception.DuplicateReservationException;
import com.parkiraja.exception.DuplicateVehicleException;
import com.parkiraja.exception.GlobalExceptionHandler;
import com.parkiraja.exception.ParkingLotFullException;
import com.parkiraja.exception.ReservationNotFoundException;
import com.parkiraja.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ParkingReservationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ParkingReservationController reservationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reservationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn201WhenReservationSuccess() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(reservationService.createReservation(any(ReservationRequest.class)))
                .thenReturn(new ReservationResponse("B 5678 ABC", now));

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 5678 ABC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("B 5678 ABC"))
                .andExpect(jsonPath("$.reservedAt").isNotEmpty());

        verify(reservationService).createReservation(any(ReservationRequest.class));
    }

    @Test
    void shouldReturn400WhenLicensePlateIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("License plate must not be blank"));
    }

    @Test
    void shouldReturn404WhenCapacityNotConfigured() throws Exception {
        when(reservationService.createReservation(any(ReservationRequest.class)))
                .thenThrow(new CapacityNotFoundException("Parking capacity has not been configured"));

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 5678 ABC\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Parking capacity has not been configured"));
    }

    @Test
    void shouldReturn409WhenParkingLotFull() throws Exception {
        when(reservationService.createReservation(any(ReservationRequest.class)))
                .thenThrow(new ParkingLotFullException("Parking lot is full"));

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 5678 ABC\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Parking lot is full"));
    }

    @Test
    void shouldReturn409WhenDuplicateReservation() throws Exception {
        when(reservationService.createReservation(any(ReservationRequest.class)))
                .thenThrow(new DuplicateReservationException("Vehicle with license plate B 5678 ABC already has an active reservation"));

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 5678 ABC\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate B 5678 ABC already has an active reservation"));
    }

    @Test
    void shouldReturn409WhenVehicleAlreadyParked() throws Exception {
        when(reservationService.createReservation(any(ReservationRequest.class)))
                .thenThrow(new DuplicateVehicleException("Vehicle with license plate B 5678 ABC is already actively parked"));

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 5678 ABC\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate B 5678 ABC is already actively parked"));
    }

    @Test
    void shouldReturn200WhenGetReservationFound() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(reservationService.getReservation("B 5678 ABC"))
                .thenReturn(new ReservationResponse("B 5678 ABC", now));

        mockMvc.perform(get("/api/v1/parking/reservations/B 5678 ABC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value("B 5678 ABC"))
                .andExpect(jsonPath("$.reservedAt").isNotEmpty());

        verify(reservationService).getReservation("B 5678 ABC");
    }

    @Test
    void shouldReturn404WhenGetReservationNotFound() throws Exception {
        when(reservationService.getReservation("B 9999 NON"))
                .thenThrow(new ReservationNotFoundException("No active reservation found for license plate B 9999 NON"));

        mockMvc.perform(get("/api/v1/parking/reservations/B 9999 NON"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No active reservation found for license plate B 9999 NON"));
    }
}
