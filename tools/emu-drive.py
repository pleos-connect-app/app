#!/usr/bin/env python3
"""에뮬레이터 가상 GPS로 주행을 흉내 낸다 (앱의 LocationManager 기록·도착 판정 시연용).

에뮬레이터 내비는 가상 GPS를 인식하지 않지만 앱(LocationManager)은 받는다.
출발지 → 목적지를 직선으로 나눠 1초마다 `adb emu geo fix`를 보낸다.

예) 서울역 → 남한산성, 90초 주행, 중간에 정차 없이, 도착 후 20초 머무름
    tools/emu-drive.py 37.5547 126.9707 37.4786 127.1817 --seconds 90 --dwell 20
    중간(50%)에서 6분 정차:  --stop-at 0.5 --stop-seconds 360
    실제 도로를 따라 주행(시연·리캡 영상용): --road  (OSRM 공개 데모 서버로 경로를 받아 같은 속도로 나눔)
"""
import argparse, json, math, os, subprocess, time

ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))

def fix(lat, lng):
    # geo fix 인자 순서는 경도, 위도
    subprocess.run([ADB, "emu", "geo", "fix", f"{lng:.6f}", f"{lat:.6f}"], check=True, capture_output=True)

def road_route(a_lat, a_lng, b_lat, b_lng):
    """OSRM 공개 데모 서버(router.project-osrm.org)의 자동차 경로 좌표 [(lat, lng), ...]. 시연용 — 상용 앱에서 쓰지 말 것."""
    url = (f"https://router.project-osrm.org/route/v1/driving/{a_lng},{a_lat};{b_lng},{b_lat}"
           "?overview=full&geometries=geojson")
    # macOS 기본 python3(LibreSSL)는 OSRM과 TLS 핸드셰이크가 안 돼 curl로 받는다.
    d = json.loads(subprocess.run(["curl", "-sf", "-m", "20", url], check=True, capture_output=True, text=True).stdout)
    if d.get("code") != "Ok":
        raise SystemExit(f"OSRM 경로 실패: {d.get('code')} {d.get('message', '')}")
    route = d["routes"][0]
    print(f"도로 경로 {route['distance'] / 1000:.1f}km (실제 예상 {route['duration'] / 60:.0f}분)", flush=True)
    return [(lat, lng) for lng, lat in route["geometry"]["coordinates"]]

def meters(a, b):
    lat = math.radians((a[0] + b[0]) / 2)
    return math.hypot((b[0] - a[0]) * 111_320, (b[1] - a[1]) * 111_320 * math.cos(lat))

def resample(path, n):
    """경로를 같은 거리 간격 n+1개 점으로 나눈다(= 1초마다 같은 속도)."""
    cum = [0.0]
    for a, b in zip(path, path[1:]):
        cum.append(cum[-1] + meters(a, b))
    total, out, j = cum[-1], [], 0
    for i in range(n + 1):
        target = total * i / n
        while j < len(path) - 2 and cum[j + 1] < target:
            j += 1
        seg = cum[j + 1] - cum[j]
        t = 0.0 if seg == 0 else (target - cum[j]) / seg
        out.append((path[j][0] + (path[j + 1][0] - path[j][0]) * t, path[j][1] + (path[j + 1][1] - path[j][1]) * t))
    return out

def main():
    p = argparse.ArgumentParser()
    p.add_argument("from_lat", type=float); p.add_argument("from_lng", type=float)
    p.add_argument("to_lat", type=float); p.add_argument("to_lng", type=float)
    p.add_argument("--seconds", type=int, default=90, help="주행 시간(초) = 보내는 점 개수")
    p.add_argument("--dwell", type=int, default=20, help="도착 후 목적지에 머무는 시간(초)")
    p.add_argument("--stop-at", type=float, default=None, help="중간 정차 위치(0~1)")
    p.add_argument("--stop-seconds", type=int, default=0)
    p.add_argument("--road", action="store_true", help="직선 대신 실제 도로 경로(OSRM)를 따라 주행")
    a = p.parse_args()
    stop_step = int(a.stop_at * a.seconds) if a.stop_at is not None else -1
    if a.road:
        points = resample(road_route(a.from_lat, a.from_lng, a.to_lat, a.to_lng), a.seconds)
    else:
        points = [(a.from_lat + (a.to_lat - a.from_lat) * i / a.seconds, a.from_lng + (a.to_lng - a.from_lng) * i / a.seconds)
                  for i in range(a.seconds + 1)]
    for i, (lat, lng) in enumerate(points):
        fix(lat, lng)
        print(f"[{i:4d}/{a.seconds}] {lat:.5f},{lng:.5f}", flush=True)
        if i == stop_step and a.stop_seconds > 0:
            print(f"  정차 {a.stop_seconds}s", flush=True)
            for _ in range(a.stop_seconds):
                fix(lat, lng); time.sleep(1)
        time.sleep(1)
    for _ in range(a.dwell):
        fix(a.to_lat, a.to_lng); time.sleep(1)
    print("done", flush=True)

if __name__ == "__main__":
    main()
