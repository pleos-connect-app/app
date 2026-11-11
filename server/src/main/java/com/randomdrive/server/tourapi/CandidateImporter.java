package com.randomdrive.server.tourapi;

import com.randomdrive.server.service.Candidate;
import com.randomdrive.server.service.CandidateRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * TourAPI 관광지 목록 → 랜덤 목적지 후보 변환 (순수 로직, 네트워크 없음).
 *
 * 후보 기준(2026-11-11 결정, "드라이브 목적지만"):
 * - 대표 이미지가 있는 곳만 (앱 결과 화면·리캡 영상에 쓰고, 정보가 관리되는 장소라는 신호)
 * - 쇼핑(SH)·숙박(AC) 대분류 제외
 * - 도시공원(VE03)·교육시설(VE09)·교통시설(VE11)·기타문화관광지(VE12) 중분류 제외 — 동네 공원 등 드라이브 목적지로 부적합
 * - 좌표가 한국 범위 밖이면 제외 (TourAPI에 경도 117.99/위도 19.69 같은 잘못된 기본값이 섞여 있음)
 * - contentId·이름·주소·좌표가 비면 제외, contentId 중복 제외
 */
public final class CandidateImporter {

    /** 관광지. 문화시설(14)·레포츠(28)는 MVP에서 제외. */
    public static final int CONTENT_TYPE_TOURIST_SPOT = 12;

    static final Set<String> EXCLUDED_LCLS1 = Set.of("SH", "AC");
    static final Set<String> EXCLUDED_LCLS2 = Set.of("VE03", "VE09", "VE11", "VE12");

    /** 제주(마라도 33.1)·백령도(124.6)·독도(131.9)·고성(38.6)을 포함하는 사각형. */
    static final double MIN_LAT = 33.0;
    static final double MAX_LAT = 38.7;
    static final double MIN_LNG = 124.0;
    static final double MAX_LNG = 132.0;

    /** 시연용 고정 장소. 앱 assets/candidates.json의 demo-namhansanseong과 같은 값. */
    public static final Candidate DEMO = new Candidate(CandidateRepository.DEMO_PLACE_ID, "남한산성",
            37.4786, 127.1817, "경기도 광주시 남한산성면", null, null, true);

    private CandidateImporter() {
    }

    /** 제외 사유. 가져오기 결과 로그에 건수로 남긴다. */
    public enum SkipReason {
        MISSING_FIELD, OUT_OF_KOREA, NO_IMAGE, EXCLUDED_CATEGORY, DUPLICATE
    }

    public record Result(List<Candidate> candidates, Map<SkipReason, Integer> skipped) {
    }

    public static Result convert(List<TourItem> items) {
        List<Candidate> candidates = new ArrayList<>();
        Map<SkipReason, Integer> skipped = new TreeMap<>();
        Set<String> seen = new HashSet<>();
        for (TourItem item : items) {
            SkipReason reason = skipReason(item, seen);
            if (reason != null) {
                skipped.merge(reason, 1, Integer::sum);
                continue;
            }
            candidates.add(toCandidate(item));
        }
        // placeId 순으로 정렬해 다시 가져와도 순서가 같게 한다(git diff가 실제 변경만 보이도록).
        candidates.sort(Comparator.comparing(Candidate::placeId));
        return new Result(List.copyOf(candidates), skipped);
    }

    private static SkipReason skipReason(TourItem item, Set<String> seen) {
        if (isBlank(item.contentId()) || isBlank(item.title()) || isBlank(item.addr1())) {
            return SkipReason.MISSING_FIELD;
        }
        Double lat = parse(item.mapy());
        Double lng = parse(item.mapx());
        if (lat == null || lng == null) {
            return SkipReason.MISSING_FIELD;
        }
        if (lat < MIN_LAT || lat > MAX_LAT || lng < MIN_LNG || lng > MAX_LNG) {
            return SkipReason.OUT_OF_KOREA;
        }
        if (isBlank(item.firstImage())) {
            return SkipReason.NO_IMAGE;
        }
        if (EXCLUDED_LCLS1.contains(item.lclsSystm1()) || EXCLUDED_LCLS2.contains(item.lclsSystm2())) {
            return SkipReason.EXCLUDED_CATEGORY;
        }
        if (!seen.add(item.contentId())) {
            return SkipReason.DUPLICATE;
        }
        return null;
    }

    private static Candidate toCandidate(TourItem item) {
        return new Candidate(
                item.contentId(),
                item.title(),
                parse(item.mapy()),
                parse(item.mapx()),
                item.addr1(),
                toHttps(item.firstImage()),
                isBlank(item.cpyrhtDivCd()) ? null : item.cpyrhtDivCd(),
                true);
    }

    /**
     * TourAPI 이미지 서버(tong.visitkorea.or.kr)는 https도 같은 이미지를 준다(2026-11-11 확인).
     * 앱 릴리스 빌드는 https만 허용하므로 http 주소를 https로 바꾼다.
     */
    static String toHttps(String url) {
        return url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
    }

    /**
     * 후보 파일 내용을 만든다. 한 줄에 후보 하나라 다시 가져왔을 때 바뀐 장소만 diff에 보인다.
     * active는 모두 true라 쓰지 않는다(필요하면 손으로 "active": false를 넣는 용도).
     */
    public static String render(JsonMapper jsonMapper, List<Candidate> candidates, LocalDate generatedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"_comment\": \"").append(escape("자동 생성 파일 — 직접 고치지 말고 server에서 ./gradlew importCandidates로 다시 만든다. "
                + "demo는 demo=true 요청이 항상 주는 시연용 장소, candidates는 랜덤 추첨 후보(F-01).")).append("\",\n");
        sb.append("  \"source\": \"").append(escape("한국관광공사 TourAPI (KorService2 areaBasedList2, contentTypeId="
                + CONTENT_TYPE_TOURIST_SPOT + ")")).append("\",\n");
        sb.append("  \"generatedAt\": \"").append(generatedAt).append("\",\n");
        sb.append("  \"demo\": ").append(jsonMapper.writeValueAsString(toJson(DEMO))).append(",\n");
        sb.append("  \"candidates\": [\n");
        for (int i = 0; i < candidates.size(); i++) {
            sb.append("    ").append(jsonMapper.writeValueAsString(toJson(candidates.get(i))));
            sb.append(i < candidates.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static Map<String, Object> toJson(Candidate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("placeId", c.placeId());
        m.put("name", c.name());
        m.put("lat", c.lat());
        m.put("lng", c.lng());
        m.put("address", c.address());
        if (c.imageUrl() != null) {
            m.put("imageUrl", c.imageUrl());
        }
        if (c.imageLicense() != null) {
            m.put("imageLicense", c.imageLicense());
        }
        if (!c.active()) {
            m.put("active", false);
        }
        return m;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Double parse(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            double v = Double.parseDouble(s);
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
