package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.TripStatus;

/**
 * 메모리에 저장되는 트립 한 건. (나중에 DB가 생기면 Entity가 될 자리)
 * 여러 요청이 동시에 같은 트립을 건드릴 수 있어 상태를 읽고 바꾸는 메서드는 synchronized.
 *
 * 리캡 상태 흐름: (finish 전) PENDING → finish → PENDING → RUNNING → DONE 또는 FAILED
 * FAILED에서 finish가 다시 오면 새 데이터로 처음부터 다시 생성한다(앱의 "다시 시도").
 */
public class Trip {

    /** (mock 생성) finish 이후 이 횟수만큼은 RUNNING, 그 다음 조회부터 DONE. */
    private static final int RUNNING_POLLS = 2;

    private final String id;
    /** 이 트립에 발급한 목적지. 리캡 영상에 장소명을 넣을 때 쓴다. 발급 후 바뀌지 않는다. */
    private final DestinationDto destination;
    private TripStatus status = TripStatus.PENDING;
    /** finish로 받은 주행 데이터. finish 전에는 null. */
    private FinishTripRequest finishData;
    private int recapPollCount = 0;
    /** DONE이 되면 한 번 만들어지는 결과(공유 토큰·요약). 그 전에는 null. */
    private Recap recap;

    public Trip(String id, DestinationDto destination) {
        this.id = id;
        this.destination = destination;
    }

    public String getId() {
        return id;
    }

    public DestinationDto getDestination() {
        return destination;
    }

    public synchronized FinishTripRequest getFinishData() {
        return finishData;
    }

    /**
     * finish 처리.
     * - 처음 finish: 데이터를 저장하고 리캡 생성을 시작(PENDING).
     * - FAILED 상태: 새 데이터로 바꾸고 처음부터 다시 생성(PENDING).
     * - 그 외(이미 PENDING/RUNNING/DONE): 아무것도 바꾸지 않고 현재 상태만 반환(멱등, 데이터도 그대로).
     */
    public synchronized TripStatus finish(FinishTripRequest data) {
        if (finishData == null || status == TripStatus.FAILED) {
            finishData = data;
            status = TripStatus.PENDING;
            recapPollCount = 0;
        }
        return status;
    }

    /** recap 조회 1회를 기록하고, 그 결과 상태를 반환한다. finish 전이거나 끝난 상태(DONE/FAILED)면 그대로. */
    public synchronized TripStatus pollRecap() {
        if (finishData == null || status == TripStatus.DONE || status == TripStatus.FAILED) {
            return status;
        }
        recapPollCount++;
        status = recapPollCount <= RUNNING_POLLS ? TripStatus.RUNNING : TripStatus.DONE;
        return status;
    }

    /** 리캡 생성 실패. 실제 영상 생성기가 생기면 생성 작업이 부른다. finish 전에는 의미가 없어 무시한다. */
    public synchronized void fail() {
        if (finishData != null && status != TripStatus.DONE) {
            status = TripStatus.FAILED;
        }
    }

    public synchronized TripStatus getStatus() {
        return status;
    }

    public synchronized Recap getRecap() {
        return recap;
    }

    /**
     * DONE 상태일 때 결과를 한 번만 만든다. 동시에 여러 조회가 와도 토큰은 하나만 발급된다.
     * DONE이 아니면 null.
     */
    public synchronized Recap completeRecap(java.util.function.Function<Trip, Recap> factory) {
        if (status != TripStatus.DONE) {
            return null;
        }
        if (recap == null) {
            recap = factory.apply(this);
        }
        return recap;
    }
}
