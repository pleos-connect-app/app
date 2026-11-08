package com.randomdrive.server.service;

import com.randomdrive.server.dto.TripStatus;

/**
 * 메모리에 저장되는 트립 한 건. (나중에 DB가 생기면 Entity가 될 자리)
 * 여러 요청이 동시에 같은 트립을 건드릴 수 있어 상태를 바꾸는 메서드는 synchronized.
 */
public class Trip {

    /** finish 이후 이 횟수만큼은 RUNNING, 그 다음 조회부터 DONE. */
    private static final int RUNNING_POLLS = 2;

    private final String id;
    private TripStatus status = TripStatus.PENDING;
    private boolean finished = false;
    private int recapPollCount = 0;

    public Trip(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** finish 처리. 이미 finish된 트립이면 아무것도 바꾸지 않고 현재 상태만 반환(멱등). */
    public synchronized TripStatus finish() {
        finished = true;
        return status;
    }

    /** recap 조회 1회를 기록하고, 그 결과 상태를 반환한다. finish 전이면 PENDING 그대로. */
    public synchronized TripStatus pollRecap() {
        if (!finished) {
            return status;
        }
        recapPollCount++;
        status = recapPollCount <= RUNNING_POLLS ? TripStatus.RUNNING : TripStatus.DONE;
        return status;
    }
}
