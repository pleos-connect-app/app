package com.randomdrive.server.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.randomdrive.server.exception.ApiException;
import com.randomdrive.server.recap.VWorldClient;
import com.randomdrive.server.service.MapProxyService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** 앱 지도 중계(VWorld 키는 서버에만). 위성 타일과 좌표 → 지역 이름. */
@RestController
@RequestMapping("/api/v1")
public class MapController {

    private final MapProxyService maps;

    public MapController(MapProxyService maps) {
        this.maps = maps;
    }

    /** GET /api/v1/map/satellite/{z}/{x}/{y}.jpeg → image/jpeg */
    @GetMapping("/map/satellite/{z}/{x}/{y}.jpeg")
    public ResponseEntity<byte[]> satellite(@PathVariable int z, @PathVariable int x, @PathVariable int y) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                .body(maps.satelliteTile(z, x, y));
    }

    /** GET /api/v1/geo/area?lat=&lng= → {"full", "short"} (못 찾으면 둘 다 null) */
    @GetMapping("/geo/area")
    public Area area(@RequestParam double lat, @RequestParam double lng) {
        if (!(lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "lat/lng: out of range");
        }
        VWorldClient.Area a = maps.area(lat, lng);
        return a == null ? new Area(null, null) : new Area(a.full(), a.shortName());
    }

    /** GET /api/v1/geo/search?query= → {"lat", "lng"} (못 찾으면 둘 다 null). 경유지 좌표 보완용. */
    @GetMapping("/geo/search")
    public Coord search(@RequestParam String query) {
        double[] c = maps.search(query);
        return c == null ? new Coord(null, null) : new Coord(c[0], c[1]);
    }

    public record Area(String full, @JsonProperty("short") String shortName) {
    }

    public record Coord(Double lat, Double lng) {
    }
}
