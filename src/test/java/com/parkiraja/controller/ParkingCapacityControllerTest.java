package com.parkiraja.controller;

import com.parkiraja.dto.CapacityRequest;
import com.parkiraja.dto.CapacityResponse;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.exception.GlobalExceptionHandler;
import com.parkiraja.service.ParkingCapacityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ParkingCapacityControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ParkingCapacityService capacityService;

    @InjectMocks
    private ParkingCapacityController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturn200WhenUpdatingCapacity() throws Exception {
        when(capacityService.updateCapacity(any(CapacityRequest.class)))
                .thenReturn(new CapacityResponse(50));

        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacity\": 50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCapacity").value(50));

        verify(capacityService).updateCapacity(new CapacityRequest(50));
    }

    @Test
    void shouldReturn200WhenRetrievingCapacity() throws Exception {
        when(capacityService.getCapacity()).thenReturn(new CapacityResponse(50));

        mockMvc.perform(get("/api/v1/parking/capacity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCapacity").value(50));

        verify(capacityService).getCapacity();
    }

    @Test
    void shouldReturn404WhenCapacityNotConfigured() throws Exception {
        when(capacityService.getCapacity())
                .thenThrow(new CapacityNotFoundException("Parking capacity has not been configured"));

        mockMvc.perform(get("/api/v1/parking/capacity"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Parking capacity has not been configured"));

        verify(capacityService).getCapacity();
    }

    @Test
    void shouldReturn400WhenCapacityIsZero() throws Exception {
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Total capacity must be greater than zero"));
    }

    @Test
    void shouldReturn400WhenCapacityIsNegative() throws Exception {
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacity\": -10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Total capacity must be greater than zero"));
    }

    @Test
    void shouldReturn400WhenCapacityIsMissing() throws Exception {
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Total capacity is required"));
    }

    @Test
    void shouldReturn400WhenPayloadIsMalformed() throws Exception {
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-a-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid request payload"));
    }

    @Test
    void shouldReturn500WhenGenericExceptionOccurs() throws Exception {
        when(capacityService.getCapacity()).thenThrow(new RuntimeException("Unexpected server error: SELECT * FROM users"));

        mockMvc.perform(get("/api/v1/parking/capacity"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred"));
    }

    @Test
    void shouldNotExposeSensitiveSqlOrFilesystemDetailsOnGenericException() throws Exception {
        when(capacityService.getCapacity())
                .thenThrow(new RuntimeException("Syntax error in SQL statement SELECT * FROM SECRET_TABLE at /var/data/h2/db.mv.db"));

        mockMvc.perform(get("/api/v1/parking/capacity"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
