package com.nsu.capstone.artist.schedule;

import java.time.LocalDateTime;
import java.util.UUID;

/** 내 일정 한 건의 저장 값. 시각은 Asia/Seoul 벽시계이고 {@code title}은 없으면 null이다. 응답에만 쓰며 다른 모듈에 열지 않는다. */
record MyArtistSchedule(UUID id, LocalDateTime start, LocalDateTime end, ArtistScheduleStatus status, String title) {}
