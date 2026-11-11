package com.randomdrive.server.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateRepositoryTest {

    @Test
    void 실제_candidates_json을_읽고_demo_장소가_따로_있다() {
        CandidateRepository repository = new CandidateRepository(JsonMapper.builder().build());

        assertThat(repository.findAll()).isNotEmpty();
        assertThat(repository.findAll()).allMatch(Candidate::active, "생략된 active는 true");
        assertThat(repository.demo().placeId()).isEqualTo(CandidateRepository.DEMO_PLACE_ID);
        assertThat(repository.demo().name()).isEqualTo("남한산성");
    }

    @Test
    void placeId가_중복이면_시작_실패() {
        List<Candidate> candidates = List.of(
                new Candidate("same", "A", 37.0, 127.0, "addr", null, null, true),
                new Candidate("same", "B", 37.1, 127.1, "addr", null, null, true));

        assertThatThrownBy(() -> CandidateRepository.validate(candidates))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate placeId 'same'");
    }

    @Test
    void 필수값이_비었거나_좌표가_범위_밖이면_시작_실패() {
        assertThatThrownBy(() -> CandidateRepository.validate(List.of(
                new Candidate("p1", " ", 37.0, 127.0, "addr", null, null, true))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required");

        assertThatThrownBy(() -> CandidateRepository.validate(List.of(
                new Candidate("p1", "A", 127.0, 37.0, "addr", null, null, true))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("out of range");
    }
}
