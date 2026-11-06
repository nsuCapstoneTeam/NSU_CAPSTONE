package com.nsu.capstone.identity.repository;

import com.nsu.capstone.identity.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    /**
     * 아이디 목록의 계정 역할·상태를 한 번의 조회로 읽는다. 엔티티를 읽지 않으므로 개인정보 컬럼이 오지 않는다.
     */
    List<UserAccountRow> findAllByIdIn(Collection<UUID> ids);
}
