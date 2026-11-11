package com.randomdrive.server.tourapi;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 응답 해석(parsePage)만 검사한다. 네트워크는 쓰지 않는다. */
class TourApiClientTest {

    private final TourApiClient client = new TourApiClient(
            new TourApiProperties("test-key", "https://example.invalid", "unused"), JsonMapper.builder().build());

    /** 2026-11-11 실제 areaBasedList2 응답에서 필드 일부만 남긴 것. */
    private static final String TWO_ITEMS = """
            {"response": {"header":{"resultCode":"0000","resultMsg":"OK"},"body": {"items": {"item":[
              {"addr1":"서울특별시 광진구 영화사로 107","addr2":"","contentid":"337546","contenttypeid":"12",
               "firstimage":"https://tong.visitkorea.or.kr/cms/resource/06/3506706_image2_1.jpg","cpyrhtDivCd":"Type1",
               "mapx":"127.0982434532","mapy":"37.5542644791","title":"영화사(서울)",
               "lclsSystm1":"HS","lclsSystm2":"HS03","lclsSystm3":"HS030100"},
              {"addr1":"서울특별시 성북구 길음로13길 39","contentid":"596259","firstimage":"","cpyrhtDivCd":"",
               "mapx":"127.0181502452","mapy":"37.6088756630","title":"예지원","lclsSystm1":"VE","lclsSystm2":"VE09"}
            ]},"numOfRows":2,"pageNo":1,"totalCount":767}}}
            """;

    @Test
    void 정상_응답의_item_배열과_totalCount를_읽는다() {
        TourApiClient.Page page = client.parsePage(TWO_ITEMS);

        assertThat(page.totalCount()).isEqualTo(767);
        assertThat(page.items()).containsExactly(
                new TourItem("337546", "영화사(서울)", "서울특별시 광진구 영화사로 107",
                        "127.0982434532", "37.5542644791",
                        "https://tong.visitkorea.or.kr/cms/resource/06/3506706_image2_1.jpg", "Type1", "HS", "HS03"),
                new TourItem("596259", "예지원", "서울특별시 성북구 길음로13길 39",
                        "127.0181502452", "37.6088756630", "", "", "VE", "VE09"));
    }

    @Test
    void item이_하나면_배열이_아닌_객체로_와도_읽는다() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":{"item":
                  {"contentid":"1","title":"A","addr1":"주소","mapx":"127","mapy":"37"}},"totalCount":1}}}
                """;

        assertThat(client.parsePage(body).items()).extracting(TourItem::contentId).containsExactly("1");
    }

    @Test
    void 결과가_0건이면_items가_빈_문자열이어도_빈_목록() {
        String body = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{"items":"","numOfRows":1000,"pageNo":14,"totalCount":12546}}}
                """;

        TourApiClient.Page page = client.parsePage(body);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalCount()).isEqualTo(12546);
    }

    @Test
    void resultCode가_0000이_아니면_실패() {
        String body = """
                {"response":{"header":{"resultCode":"10","resultMsg":"INVALID_REQUEST_PARAMETER_ERROR"}}}
                """;

        assertThatThrownBy(() -> client.parsePage(body))
                .isInstanceOf(TourApiException.class)
                .hasMessageContaining("resultCode=10")
                .hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR");
    }

    @Test
    void 인증키_오류처럼_XML이_오면_실패() {
        String body = """
                <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
                <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg><returnReasonCode>30</returnReasonCode>
                </cmmMsgHeader></OpenAPI_ServiceResponse>
                """;

        assertThatThrownBy(() -> client.parsePage(body))
                .isInstanceOf(TourApiException.class)
                .hasMessageContaining("JSON이 아닌 응답")
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    void 인증키가_비어_있으면_호출하지_않고_실패() {
        TourApiClient noKey = new TourApiClient(
                new TourApiProperties(" ", "https://example.invalid", "unused"), JsonMapper.builder().build());

        assertThatThrownBy(() -> noKey.fetchAllByContentType(12))
                .isInstanceOf(TourApiException.class)
                .hasMessageContaining("service-key");
    }
}
