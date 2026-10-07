package com.nsu.capstone.identity.security;

public record IssuedAccessToken(String value, long expiresInSeconds) {
}
