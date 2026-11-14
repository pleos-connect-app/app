#!/usr/bin/env python3
"""에뮬레이터 가상 GPS로 주행을 흉내 낸다 (앱의 위치 기록·도착 판정·리캡 시연용).

에뮬레이터 내비는 가상 GPS를 인식하지 않지만 앱(LocationManager)은 받는다. 1초마다 `adb emu geo fix`를 보낸다.

■ 따라가기 모드 (시연 추천) — 앱에서 지금 진행 중인 여행을 읽어 알아서 달린다(debug 빌드, run-as 사용)
    tools/emu-drive.py --follow
  - 출발지 → (경유지들) → 목적지를 실제 도로(OSRM)로 달리고, 목적지에서 머물러 앱이 도착을 판정하게 한다.
  - 달리는 중에 내비에서 경유지를 추가하면 몇 초 안에 알아채고 현재 위치에서 경유지를 거쳐 가도록 경로를 바꾼다.
    경유지에서는 잠깐 머물러 앱이 경유지 도착을 기록하게 한다.
  - 속도는 --kmh(가상 시속, 기본 500 — 30km를 약 4분), 중간 정차는 --stop-after-km 5 --stop-seconds 320
    (예상 밖 정차는 5분 이상 머물러야 센다).

■ 좌표 지정 모드
    서울역 → 남한산성, 90초 주행, 도착 후 20초 머무름:
        tools/emu-drive.py 37.5547 126.9707 37.4786 127.1817 --seconds 90 --dwell 20
    실제 도로를 따라: --road        경유지 거치기: --road --via 37.5262,126.8752 (여러 번 가능)
    중간(50%)에서 6분 정차: --stop-at 0.5 --stop-seconds 360

도로 경로는 OSRM 공개 데모 서버(router.project-osrm.org)를 쓴다. 시연용이며 상용 앱에서 쓰면 안 된다.
"""
import argparse, json, math, os, subprocess, time

ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
PKG = os.environ.get("PKG", "com.gyeongroital.gallaemallae")
USER = os.environ.get("AAOS_USER", "10")  # AAOS 사용자 ID


def fix(lat, lng):
    # geo fix 인자 순서는 경도, 위도
    subprocess.run([ADB, "emu", "geo", "fix", f"{lng:.6f}", f"{lat:.6f}"], check=True, capture_output=True)


def meters(a, b):
    lat = math.radians((a[0] + b[0]) / 2)
    return math.hypot((b[0] - a[0]) * 111_320, (b[1] - a[1]) * 111_320 * math.cos(lat))


def road_route(stops):
    """stops [(lat, lng), ...]를 차례로 지나는 자동차 경로. (경로 좌표들, 각 구간 끝까지의 누적 거리 m)."""
    coords = ";".join(f"{lng},{lat}" for lat, lng in stops)
    url = f"https://router.project-osrm.org/route/v1/driving/{coords}?overview=full&geometries=geojson"
    # macOS 기본 python3(LibreSSL)는 OSRM과 TLS 핸드셰이크가 안 돼 curl로 받는다.
    d = json.loads(subprocess.run(["curl", "-sf", "-m", "20", url], check=True, capture_output=True, text=True).stdout)
    if d.get("code") != "Ok":
        raise SystemExit(f"OSRM 경로 실패: {d.get('code')} {d.get('message', '')}")
    route = d["routes"][0]
    print(f"도로 경로 {route['distance'] / 1000:.1f}km (실제 예상 {route['duration'] / 60:.0f}분)", flush=True)
    path = [(lat, lng) for lng, lat in route["geometry"]["coordinates"]]
    # 구간(leg) 끝 = 경유지. OSRM 구간 거리와 경로 길이가 조금 달라 비율로 맞춘다.
    total = sum(meters(a, b) for a, b in zip(path, path[1:]))
    legs, run = [], 0.0
    for leg in route["legs"]:
        run += leg["distance"]
        legs.append(run * total / route["distance"] if route["distance"] else 0)
    return path, legs


def straight(stops, n=200):
    path = []
    for a, b in zip(stops, stops[1:]):
        path += [(a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n) for i in range(n)]
    path.append(stops[-1])
    total, legs, run = 0.0, [], 0.0
    for a, b in zip(stops, stops[1:]):
        run += meters(a, b)
        legs.append(run)
    return path, legs


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


def dwell(lat, lng, seconds, label):
    if seconds > 0:
        print(f"  {label} {seconds}s", flush=True)
    for _ in range(seconds):
        fix(lat, lng)
        time.sleep(1)


# ------------------------------------------------------------------ 좌표 지정 모드

def run_fixed(a):
    via = [tuple(map(float, v.split(","))) for v in a.via]
    stops = [(a.from_lat, a.from_lng), *via, (a.to_lat, a.to_lng)]
    path, legs = road_route(stops) if a.road else straight(stops)
    total = legs[-1] if legs else 0
    points = resample(path, a.seconds)
    step = total / a.seconds if a.seconds else 0
    leg_steps = {min(a.seconds, round(d / step)) if step else 0: via[k] for k, d in enumerate(legs[:-1])}
    stop_step = int(a.stop_at * a.seconds) if a.stop_at is not None else -1
    for i, (lat, lng) in enumerate(points):
        fix(lat, lng)
        print(f"[{i:4d}/{a.seconds}] {lat:.5f},{lng:.5f}", flush=True)
        if i in leg_steps:
            w = leg_steps[i]
            dwell(w[0], w[1], a.waypoint_dwell, "경유지 머무름")
        if i == stop_step and a.stop_seconds > 0:
            dwell(lat, lng, a.stop_seconds, "정차")
        time.sleep(1)
    dwell(a.to_lat, a.to_lng, a.dwell, "목적지 머무름")
    print("done", flush=True)


# ------------------------------------------------------------------ 따라가기 모드

def read_session():
    r = subprocess.run([ADB, "shell", f"run-as {PKG} --user {USER} cat files/trip/session.json"],
                       capture_output=True, text=True)
    if r.returncode != 0 or not r.stdout.strip().startswith("{"):
        return None
    try:
        return json.loads(r.stdout)
    except json.JSONDecodeError:
        return None


def known(lat, lng):
    return not (abs(lat) < 1e-6 and abs(lng) < 1e-6)


def pending_waypoints(session, visited):
    """좌표를 아는, 아직 들르지 않은 경유지(추가된 순서). 이름 기준 중복 제거."""
    out, seen = [], set()
    for e in session.get("events", []):
        name = e.get("name") or "경유지"
        if e["type"] != "WAYPOINT_ADDED" or name in seen:
            continue
        seen.add(name)
        if name in visited:
            continue
        if known(e["lat"], e["lng"]):
            out.append((name, (e["lat"], e["lng"])))
    return out


def run_follow(a):
    s = read_session()
    if not s or s.get("phase") != "RECORDING":
        raise SystemExit("기록 중인 여행이 없어요. 앱에서 목적지를 뽑고 '내비게이션 시작'을 누른 뒤 다시 실행하세요"
                         " (debug 빌드여야 run-as로 읽을 수 있어요).")
    dest_name = s["destination"]["name"]
    dest = (s["destination"]["lat"], s["destination"]["lng"])
    pos = (s["originLat"], s["originLng"])
    step = a.kmh / 3.6  # 1초에 가는 거리(m)
    visited, warned = set(), set()
    driven, stopped = 0.0, a.stop_after_km is None
    print(f"따라가기: {s['originLabel']} → {dest_name}, 가상 시속 {a.kmh:.0f}km", flush=True)

    while True:
        wps = pending_waypoints(s, visited)
        for e in s.get("events", []):
            n = e.get("name") or "경유지"
            if e["type"] == "WAYPOINT_ADDED" and not known(e["lat"], e["lng"]) and n not in warned:
                warned.add(n)
                print(f"  ! 경유지 '{n}' 좌표를 아직 몰라요(서버 검색 대기 또는 실패) — 좌표가 생기면 거쳐 갑니다", flush=True)
        if wps:
            print("  경로: " + " → ".join([n for n, _ in wps] + [dest_name]), flush=True)
        path, legs = road_route([pos, *[c for _, c in wps], dest])
        n = max(1, int(legs[-1] // step))
        points = resample(path, n)
        leg_idx = [min(n, round(d / step)) for d in legs[:-1]]
        known_names = {nm for nm, _ in pending_waypoints(s, set())}
        replan = False
        for i, p in enumerate(points[1:], start=1):
            fix(*p)
            driven += meters(pos, p)
            pos = p
            if i % 10 == 0 or i == n:
                print(f"[{i:4d}/{n}] {p[0]:.5f},{p[1]:.5f} · {driven / 1000:.1f}km", flush=True)
            if not stopped and driven >= a.stop_after_km * 1000:
                stopped = True
                dwell(p[0], p[1], a.stop_seconds, "정차")
            if i in leg_idx:
                k = leg_idx.index(i)
                name, at = wps[k]
                dwell(at[0], at[1], a.waypoint_dwell, f"경유지 '{name}' 머무름")
                visited.add(name)
                pos = at
            time.sleep(1)
            if i % a.poll == 0:
                s2 = read_session()
                if not s2 or s2.get("phase") != "RECORDING":
                    print("여행 기록이 끝나 멈춥니다", flush=True)
                    return
                s = s2
                now = {nm for nm, _ in pending_waypoints(s, set())}
                if now - known_names - visited:
                    print(f"  + 새 경유지: {', '.join(sorted(now - known_names - visited))} — 경로를 다시 짭니다", flush=True)
                    replan = True
                    break
        if not replan:
            break
    dwell(dest[0], dest[1], a.dwell, "목적지 머무름")
    print("done — 앱에서 '리캡 영상 만들기'를 누르세요", flush=True)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("from_lat", type=float, nargs="?"); p.add_argument("from_lng", type=float, nargs="?")
    p.add_argument("to_lat", type=float, nargs="?"); p.add_argument("to_lng", type=float, nargs="?")
    p.add_argument("--follow", action="store_true", help="앱에서 진행 중인 여행을 읽어 주행(경유지 추가에 맞춰 경로 변경)")
    p.add_argument("--kmh", type=float, default=500, help="따라가기: 가상 시속(기본 500)")
    p.add_argument("--poll", type=int, default=3, help="따라가기: 새 경유지 확인 간격(초)")
    p.add_argument("--stop-after-km", type=float, default=None, help="따라가기: 이만큼 달린 뒤 한 번 정차")
    p.add_argument("--seconds", type=int, default=90, help="좌표 지정: 주행 시간(초) = 보내는 점 개수")
    p.add_argument("--via", action="append", default=[], help="좌표 지정: 경유지 '위도,경도' (여러 번 가능)")
    p.add_argument("--road", action="store_true", help="좌표 지정: 직선 대신 실제 도로 경로(OSRM)")
    p.add_argument("--stop-at", type=float, default=None, help="좌표 지정: 중간 정차 위치(0~1)")
    p.add_argument("--stop-seconds", type=int, default=320, help="정차 시간(초). 예상 밖 정차는 5분 이상")
    p.add_argument("--dwell", type=int, default=25, help="목적지에 머무는 시간(초) — 앱 도착 판정은 10초")
    p.add_argument("--waypoint-dwell", type=int, default=15, help="경유지에 머무는 시간(초) — 앱 판정은 10초")
    a = p.parse_args()
    if a.follow:
        run_follow(a)
    elif None in (a.from_lat, a.from_lng, a.to_lat, a.to_lng):
        p.error("출발·도착 좌표 4개를 주거나 --follow를 쓰세요")
    else:
        if a.stop_at is None:
            a.stop_seconds = 0
        run_fixed(a)


if __name__ == "__main__":
    main()
