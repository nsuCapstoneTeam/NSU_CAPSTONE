package com.nsu.capstone.identity.security;

public record IssuedRefreshToken(String value, long expiresInSeconds) {}
