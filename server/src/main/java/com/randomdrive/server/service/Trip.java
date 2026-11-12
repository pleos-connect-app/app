package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;
import com.randomdrive.server.dto.FinishTripRequest;
import com.randomdrive.server.dto.TripStatus;

/**
 * 트립 한 건의 상태와 규칙. DB에 저장되지만(TripStore) 상태를 바꾸는 규칙은 여기에만 있다.
 * 서버가 한 대라 같은 트립은 메모리에 객체 하나만 두고, 여러 요청·생성 작업이 동시에 건드리므로 synchronized.
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

    /** DB에 저장할 값을 한 번에(잠금 안에서) 읽은 것. */
    public record Snapshot(String id, DestinationDto destination, TripStatus status, int generation,
                           boolean finished, Long endedAt, Recap recap) {
    }

    private final String id;
    /** 이 트립에 발급한 목적지. 리캡 영상에 장소명을 넣을 때 쓴다. 발급 후 바뀌지 않는다. */
    private final DestinationDto destination;
    private TripStatus status = TripStatus.PENDING;
    private boolean finished;
    /** 사용자가 여행 종료를 누른 시각(공유 페이지 날짜). finish 전에는 null. */
    private Long endedAt;
    /** finish로 받은 주행 데이터. 생성에만 필요하므로 DONE이 되면 메모리에서 내려놓는다(DB에는 남음). */
    private FinishTripRequest finishData;
    private int generation = 0;
    /** DONE이 되면 한 번 만들어지는 결과(공유 토큰·요약·미디어). 그 전에는 null. */
    private Recap recap;

    public Trip(String id, DestinationDto destination) {
        this.id = id;
        this.destination = destination;
    }

    /**
     * DB에서 되살린다. 생성 중(RUNNING)이던 트립은 서버가 꺼지며 작업이 사라졌으므로 대기(PENDING)로 돌려
     * 다시 생성되게 한다.
     */
    public static Trip restore(String id, DestinationDto destination, TripStatus status, int generation,
                               boolean finished, Long endedAt, FinishTripRequest finishData, Recap recap) {
        Trip trip = new Trip(id, destination);
        trip.status = status == TripStatus.RUNNING ? TripStatus.PENDING : status;
        trip.generation = generation;
        trip.finished = finished;
        trip.endedAt = endedAt;
        trip.finishData = finishData;
        trip.recap = recap;
        return trip;
    }

    public String getId() {
        return id;
    }

    public DestinationDto getDestination() {
        return destination;
    }

    /** 생성 대기·중·실패 상태에서만 값이 있다(DONE이면 null). */
    public synchronized FinishTripRequest getFinishData() {
        return finishData;
    }

    public synchronized Long getEndedAt() {
        return endedAt;
    }

    public synchronized int getGeneration() {
        return generation;
    }

    /** finish 이후 생성을 기다리는 중인지(서버 재시작 후 다시 시작할 대상). */
    public synchronized boolean awaitsGeneration() {
        return finished && status == TripStatus.PENDING && finishData != null;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(id, destination, status, generation, finished, endedAt, recap);
    }

    /**
     * finish 처리.
     * - 처음 finish: 데이터를 저장하고 생성 작업을 새로 시작(PENDING).
     * - FAILED 상태: 새 데이터로 바꾸고 처음부터 다시 생성(PENDING).
     * - 그 외(이미 PENDING/RUNNING/DONE): 아무것도 바꾸지 않고 현재 상태만 반환(멱등, 데이터도 그대로).
     */
    public synchronized FinishOutcome finish(FinishTripRequest data) {
        if (!finished || status == TripStatus.FAILED) {
            finished = true;
            finishData = data;
            endedAt = data.endedAt();
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
        if (gen != generation || status != TripStatus.PENDING || finishData == null) {
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
        finishData = null;
        return true;
    }

    /** 생성 실패. finish 후 이 세대가 대기·진행 중일 때만 FAILED로 바꾼다. */
    public synchronized void fail(int gen) {
        if (finished && gen == generation
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
