package com.nsu.capstone.event.domain;

import java.util.ArrayList;
import java.util.Arrays;
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

    private static final String MUSIC = " music";

    private KeyphraseVocabulary() {}

    /**
     * 검증된 매핑 버전과 같을 때만 검증된 것이다. 문구를 바꿔 {@link #MAPPING_VERSION}을 올리면 두 번호가 달라져
     * 자동으로 검증되지 않은 것이 된다.
     */
    static boolean isVerified(int mappingVersion, int verifiedMappingVersion) {
        return verifiedMappingVersion != 0 && mappingVersion == verifiedMappingVersion;
    }

    static String sentence(MusicConditions conditions) {
        return String.join("", pieces(conditions)).substring(1);
    }

    /** 조건이 {@link #slotPieces()}와 같은 칸 순서로 만드는 조각. 고르지 않은 칸은 빈 문자열이다. */
    static List<String> pieces(MusicConditions conditions) {
        return List.of(
                moodPiece(conditions.mood()),
                tempoPiece(conditions.tempo()),
                instrumentalPiece(conditions.vocal()),
                genresPiece(conditions.genres()),
                MUSIC,
                vocalAfterMusic(conditions.vocal()),
                forClause(conditions.eventType()));
    }

    /**
     * 문장 틀의 칸 순서대로, 칸마다 고를 수 있는 모든 조각(앞 공백 포함, 빈 조각 제외). 길이 증명과 버전 가드
     * 테스트가 쓴다.
     */
    static List<List<String>> slotPieces() {
        return List.of(
                Arrays.stream(Mood.values()).map(KeyphraseVocabulary::moodPiece).toList(),
                Arrays.stream(Tempo.values())
                        .map(KeyphraseVocabulary::tempoPiece)
                        .toList(),
                List.of(instrumentalPiece(Vocal.INSTRUMENTAL)),
                genreCombinationPieces(),
                List.of(MUSIC),
                Arrays.stream(Vocal.values())
                        .map(KeyphraseVocabulary::vocalAfterMusic)
                        .filter(piece -> !piece.isEmpty())
                        .toList(),
                Arrays.stream(EventType.values())
                        .map(KeyphraseVocabulary::forClause)
                        .filter(piece -> !piece.isEmpty())
                        .toList());
    }

    /** 장르를 1개부터 {@link MusicConditions#MAX_GENRES}개까지 선언 순서로 고른 모든 조합의 조각이다. */
    private static List<String> genreCombinationPieces() {
        List<String> pieces = new ArrayList<>();
        collectGenreCombinations(List.of(), 0, pieces);
        return pieces;
    }

    private static void collectGenreCombinations(List<Genre> chosen, int from, List<String> pieces) {
        if (!chosen.isEmpty()) {
            pieces.add(genresPiece(chosen));
        }
        if (chosen.size() == MusicConditions.MAX_GENRES) {
            return;
        }
        Genre[] all = Genre.values();
        for (int i = from; i < all.length; i++) {
            List<Genre> next = new ArrayList<>(chosen);
            next.add(all[i]);
            collectGenreCombinations(next, i + 1, pieces);
        }
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
            default -> " " + String.join(", ", phrases.subList(0, phrases.size() - 1)) + ", or " + phrases.getLast();
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
