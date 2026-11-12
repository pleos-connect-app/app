package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.TripStatus;

/**
 * 메모리에 저장되는 트립 한 건. (나중에 DB가 생기면 Entity가 될 자리)
 * 여러 요청과 생성 작업이 동시에 같은 트립을 건드릴 수 있어 상태를 읽고 바꾸는 메서드는 synchronized.
 *
 * 리캡 상태 흐름: (finish 전) PENDING → finish → PENDING(작업 대기) → RUNNING(생성 중) → DONE 또는 FAILED
 * FAILED에서 finish가 다시 오면 새 데이터로 처음부터 다시 생성한다(앱의 "다시 시도").
 *
 * 생성 작업은 finish 때 받은 "세대(generation)" 번호를 들고 다닌다. FAILED 후 다시 시작하면 세대가 바뀌므로,
 * 늦게 끝난 옛 작업의 결과·실패가 새 작업 상태를 덮어쓰지 않는다.
 */
public class Trip {

    /**
     * finish 처리 결과.
     *
     * @param status        응답에 줄 상태
     * @param newGeneration 새로 생성 작업을 시작해야 하면 그 세대 번호, 아니면 null(멱등 재전송)
     */
    public record FinishOutcome(TripStatus status, Integer newGeneration) {
    }

    private final String id;
    /** 이 트립에 발급한 목적지. 리캡 영상에 장소명을 넣을 때 쓴다. 발급 후 바뀌지 않는다. */
    private final DestinationDto destination;
    private TripStatus status = TripStatus.PENDING;
    /** finish로 받은 주행 데이터. finish 전에는 null. */
    private FinishTripRequest finishData;
    private int generation = 0;
    /** DONE이 되면 한 번 만들어지는 결과(공유 토큰·요약·미디어). 그 전에는 null. */
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
     * - 처음 finish: 데이터를 저장하고 생성 작업을 새로 시작(PENDING).
     * - FAILED 상태: 새 데이터로 바꾸고 처음부터 다시 생성(PENDING).
     * - 그 외(이미 PENDING/RUNNING/DONE): 아무것도 바꾸지 않고 현재 상태만 반환(멱등, 데이터도 그대로).
     */
    public synchronized FinishOutcome finish(FinishTripRequest data) {
        if (finishData == null || status == TripStatus.FAILED) {
            finishData = data;
            status = TripStatus.PENDING;
            generation++;
            return new FinishOutcome(status, generation);
        }
        return new FinishOutcome(status, null);
    }

    /**
     * 생성 작업 시작. 이 세대가 아직 유효하고 대기 중이면 RUNNING으로 바꾸고 주행 데이터를 돌려준다.
     * 이미 다른 세대로 바뀌었으면 null(작업은 아무것도 하지 말고 끝낸다).
     */
    public synchronized FinishTripRequest startGenerating(int gen) {
        if (gen != generation || status != TripStatus.PENDING) {
            return null;
        }
        status = TripStatus.RUNNING;
        return finishData;
    }

    /** 생성 성공. 이 세대가 진행 중일 때만 DONE으로 바꾸고 true. */
    public synchronized boolean complete(int gen, Recap result) {
        if (gen != generation || status != TripStatus.RUNNING) {
            return false;
        }
        recap = result;
        status = TripStatus.DONE;
        return true;
    }

    /** 생성 실패. finish 후 이 세대가 대기·진행 중일 때만 FAILED로 바꾼다. */
    public synchronized void fail(int gen) {
        if (finishData != null && gen == generation
                && (status == TripStatus.PENDING || status == TripStatus.RUNNING)) {
            status = TripStatus.FAILED;
        }
    }

    public synchronized TripStatus getStatus() {
        return status;
    }

    public synchronized Recap getRecap() {
        return recap;
    }
}
