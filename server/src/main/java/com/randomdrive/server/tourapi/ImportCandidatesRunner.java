package com.randomdrive.server.tourapi;

import com.randomdrive.server.service.Candidate;
import com.randomdrive.server.service.CandidateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * `./gradlew importCandidates` (프로필 local,import, 웹 서버 없이 실행)에서만 동작.
 * TourAPI에서 관광지를 받아 후보 파일을 다시 만들고 끝난다.
 * 결과가 이상하면(너무 적음, 검증 실패) 기존 파일을 건드리지 않고 실패한다.
 */
@Component
@Profile("import")
public class ImportCandidatesRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportCandidatesRunner.class);

    /** 2026-11 기준 약 1만 곳. 이보다 훨씬 적으면 API 쪽 문제로 보고 덮어쓰지 않는다. */
    static final int MIN_EXPECTED = 1000;

    private final TourApiClient client;
    private final TourApiProperties properties;
    private final JsonMapper jsonMapper;

    public ImportCandidatesRunner(TourApiClient client, TourApiProperties properties, JsonMapper jsonMapper) {
        this.client = client;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<TourItem> items = client.fetchAllByContentType(CandidateImporter.CONTENT_TYPE_TOURIST_SPOT);
        CandidateImporter.Result result = CandidateImporter.convert(items);
        log.info("TourAPI 관광지 {}건 → 후보 {}건, 제외 {}", items.size(), result.candidates().size(), result.skipped());

        if (result.candidates().size() < MIN_EXPECTED) {
            throw new IllegalStateException("후보가 " + result.candidates().size() + "건뿐이라 파일을 덮어쓰지 않음(최소 "
                    + MIN_EXPECTED + "건 기대)");
        }
        List<Candidate> all = new ArrayList<>(result.candidates());
        all.add(CandidateImporter.DEMO);
        CandidateRepository.validate(all);

        String content = CandidateImporter.render(jsonMapper, result.candidates(),
                LocalDate.now(ZoneId.of("Asia/Seoul")));
        Path output = Path.of(properties.output()).toAbsolutePath();
        // 임시 파일에 다 쓴 뒤 바꿔치기: 중간에 실패해도 기존 파일이 반쯤 쓰인 채로 남지 않는다.
        Path tmp = output.resolveSibling(output.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        Files.move(tmp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log.info("후보 파일 저장: {} ({} bytes)", output, Files.size(output));
    }
}
