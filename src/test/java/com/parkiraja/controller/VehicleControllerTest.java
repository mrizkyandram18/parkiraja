package com.parkiraja.controller;

import com.parkiraja.dto.CheckInRequest;
import com.parkiraja.dto.CheckOutRequest;
import com.parkiraja.dto.CheckOutResponse;
import com.parkiraja.dto.VehicleResponse;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.exception.DuplicateVehicleException;
import com.parkiraja.exception.GlobalExceptionHandler;
import com.parkiraja.exception.InvalidCapacityException;
import com.parkiraja.exception.ParkingHistoryException;
import com.parkiraja.exception.ParkingLotFullException;
import com.parkiraja.exception.VehicleNotFoundException;
import com.parkiraja.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
class VehicleControllerTest {

    private MockMvc mockMvc;

    @Mock
    private VehicleService vehicleService;

    @InjectMocks
    private VehicleController vehicleController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(vehicleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn201WhenCheckInSuccess() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(vehicleService.checkIn(any(CheckInRequest.class)))
                .thenReturn(new VehicleResponse("B 1234 XYZ", 1, now));

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\", \"vehicleType\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("B 1234 XYZ"))
                .andExpect(jsonPath("$.vehicleType").value(1))
                .andExpect(jsonPath("$.entryTime").isNotEmpty());

        verify(vehicleService).checkIn(new CheckInRequest("B 1234 XYZ", 1));
    }

    @Test
    void shouldReturn400WhenLicensePlateIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"   \", \"vehicleType\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("License plate must not be blank"));
    }

    @Test
    void shouldReturn400WhenVehicleTypeIsNull() throws Exception {
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Vehicle type is required"));
    }

    @Test
    void shouldReturn400WhenVehicleTypeIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\", \"vehicleType\": 3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Vehicle type must be 1 (Car) or 2 (Motorcycle)"));
    }

    @Test
    void shouldReturn404WhenCapacityNotConfiguredOnCheckIn() throws Exception {
        when(vehicleService.checkIn(any(CheckInRequest.class)))
                .thenThrow(new CapacityNotFoundException("Parking capacity has not been configured"));

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\", \"vehicleType\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Parking capacity has not been configured"));
    }

    @Test
    void shouldReturn409WhenVehicleAlreadyParked() throws Exception {
        when(vehicleService.checkIn(any(CheckInRequest.class)))
                .thenThrow(new DuplicateVehicleException("Vehicle with license plate B 1234 XYZ is already parked"));

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\", \"vehicleType\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate B 1234 XYZ is already parked"));
    }

    @Test
    void shouldReturn409WhenParkingLotIsFull() throws Exception {
        when(vehicleService.checkIn(any(CheckInRequest.class)))
                .thenThrow(new ParkingLotFullException("Parking lot is full"));

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\", \"vehicleType\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Parking lot is full"));
    }

    @Test
    void shouldReturn409WhenDataIntegrityViolationOccurs() throws Exception {
        when(vehicleService.checkIn(any(CheckInRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Unique index or primary key violation"));

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\", \"vehicleType\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate is already parked"));
    }

    @Test
    void shouldReturn400WhenInvalidCapacityExceptionOccurs() throws Exception {
        when(vehicleService.checkIn(any(CheckInRequest.class)))
                .thenThrow(new InvalidCapacityException("Total capacity cannot be less than current active vehicle count: 5"));

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\", \"vehicleType\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Total capacity cannot be less than current active vehicle count: 5"));
    }

    @Test
    void shouldReturn200WhenVehicleFound() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(vehicleService.getVehicle("B 1234 XYZ"))
                .thenReturn(new VehicleResponse("B 1234 XYZ", 1, now));

        mockMvc.perform(get("/api/v1/parking/vehicles/B 1234 XYZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value("B 1234 XYZ"))
                .andExpect(jsonPath("$.vehicleType").value(1))
                .andExpect(jsonPath("$.entryTime").isNotEmpty());

        verify(vehicleService).getVehicle("B 1234 XYZ");
    }

    @Test
    void shouldReturn404WhenVehicleNotFound() throws Exception {
        when(vehicleService.getVehicle("UNKNOWN"))
                .thenThrow(new VehicleNotFoundException("Vehicle with license plate UNKNOWN is not actively parked"));

        mockMvc.perform(get("/api/v1/parking/vehicles/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate UNKNOWN is not actively parked"));

        verify(vehicleService).getVehicle("UNKNOWN");
    }

    @Test
    void shouldReturn200WhenCheckOutSuccess() throws Exception {
        LocalDateTime entryTime = LocalDateTime.now().minusHours(2);
        LocalDateTime exitTime = LocalDateTime.now();
        when(vehicleService.checkOut(any(CheckOutRequest.class)))
                .thenReturn(new CheckOutResponse("B 1234 XYZ", 1, entryTime, exitTime, 2, 5000L, 10000L));

        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value("B 1234 XYZ"))
                .andExpect(jsonPath("$.vehicleType").value(1))
                .andExpect(jsonPath("$.entryTime").isNotEmpty())
                .andExpect(jsonPath("$.exitTime").isNotEmpty())
                .andExpect(jsonPath("$.durationHours").value(2))
                .andExpect(jsonPath("$.hourlyRate").value(5000))
                .andExpect(jsonPath("$.totalFee").value(10000));

        verify(vehicleService).checkOut(any(CheckOutRequest.class));
    }

    @Test
    void shouldReturn400WhenCheckOutLicensePlateIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("License plate must not be blank"));
    }

    @Test
    void shouldReturn404WhenCheckOutVehicleNotFound() throws Exception {
        when(vehicleService.checkOut(any(CheckOutRequest.class)))
                .thenThrow(new VehicleNotFoundException("Vehicle with license plate B 9999 NON is not actively parked"));

        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 9999 NON\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate B 9999 NON is not actively parked"));
    }

    @Test
    void shouldReturn500WhenParkingHistoryExceptionOccurs() throws Exception {
        when(vehicleService.checkOut(any(CheckOutRequest.class)))
                .thenThrow(new ParkingHistoryException("Failed to record parking history to CSV"));

        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Failed to record parking history to CSV"));
    }
}
