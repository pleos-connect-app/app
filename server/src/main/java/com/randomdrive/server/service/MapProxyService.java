package com.randomdrive.server.service;

import com.randomdrive.server.exception.ApiException;
import com.randomdrive.server.recap.VWorldClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 앱 지도·지역 이름 중계. VWorld 인증키를 앱(APK)에 넣지 않고 서버에만 두기 위해, 앱은 이 서버를 거쳐 받는다.
 * - 위성 타일: 같은 지역을 여러 번 받지 않게 최근 타일을 메모리에 둔다(작은 서버라 장수 제한).
 * - 지역 이름: 좌표는 저장하지 않고 VWorld에 물어 바로 돌려준다.
 */
@Service
public class MapProxyService {

    public static final int MIN_ZOOM = 6;
    public static final int MAX_ZOOM = 18;
    /** 256px JPEG 한 장 약 10~40KB → 최대 약 15MB. */
    private static final int MAX_CACHED_TILES = 500;

    private final VWorldClient vworld;
    private final Map<String, byte[]> tiles = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > MAX_CACHED_TILES;
        }
    };

    public MapProxyService(VWorldClient vworld) {
        this.vworld = vworld;
    }

    /** 위성 타일 JPEG. 범위 밖이면 400, VWorld 키가 없거나 받지 못하면 503(앱은 지도 없는 배경으로 그린다). */
    public byte[] satelliteTile(int z, int x, int y) {
        long n = 1L << Math.max(0, Math.min(z, 30));
        if (z < MIN_ZOOM || z > MAX_ZOOM || x < 0 || y < 0 || x >= n || y >= n) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "지원하지 않는 지도 타일이에요.");
        }
        String key = z + "/" + x + "/" + y;
        synchronized (tiles) {
            byte[] hit = tiles.get(key);
            if (hit != null) {
                return hit;
            }
        }
        byte[] jpeg = vworld.satelliteTileBytes(z, x, y);
        if (jpeg == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAP_UNAVAILABLE", "지도를 불러오지 못했어요.");
        }
        synchronized (tiles) {
            tiles.put(key, jpeg);
        }
        return jpeg;
    }

    /** 장소 이름·주소 → 좌표 {lat, lng}. 못 찾으면 null. */
    public double[] search(String query) {
        if (query == null || query.isBlank() || query.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "query: 1~200자");
        }
        return vworld.search(query);
    }

    /** 좌표의 행정구역 이름. 바다·키 없음·실패면 null. */
    public VWorldClient.Area area(double lat, double lng) {
        return vworld.area(lat, lng);
    }
}
