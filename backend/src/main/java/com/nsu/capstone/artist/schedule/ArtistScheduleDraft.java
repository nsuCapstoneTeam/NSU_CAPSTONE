package com.nsu.capstone.artist.schedule;

import java.time.LocalDateTime;

/** 검증을 마친 일정 한 건. 시각은 Asia/Seoul 벽시계 값이고 {@code title}은 없으면 null이다. */
record ArtistScheduleDraft(LocalDateTime start, LocalDateTime end, ArtistScheduleStatus status, String title) {}
