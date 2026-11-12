package com.randomdrive.server.recap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 영상 한 편이 쓰는 지도 타일(픽셀 배열). 렌더링 전에 필요한 타일을 병렬로 미리 받아 두고,
 * 렌더링 중에는 네트워크를 쓰지 않는다(없는 타일은 격자 바닥으로 그린다). 영상이 끝나면 버린다.
 */
final class TileCache {

    private static final Logger log = LoggerFactory.getLogger(TileCache.class);
    private static final int PARALLEL = 8;
    private static final long FETCH_BUDGET_S = 60;

    private final Map<Long, int[]> tiles = new ConcurrentHashMap<>();

    static long key(int z, int x, int y) {
        return ((long) z << 48) | ((long) x << 24) | y;
    }

    int[] get(int z, int x, int y) {
        return tiles.get(key(z, x, y));
    }

    int size() {
        return tiles.size();
    }

    /** keys의 타일을 source에서 받는다(최대 max장). source가 null이면 아무것도 안 한다. */
    void prefetch(Collection<Long> keys, MapView.TileSource source, int max) {
        if (source == null || keys.isEmpty()) {
            return;
        }
        List<Long> todo = new ArrayList<>(keys);
        if (todo.size() > max) {
            log.warn("recap map needs {} tiles, fetching only {}", todo.size(), max);
            todo = todo.subList(0, max);
        }
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL, r -> {
            Thread t = new Thread(r, "recap-tiles");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (long k : todo) {
                int z = (int) (k >>> 48), x = (int) ((k >>> 24) & 0xFFFFFF), y = (int) (k & 0xFFFFFF);
                futures.add(pool.submit(() -> {
                    BufferedImage img = source.tile(z, x, y);
                    if (img != null && img.getWidth() == MapView.TILE && img.getHeight() == MapView.TILE) {
                        tiles.put(k, img.getRGB(0, 0, MapView.TILE, MapView.TILE, null, 0, MapView.TILE));
                    }
                }));
            }
            pool.shutdown();
            if (!pool.awaitTermination(FETCH_BUDGET_S, TimeUnit.SECONDS)) {
                log.warn("recap map tile fetch timed out; drawing missing tiles as grid");
                futures.forEach(f -> f.cancel(true));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
    }
}
