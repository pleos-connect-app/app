package com.randomdrive.server.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * trip_finish 테이블 한 줄: finish로 받은 주행 데이터(경로 점 최대 1만 개)를 JSON 그대로.
 * 리캡을 (다시) 만들 때만 읽는다. 공유 페이지·리캡 조회는 이 테이블을 읽지 않는다.
 * 원본 위치 로그라 외부로 내보내지 않는다(F-08).
 */
@Entity
@Table(name = "trip_finish")
public class TripFinishEntity {

    @Id
    @Column(length = 16)
    private String tripId;

    @Lob
    @Column(nullable = false)
    private String json;

    protected TripFinishEntity() {
    }

    public TripFinishEntity(String tripId, String json) {
        this.tripId = tripId;
        this.json = json;
    }

    public String getTripId() {
        return tripId;
    }

    public String getJson() {
        return json;
    }
}
