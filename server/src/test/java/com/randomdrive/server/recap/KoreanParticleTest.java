package com.randomdrive.server.recap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanParticleTest {

    @Test
    void 받침이_없거나_ㄹ_받침이면_로_그_외는_으로() {
        assertThat(KoreanParticle.euro("안목해변")).isEqualTo("으로"); // 변: ㄴ
        assertThat(KoreanParticle.euro("남한산성")).isEqualTo("으로"); // 성: ㅇ
        assertThat(KoreanParticle.euro("외암민속마을")).isEqualTo("로"); // 을: ㄹ
        assertThat(KoreanParticle.euro("금오서원")).isEqualTo("으로"); // 원: ㄴ
        assertThat(KoreanParticle.euro("녹도")).isEqualTo("로");       // 도: 없음
    }

    @Test
    void 끝의_괄호는_건너뛴다() {
        assertThat(KoreanParticle.euro("만일사(순창)")).isEqualTo("로");          // 사
        assertThat(KoreanParticle.euro("십이선녀탕(탕수동계곡)")).isEqualTo("으로"); // 탕
    }

    @Test
    void 한글로_끝나지_않으면_으로_괄호() {
        assertThat(KoreanParticle.euro("N서울타워")).isEqualTo("로");
        assertThat(KoreanParticle.euro("63빌딩 2")).isEqualTo("(으)로");
        assertThat(KoreanParticle.euro("ABC")).isEqualTo("(으)로");
    }
}
