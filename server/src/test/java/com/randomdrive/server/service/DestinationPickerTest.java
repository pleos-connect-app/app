package com.randomdrive.server.service;

import com.randomdrive.server.dto.DestinationDto;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 후보는 기준점(37.0, 127.0)에서 북쪽으로만 떨어뜨려 거리를 예측하기 쉽게 했다.
 * 위도 0.01도 ≈ 1.11km.
 */
class DestinationPickerTest {

    private static final double LAT = 37.0;
    private static final double LNG = 127.0;

    private static Candidate north(String id, double dLat) {
        return new Candidate(id, id, LAT + dLat, LNG, "addr", null, null, true);
    }

    private static Set<String> pickedIds(List<Candidate> candidates, int radiusKm) {
        Random random = new Random(42);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            DestinationPicker.pick(candidates, LAT, LNG, radiusKm, random)
                    .ifPresent(d -> ids.add(d.placeId()));
        }
        return ids;
    }

    @Test
    void 반경_안의_후보만_모두_고르게_나온다() {
        List<Candidate> candidates = List.of(
                north("km11", 0.1),     // 11.1km
                north("km56", 0.5),     // 55.6km
                north("km133", 1.2));   // 133.4km

        assertThat(pickedIds(candidates, 50)).containsExactly("km11");
        assertThat(pickedIds(candidates, 100)).containsExactlyInAnyOrder("km11", "km56");
        assertThat(pickedIds(candidates, 150)).containsExactlyInAnyOrder("km11", "km56", "km133");
    }

    @Test
    void 직선_3km_미만은_너무_가까워서_제외() {
        List<Candidate> candidates = List.of(
                north("km2_9", 0.026),  // 2.89km
                north("km3_1", 0.028)); // 3.11km

        assertThat(pickedIds(candidates, 50)).containsExactly("km3_1");
    }

    @Test
    void 반경_경계값_이하까지_포함() {
        List<Candidate> candidates = List.of(
                north("km49_9", 0.449),  // 49.92km
                north("km50_1", 0.451)); // 50.15km

        assertThat(pickedIds(candidates, 50)).containsExactly("km49_9");
    }

    @Test
    void active가_false면_제외() {
        List<Candidate> candidates = List.of(
                north("open", 0.1),
                new Candidate("closed", "closed", LAT + 0.2, LNG, "addr", null, null, false));

        assertThat(pickedIds(candidates, 50)).containsExactly("open");
    }

    @Test
    void 조건에_맞는_후보가_없으면_empty() {
        List<Candidate> candidates = List.of(north("km133", 1.2));

        assertThat(DestinationPicker.pick(candidates, LAT, LNG, 50, new Random(1))).isEmpty();
        assertThat(DestinationPicker.pick(List.of(), LAT, LNG, 150, new Random(1))).isEmpty();
    }

    @Test
    void 응답에는_후보_정보와_소수_첫째자리_직선거리가_들어간다() {
        Candidate c = new Candidate("p1", "장소", LAT + 0.1, LNG, "주소", "https://img/1.jpg", "Type1", true);

        Optional<DestinationDto> picked = DestinationPicker.pick(List.of(c), LAT, LNG, 50, new Random(1));

        assertThat(picked).contains(new DestinationDto(
                "p1", "장소", LAT + 0.1, LNG, "주소", "https://img/1.jpg", 11.1));
    }
}
