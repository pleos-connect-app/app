package com.randomdrive.server.persistence;

import com.randomdrive.server.dto.TripStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * trip 테이블 한 줄. 목적지·리캡 상태·요약·공유 토큰. 경로 점(무거움)은 TripFinishEntity에 따로 둔다.
 * 상태 규칙은 service.Trip에 있고, 여기는 저장 형태만 담는다.
 */
@Entity
@Table(name = "trip", indexes = {
        @Index(name = "ux_trip_share_token", columnList = "shareToken", unique = true),
        @Index(name = "ix_trip_status", columnList = "status")
})
public class TripEntity {

    @Id
    @Column(length = 16)
    private String id;

    @Column(nullable = false)
    private Instant createdAt;

    // 목적지(발급 후 바뀌지 않음)
    @Column(nullable = false, length = 64)
    private String placeId;
    @Column(nullable = false)
    private String placeName;
    private double placeLat;
    private double placeLng;
    private String placeAddress;
    @Column(length = 1024)
    private String placeImageUrl;
    private double placeDistanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TripStatus status;
    private int generation;
    private boolean finished;
    private Long endedAt;

    // 리캡(DONE일 때)
    @Column(length = 32)
    private String shareToken;
    private Double summaryDistanceKm;
    private Integer summaryDurationMin;
    private Integer summaryStopCount;
    private boolean hasVideo;
    private boolean hasThumbnail;
    private Instant completedAt;

    protected TripEntity() {
    }

    public TripEntity(String id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getPlaceId() { return placeId; }
    public String getPlaceName() { return placeName; }
    public double getPlaceLat() { return placeLat; }
    public double getPlaceLng() { return placeLng; }
    public String getPlaceAddress() { return placeAddress; }
    public String getPlaceImageUrl() { return placeImageUrl; }
    public double getPlaceDistanceKm() { return placeDistanceKm; }
    public TripStatus getStatus() { return status; }
    public int getGeneration() { return generation; }
    public boolean isFinished() { return finished; }
    public Long getEndedAt() { return endedAt; }
    public String getShareToken() { return shareToken; }
    public Double getSummaryDistanceKm() { return summaryDistanceKm; }
    public Integer getSummaryDurationMin() { return summaryDurationMin; }
    public Integer getSummaryStopCount() { return summaryStopCount; }
    public boolean isHasVideo() { return hasVideo; }
    public boolean isHasThumbnail() { return hasThumbnail; }
    public Instant getCompletedAt() { return completedAt; }

    void setPlace(String placeId, String name, double lat, double lng, String address, String imageUrl, double distanceKm) {
        this.placeId = placeId;
        this.placeName = name;
        this.placeLat = lat;
        this.placeLng = lng;
        this.placeAddress = address;
        this.placeImageUrl = imageUrl;
        this.placeDistanceKm = distanceKm;
    }

    void setState(TripStatus status, int generation, boolean finished, Long endedAt) {
        this.status = status;
        this.generation = generation;
        this.finished = finished;
        this.endedAt = endedAt;
    }

    void setRecap(String shareToken, Double distanceKm, Integer durationMin, Integer stopCount,
                  boolean hasVideo, boolean hasThumbnail, Instant completedAt) {
        this.shareToken = shareToken;
        this.summaryDistanceKm = distanceKm;
        this.summaryDurationMin = durationMin;
        this.summaryStopCount = stopCount;
        this.hasVideo = hasVideo;
        this.hasThumbnail = hasThumbnail;
        this.completedAt = completedAt;
    }
}
