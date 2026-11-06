package com.nsu.capstone.identity.repository;

import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import java.util.UUID;

/**
 * 계정의 역할과 상태만 읽는 projection. 이메일·전화번호·비밀번호 해시는 읽지 않는다.
 */
public record UserAccountRow(UUID id, UserRole role, UserStatus status) {
}
