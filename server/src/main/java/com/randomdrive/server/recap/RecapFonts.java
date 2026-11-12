package com.randomdrive.server.recap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.List;

/**
 * 영상에 쓸 한글 글꼴. 설정 파일(recap.font-file)이 있으면 그것, 없으면 설치된 한글 글꼴 중 하나.
 * 한글 글꼴이 하나도 없는 서버(예: 최소 설치 Linux)면 글자가 네모로 나오므로 경고를 남긴다
 * → `fonts-noto-cjk` 설치 또는 recap.font-file 지정.
 */
final class RecapFonts {

    private static final Logger log = LoggerFactory.getLogger(RecapFonts.class);
    private static final String SAMPLE = "한글 드라이브";
    private static final List<String> PREFERRED = List.of(
            "Pretendard", "Apple SD Gothic Neo", "AppleSDGothicNeo", "Noto Sans KR", "Noto Sans CJK KR",
            "Malgun Gothic", "맑은 고딕", "NanumGothic", "나눔고딕", "NanumBarunGothic");

    private final Font base;

    RecapFonts(String fontFile) {
        this.base = resolve(fontFile);
    }

    Font bold(float size) {
        return base.deriveFont(Font.BOLD, size);
    }

    Font regular(float size) {
        return base.deriveFont(Font.PLAIN, size);
    }

    private static Font resolve(String fontFile) {
        if (fontFile != null && !fontFile.isBlank()) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, new File(fontFile));
            } catch (Exception e) {
                log.warn("recap.font-file {} could not be loaded ({}), falling back to installed fonts", fontFile, e.toString());
            }
        }
        List<String> installed = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        // 1순위: 한글이 되고 굵은 글꼴 파일이 따로 있는 것(Windows의 Noto Sans KR처럼 가변 글꼴이면 굵게가 안 됨)
        for (boolean needBold : new boolean[]{true, false}) {
            for (String name : PREFERRED) {
                if (installed.contains(name) && usable(name, needBold)) {
                    return new Font(name, Font.PLAIN, 12);
                }
            }
            for (String name : installed) {
                if (usable(name, needBold)) {
                    return new Font(name, Font.PLAIN, 12);
                }
            }
        }
        log.warn("No Korean font found; recap text will not render. Install a Korean font or set recap.font-file.");
        return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    private static boolean usable(String family, boolean needBold) {
        Font plain = new Font(family, Font.PLAIN, 12);
        if (plain.canDisplayUpTo(SAMPLE) != -1) {
            return false;
        }
        return !needBold || !new Font(family, Font.BOLD, 12).getFontName().equals(plain.getFontName());
    }
}
