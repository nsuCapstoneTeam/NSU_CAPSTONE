package com.nsu.capstone.global.logging;

/** 로그에 나오면 안 되는 가짜 값. 이메일은 소문자로 쓴다(users CHECK 제약과 같다). */
final class Sentinel {

    static final String EMAIL = "sentinel-dup-7c1f@example.com";

    private Sentinel() {}
}
