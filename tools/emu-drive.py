#!/usr/bin/env python3
"""에뮬레이터 가상 GPS로 주행을 흉내 낸다 (앱의 LocationManager 기록·도착 판정 시연용).

에뮬레이터 내비는 가상 GPS를 인식하지 않지만 앱(LocationManager)은 받는다.
출발지 → 목적지를 직선으로 나눠 1초마다 `adb emu geo fix`를 보낸다.

예) 서울역 → 남한산성, 90초 주행, 중간에 정차 없이, 도착 후 20초 머무름
    tools/emu-drive.py 37.5547 126.9707 37.4786 127.1817 --seconds 90 --dwell 20
    중간(50%)에서 6분 정차:  --stop-at 0.5 --stop-seconds 360
"""
import argparse, os, subprocess, time

ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))

def fix(lat, lng):
    # geo fix 인자 순서는 경도, 위도
    subprocess.run([ADB, "emu", "geo", "fix", f"{lng:.6f}", f"{lat:.6f}"], check=True, capture_output=True)

def main():
    p = argparse.ArgumentParser()
    p.add_argument("from_lat", type=float); p.add_argument("from_lng", type=float)
    p.add_argument("to_lat", type=float); p.add_argument("to_lng", type=float)
    p.add_argument("--seconds", type=int, default=90, help="주행 시간(초) = 보내는 점 개수")
    p.add_argument("--dwell", type=int, default=20, help="도착 후 목적지에 머무는 시간(초)")
    p.add_argument("--stop-at", type=float, default=None, help="중간 정차 위치(0~1)")
    p.add_argument("--stop-seconds", type=int, default=0)
    a = p.parse_args()
    stop_step = int(a.stop_at * a.seconds) if a.stop_at is not None else -1
    for i in range(a.seconds + 1):
        t = i / a.seconds
        lat = a.from_lat + (a.to_lat - a.from_lat) * t
        lng = a.from_lng + (a.to_lng - a.from_lng) * t
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
