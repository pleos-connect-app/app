package com.randomdrive.server.dto;

/** 주행 중 발생하는 이벤트 종류. 이 4개 외의 값이 오면 400 INVALID_REQUEST. */
public enum EventType {
    DESTINATION_SET,
    WAYPOINT_ADDED,
    WAYPOINT_ARRIVED,
    DESTINATION_ARRIVED
}
