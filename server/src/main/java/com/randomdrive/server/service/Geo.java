package com.randomdrive.server.service;

/** 좌표 계산 유틸. 앱의 Geo.kt와 같은 공식(하버사인, 지구 반지름 6,371km)을 써서 숫자가 맞게 한다. */
public final class Geo {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Geo() {
    }

    /** 두 좌표 사이 직선(대원) 거리, km. */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        // 부동소수 오차로 h가 [0,1]을 살짝 벗어나면 asin이 NaN이 되므로 잘라낸다.
        h = Math.min(1.0, Math.max(0.0, h));
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }
}
