package com.nsu.capstone.event.recommendation;

import java.util.List;

/**
 * AI에 보내는 행사 요청 원문(서버 협의 012 결정 3.2). 행사 종류와 장르는 코드가 아니라
 * 03 행사 API §3.3 표의 한국어 이름이며, 장르는 표의 순서다.
 */
public record AiEventSource(String description, String eventType, List<String> genres) {
}
