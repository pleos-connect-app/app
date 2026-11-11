package com.randomdrive.server.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 후보 장소 목록 저장소. classpath의 candidates.json을 서버 시작 시 한 번 읽어 메모리에 둔다.
 * 파일은 TourAPI에서 `./gradlew importCandidates`로 만든다(tourapi 패키지). DB로 바뀌면 이 클래스만 교체.
 * 데이터가 잘못돼 있으면 서버가 아예 뜨지 않게 해서, 잘못된 목적지가 앱으로 나가는 일을 막는다.
 */
@Component
public class CandidateRepository {

    /** demo=true 요청이 항상 주는 시연용 장소. 앱의 CandidatePicker.DEMO_PLACE_ID와 같은 값. */
    public static final String DEMO_PLACE_ID = "demo-namhansanseong";

    static final String RESOURCE = "candidates.json";

    private final List<Candidate> candidates;
    private final Candidate demo;
    private final Map<String, Candidate> byId;

    public CandidateRepository(JsonMapper jsonMapper) {
        CandidateFile file = load(jsonMapper);
        if (file.candidates() == null || file.candidates().isEmpty()) {
            throw new IllegalStateException(RESOURCE + ": 'candidates' is empty");
        }
        if (file.demo() == null || !DEMO_PLACE_ID.equals(file.demo().placeId())) {
            throw new IllegalStateException(RESOURCE + ": 'demo' must be the place '" + DEMO_PLACE_ID + "'");
        }
        this.candidates = List.copyOf(file.candidates());
        this.demo = file.demo();
        validate(candidates);
        validate(List.of(demo));
        Map<String, Candidate> map = new HashMap<>();
        candidates.forEach(c -> map.put(c.placeId(), c));
        map.put(demo.placeId(), demo);
        this.byId = Map.copyOf(map);
    }

    /** 랜덤 추첨 후보 전체(active=false 포함, demo 장소는 별도). 추첨 시 걸러내는 건 DestinationPicker 몫. */
    public List<Candidate> findAll() {
        return candidates;
    }

    /** demo=true일 때 주는 시연용 장소. */
    public Candidate demo() {
        return demo;
    }

    /** placeId로 후보(demo 포함)를 찾는다. 공유 페이지에서 이미지 공공누리 유형을 알아낼 때 쓴다. */
    public Optional<Candidate> findById(String placeId) {
        return Optional.ofNullable(byId.get(placeId));
    }

    private static CandidateFile load(JsonMapper jsonMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return jsonMapper.readValue(in, CandidateFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + RESOURCE, e);
        }
    }

    /** placeId/name/address 필수, placeId 중복 금지, 좌표 범위 확인. 후보 파일을 만들 때(ImportCandidatesRunner)도 쓴다. */
    public static void validate(List<Candidate> candidates) {
        Set<String> ids = new HashSet<>();
        for (Candidate c : candidates) {
            if (isBlank(c.placeId()) || isBlank(c.name()) || isBlank(c.address())) {
                throw new IllegalStateException(RESOURCE + ": placeId, name, address are required: " + c);
            }
            if (!ids.add(c.placeId())) {
                throw new IllegalStateException(RESOURCE + ": duplicate placeId '" + c.placeId() + "'");
            }
            if (c.lat() < -90 || c.lat() > 90 || c.lng() < -180 || c.lng() > 180) {
                throw new IllegalStateException(RESOURCE + ": coordinates out of range for '" + c.placeId() + "'");
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** JSON 파일 최상위 구조. "_comment", "source", "generatedAt" 같은 설명용 필드는 무시한다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CandidateFile(Candidate demo, List<Candidate> candidates) {
    }
}
