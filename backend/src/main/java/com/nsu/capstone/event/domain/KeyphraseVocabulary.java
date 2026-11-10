package com.nsu.capstone.event.domain;

import java.util.List;

/**
 * 검색 문구의 영어 문구와 문장 틀, 매핑 버전(ADR recommendation_search_input/00 결정 3~6). 문구를 바꾸면
 * {@link #MAPPING_VERSION}을 올린다.
 *
 * <p>문장 틀의 칸마다 조각을 만들고 이어 붙인다. 조각은 빈 문자열이거나 앞 공백 하나로 시작한다.
 */
final class KeyphraseVocabulary {

    static final int MAPPING_VERSION = 1;

    /** AI 담당이 검증한 매핑 버전. 0은 검증된 버전이 없다는 뜻이다. 검증 근거가 있을 때만 바꾼다. */
    static final int VERIFIED_MAPPING_VERSION = 0;

    private KeyphraseVocabulary() {}

    static String sentence(MusicConditions conditions) {
        String pieces = moodPiece(conditions.mood())
                + tempoPiece(conditions.tempo())
                + instrumentalPiece(conditions.vocal())
                + genresPiece(conditions.genres())
                + " music"
                + vocalAfterMusic(conditions.vocal())
                + forClause(conditions.eventType());
        return pieces.substring(1);
    }

    private static String moodPiece(Mood mood) {
        if (mood == null) {
            return "";
        }
        return switch (mood) {
            case ENERGETIC -> " energetic";
            case CHEERFUL -> " cheerful";
            case EMOTIONAL -> " emotional";
            case CALM -> " calm";
            case ROMANTIC -> " romantic";
            case GRAND -> " grand";
        };
    }

    private static String tempoPiece(Tempo tempo) {
        if (tempo == null) {
            return "";
        }
        return switch (tempo) {
            case SLOW -> " slow-tempo";
            case MEDIUM -> " mid-tempo";
            case FAST -> " fast-tempo";
        };
    }

    /** 연주곡은 부정형(without vocals) 대신 장르 앞 꾸밈말로 쓴다. */
    private static String instrumentalPiece(Vocal vocal) {
        return vocal == Vocal.INSTRUMENTAL ? " instrumental" : "";
    }

    /** 하나는 그대로, 둘은 {@code A or B}, 셋은 {@code A, B, or C}다. 이음말은 "이 중 아무 장르나"를 뜻하는 or다. */
    private static String genresPiece(List<Genre> genres) {
        List<String> phrases = genres.stream().map(KeyphraseVocabulary::phrase).toList();
        return switch (phrases.size()) {
            case 0 -> "";
            case 1 -> " " + phrases.get(0);
            case 2 -> " " + phrases.get(0) + " or " + phrases.get(1);
            default -> " " + phrases.get(0) + ", " + phrases.get(1) + ", or " + phrases.get(2);
        };
    }

    /** 곡에 들리는 목소리 문구. 연주곡(INSTRUMENTAL)은 장르 앞에서 쓰므로 여기서는 빠진다. */
    private static String vocalAfterMusic(Vocal vocal) {
        if (vocal == null) {
            return "";
        }
        return switch (vocal) {
            case INSTRUMENTAL -> "";
            case WITH_VOCALS -> " with vocals";
            case MALE_VOCALS -> " with male vocals";
            case FEMALE_VOCALS -> " with female vocals";
        };
    }

    /** 행사 종류 칸. 고르지 않았거나 기타(OTHER)면 칸 전체가 빠진다. */
    private static String forClause(EventType eventType) {
        if (eventType == null) {
            return "";
        }
        return switch (eventType) {
            case UNIVERSITY_FESTIVAL -> " for a university festival";
            case CAMPUS_EVENT -> " for a campus event";
            case LOCAL_FESTIVAL -> " for a local festival";
            case CORPORATE_EVENT -> " for a corporate event";
            case CONCERT -> " for a concert";
            case SMALL_VENUE -> " for a small cafe performance";
            case WEDDING -> " for a wedding";
            case OTHER -> "";
        };
    }

    static String phrase(Genre genre) {
        return switch (genre) {
            case POP -> "pop";
            case BALLAD -> "ballad";
            case ROCK -> "rock";
            case HIPHOP -> "hip hop";
            case RNB_SOUL -> "R&B";
            case JAZZ -> "jazz";
            case ELECTRONIC -> "electronic";
            case ACOUSTIC -> "acoustic folk";
            case CLASSICAL -> "classical";
            case TROT -> "Korean trot";
        };
    }
}
