package com.randomdrive.server.tourapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * TourAPI(KorService2) 호출. 지금은 후보 가져오기(importCandidates)에서만 쓴다.
 * 요청마다 부르지 않는 이유: 위치기반 조회는 반경 최대 20km라 50~150km 추첨에 못 쓰고, 일일 호출 수 제한도 있다.
 */
@Component
public class TourApiClient {

    private static final Logger log = LoggerFactory.getLogger(TourApiClient.class);

    static final int PAGE_SIZE = 1000;
    private static final int MAX_ATTEMPTS = 3;
    private static final String OK = "0000";

    private final TourApiProperties properties;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public TourApiClient(TourApiProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.restClient = RestClient.create();
    }

    /** 지역기반 관광정보(areaBasedList2)에서 해당 타입 전체를 페이지를 넘기며 받는다. */
    public List<TourItem> fetchAllByContentType(int contentTypeId) {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            throw new TourApiException("tour-api.service-key가 비어 있음. application-local.yml 또는 TOUR_API_KEY에 넣을 것");
        }
        List<TourItem> all = new ArrayList<>();
        for (int page = 1; ; page++) {
            Page result = parsePage(getWithRetry(areaBasedListUri(contentTypeId, page)));
            all.addAll(result.items());
            log.info("TourAPI contentTypeId={} page={} items={} ({}/{})",
                    contentTypeId, page, result.items().size(), all.size(), result.totalCount());
            if (result.items().isEmpty() || all.size() >= result.totalCount()) {
                break;
            }
        }
        return all;
    }

    private URI areaBasedListUri(int contentTypeId, int page) {
        // serviceKey는 템플릿 변수로 넣어야 +, /, = 같은 문자까지 정확히 인코딩된다.
        return UriComponentsBuilder.fromUriString(properties.baseUrl() + "/areaBasedList2")
                .queryParam("serviceKey", "{serviceKey}")
                .queryParam("MobileOS", "ETC")
                .queryParam("MobileApp", "RandomDrive")
                .queryParam("_type", "json")
                .queryParam("arrange", "A")
                .queryParam("contentTypeId", contentTypeId)
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("pageNo", page)
                .encode()
                .buildAndExpand(properties.serviceKey())
                .toUri();
    }

    private String getWithRetry(URI uri) {
        for (int attempt = 1; ; attempt++) {
            try {
                return restClient.get().uri(uri).retrieve().body(String.class);
            } catch (RestClientException e) {
                // 예외 메시지에 URL(=serviceKey)이 들어갈 수 있어 로그·예외에는 원인 종류만 남긴다.
                if (attempt >= MAX_ATTEMPTS) {
                    throw new TourApiException("TourAPI 요청 실패(" + MAX_ATTEMPTS + "회 시도): " + e.getClass().getSimpleName());
                }
                log.warn("TourAPI 요청 실패, {}초 후 재시도 ({}/{}): {}", attempt, attempt, MAX_ATTEMPTS, e.getClass().getSimpleName());
                sleepSeconds(attempt);
            }
        }
    }

    /**
     * 응답 본문 한 페이지를 해석한다.
     * - 인증키 오류 등은 _type=json이어도 XML로 오므로 JSON이 아니면 실패로 본다.
     * - 결과가 0건이면 items가 빈 문자열(""), 1건이면 item이 배열이 아닌 객체로 올 수 있어 모두 처리한다.
     */
    Page parsePage(String body) {
        if (body == null || !body.stripLeading().startsWith("{")) {
            throw new TourApiException("TourAPI가 JSON이 아닌 응답을 줌(인증키·승인 상태 확인): " + abbreviate(body));
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (JacksonException e) {
            throw new TourApiException("TourAPI 응답 JSON 해석 실패: " + abbreviate(body));
        }
        JsonNode response = root.path("response");
        String resultCode = response.path("header").path("resultCode").asString("");
        if (!OK.equals(resultCode)) {
            throw new TourApiException("TourAPI 오류 resultCode=" + resultCode
                    + " resultMsg=" + response.path("header").path("resultMsg").asString(""));
        }
        JsonNode bodyNode = response.path("body");
        JsonNode itemNode = bodyNode.path("items").path("item");
        List<TourItem> items = new ArrayList<>();
        if (itemNode.isArray()) {
            itemNode.forEach(n -> items.add(toItem(n)));
        } else if (itemNode.isObject()) {
            items.add(toItem(itemNode));
        }
        return new Page(items, bodyNode.path("totalCount").asInt(0));
    }

    private static TourItem toItem(JsonNode n) {
        return new TourItem(
                text(n, "contentid"), text(n, "title"), text(n, "addr1"),
                text(n, "mapx"), text(n, "mapy"), text(n, "firstimage"),
                text(n, "cpyrhtDivCd"), text(n, "lclsSystm1"), text(n, "lclsSystm2"));
    }

    private static String text(JsonNode n, String field) {
        return n.path(field).asString("").trim();
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "(빈 응답)";
        }
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...";
    }

    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TourApiException("TourAPI 재시도 대기 중 중단됨");
        }
    }

    record Page(List<TourItem> items, int totalCount) {
    }
}
