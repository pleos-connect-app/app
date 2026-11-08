package com.randomdrive.server.controller;

import com.randomdrive.server.dto.RandomDestinationRequest;
import com.randomdrive.server.dto.RandomDestinationResponse;
import com.randomdrive.server.service.TripService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/destinations 경로의 HTTP 요청을 받아 서비스로 넘긴다. */
@RestController
@RequestMapping("/api/v1/destinations")
public class DestinationController {

    private final TripService tripService;

    // 생성자 주입: 스프링이 TripService 빈을 자동으로 넣어준다.
    public DestinationController(TripService tripService) {
        this.tripService = tripService;
    }

    /** [1] POST /api/v1/destinations/random → 200 */
    @PostMapping("/random")
    public RandomDestinationResponse random(@Valid @RequestBody RandomDestinationRequest request) {
        return tripService.createRandomDestination(request);
    }
}
