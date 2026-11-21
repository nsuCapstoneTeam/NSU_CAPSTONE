package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ArtistWriteAccess {

    private final JdbcClient jdbc;

    /**
     * 저장하려는 아티스트가 지금 ACTIVE인지 확인하고 그 users 행을 FOR SHARE로 잠근다. 잠금은 호출한 쪽의 트랜잭션이 끝날
     * 때까지 이어져야 탈퇴(users.status UPDATE)와 저장이 겹쳐도 탈퇴한 사용자의 프로필이 남지 않는다(09 §5, ADR 결정 13).
     * 그래서 트랜잭션 밖에서 부르면 예외다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void require(UUID userId) {
        boolean active = jdbc.sql("""
                        SELECT 1 FROM users
                        WHERE id = :id AND role = 'ARTIST' AND status = 'ACTIVE'
                        FOR SHARE""")
                .param("id", userId)
                .query(Integer.class)
                .optional()
                .isPresent();
        if (!active) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
