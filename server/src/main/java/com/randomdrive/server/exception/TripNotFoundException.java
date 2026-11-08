package com.randomdrive.server.exception;

import org.springframework.http.HttpStatus;

/** 존재하지 않는 tripId → 404 TRIP_NOT_FOUND. */
public class TripNotFoundException extends ApiException {

    public TripNotFoundException(String tripId) {
        super(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "trip not found: " + tripId);
    }
}
