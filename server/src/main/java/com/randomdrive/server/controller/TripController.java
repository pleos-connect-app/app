package com.randomdrive.server.controller;

import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.FinishTripResponse;
import com.randomdrive.server.dto.RecapResponse;
import com.randomdrive.server.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/trips 경로의 HTTP 요청을 받아 서비스로 넘긴다. */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    /** [2] POST /api/v1/trips/{tripId}/finish → 202 Accepted */
    @PostMapping("/{tripId}/finish")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FinishTripResponse finish(@PathVariable String tripId,
                                     @Valid @RequestBody FinishTripRequest request) {
        return tripService.finishTrip(tripId, request);
    }

    /** [3] GET /api/v1/trips/{tripId}/recap → 200 */
    @GetMapping("/{tripId}/recap")
    public RecapResponse recap(@PathVariable String tripId) {
        return tripService.getRecap(tripId);
    }
}
