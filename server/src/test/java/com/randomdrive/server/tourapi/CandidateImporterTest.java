package com.randomdrive.server.tourapi;

import com.randomdrive.server.service.Candidate;
import com.randomdrive.server.service.CandidateRepository;
import com.randomdrive.server.tourapi.CandidateImporter.SkipReason;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateImporterTest {

    private static final String IMG = "https://tong.visitkorea.or.kr/cms/resource/1.jpg";

    /** 기본값은 "후보로 들어가야 하는" 정상 항목. 테스트마다 한 필드만 바꿔 쓴다. */
    private static TourItem item(String id) {
        return new TourItem(id, "장소" + id, "경기도 어딘가", "127.1", "37.4", IMG, "Type1", "NA", "NA01");
    }

    private static TourItem with(TourItem t, String field, String value) {
        return new TourItem(
                field.equals("contentId") ? value : t.contentId(),
                field.equals("title") ? value : t.title(),
                field.equals("addr1") ? value : t.addr1(),
                field.equals("mapx") ? value : t.mapx(),
                field.equals("mapy") ? value : t.mapy(),
                field.equals("firstImage") ? value : t.firstImage(),
                field.equals("cpyrhtDivCd") ? value : t.cpyrhtDivCd(),
                field.equals("lclsSystm1") ? value : t.lclsSystm1(),
                field.equals("lclsSystm2") ? value : t.lclsSystm2());
    }

    @Test
    void 정상_항목은_후보로_변환된다_mapy가_위도_mapx가_경도() {
        CandidateImporter.Result result = CandidateImporter.convert(List.of(item("100")));

        assertThat(result.candidates()).containsExactly(
                new Candidate("100", "장소100", 37.4, 127.1, "경기도 어딘가", IMG, "Type1", true));
        assertThat(result.skipped()).isEmpty();
    }

    @Test
    void 사유별로_제외하고_건수를_센다() {
        List<TourItem> items = List.of(
                item("1"),
                with(item("2"), "firstImage", ""),                 // 이미지 없음
                with(item("3"), "lclsSystm2", "VE03"),             // 도시공원
                with(item("4"), "lclsSystm2", "VE09"),             // 교육시설
                with(item("5"), "lclsSystm2", "VE11"),             // 교통시설
                with(item("6"), "lclsSystm2", "VE12"),             // 기타문화관광지
                with(item("7"), "lclsSystm1", "SH"),               // 쇼핑
                with(item("8"), "lclsSystm1", "AC"),               // 숙박
                with(with(item("9"), "mapx", "117.9925662504"), "mapy", "19.6944274800"), // 실제로 있던 잘못된 좌표
                with(item("10"), "addr1", " "),                    // 주소 없음
                with(item("11"), "mapy", "abc"),                   // 좌표 숫자 아님
                with(item("12"), "title", ""),                     // 이름 없음
                item("1"),                                         // contentId 중복
                with(item("13"), "lclsSystm2", "VE01"));           // 랜드마크 → 유지

        CandidateImporter.Result result = CandidateImporter.convert(items);

        assertThat(result.candidates()).extracting(Candidate::placeId).containsExactly("1", "13");
        assertThat(result.skipped()).isEqualTo(Map.of(
                SkipReason.NO_IMAGE, 1,
                SkipReason.EXCLUDED_CATEGORY, 6,
                SkipReason.OUT_OF_KOREA, 1,
                SkipReason.MISSING_FIELD, 3,
                SkipReason.DUPLICATE, 1));
    }

    @Test
    void 한국_끝단_좌표는_유지() {
        List<TourItem> items = List.of(
                with(with(item("marado"), "mapy", "33.1140"), "mapx", "126.2669"),
                with(with(item("baengnyeong"), "mapy", "37.9671"), "mapx", "124.6300"),
                with(with(item("dokdo"), "mapy", "37.2411"), "mapx", "131.8648"));

        assertThat(CandidateImporter.convert(items).candidates()).hasSize(3);
    }

    @Test
    void http_이미지는_https로_바꾸고_공공누리_유형이_없으면_null() {
        TourItem http = with(with(item("1"), "firstImage", "http://tong.visitkorea.or.kr/a.JPG"), "cpyrhtDivCd", "");

        Candidate c = CandidateImporter.convert(List.of(http)).candidates().getFirst();

        assertThat(c.imageUrl()).isEqualTo("https://tong.visitkorea.or.kr/a.JPG");
        assertThat(c.imageLicense()).isNull();
    }

    @Test
    void 결과는_placeId_순으로_정렬된다() {
        CandidateImporter.Result result = CandidateImporter.convert(List.of(item("300"), item("100"), item("200")));

        assertThat(result.candidates()).extracting(Candidate::placeId).containsExactly("100", "200", "300");
    }

    @Test
    void render한_파일을_CandidateRepository_형식으로_다시_읽을_수_있다() {
        JsonMapper mapper = JsonMapper.builder().build();
        List<Candidate> candidates = CandidateImporter.convert(List.of(item("1"), item("2"))).candidates();

        String json = CandidateImporter.render(mapper, candidates, LocalDate.of(2026, 11, 11));

        // 한 줄에 후보 하나
        assertThat(json.lines().filter(l -> l.contains("\"placeId\":\"1\"") || l.contains("\"placeId\":\"2\""))).hasSize(2);
        assertThat(json).contains("\"generatedAt\": \"2026-11-11\"").doesNotContain("\"active\"");

        var root = mapper.readTree(json);
        assertThat(root.path("demo").path("placeId").asString()).isEqualTo(CandidateRepository.DEMO_PLACE_ID);
        assertThat(root.path("candidates")).hasSize(2);
        Candidate back = mapper.treeToValue(root.path("candidates").get(0), Candidate.class);
        assertThat(back).isEqualTo(candidates.getFirst());
    }
}
