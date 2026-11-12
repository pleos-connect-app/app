package com.randomdrive.server.recap;

/** 받침에 따라 바뀌는 조사. 장소 이름 끝의 괄호·영문은 건너뛰고 마지막 한글 글자로 판단한다. */
final class KoreanParticle {

    private static final int RIEUL = 8;

    private KoreanParticle() {
    }

    /** "로"/"으로". 받침이 없거나 ㄹ 받침이면 "로". 한글이 없으면 "(으)로". */
    static String euro(String word) {
        String stripped = word.replaceAll("\\s*\\([^)]*\\)\\s*$", "");
        for (int i = stripped.length() - 1; i >= 0; i--) {
            char c = stripped.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int jong = (c - 0xAC00) % 28;
                return jong == 0 || jong == RIEUL ? "로" : "으로";
            }
            if (Character.isDigit(c) || Character.isLetter(c)) {
                break;
            }
        }
        return "(으)로";
    }
}
