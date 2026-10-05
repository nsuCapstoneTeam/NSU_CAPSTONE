package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UserRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UuidV7Generator uuidV7Generator;

    @Test
    void usersSchemaHasRequiredColumnsAndNullability() {
        Map<String, String> nullabilityByColumn = jdbcTemplate.queryForList("""
                SELECT column_name, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'users'
                """)
            .stream()
            .collect(Collectors.toMap(
                row -> (String) row.get("column_name"),
                row -> (String) row.get("is_nullable")
            ));

        assertEquals(8, nullabilityByColumn.size());
        assertEquals("NO", nullabilityByColumn.get("id"));
        assertEquals("NO", nullabilityByColumn.get("email"));
        assertEquals("YES", nullabilityByColumn.get("password_hash"));
        assertEquals("NO", nullabilityByColumn.get("phone"));
        assertEquals("NO", nullabilityByColumn.get("role"));
        assertEquals("NO", nullabilityByColumn.get("status"));
        assertEquals("NO", nullabilityByColumn.get("created_at"));
        assertEquals("NO", nullabilityByColumn.get("updated_at"));
    }

    @Test
    void allowsNullPasswordHash() {
        User user = User.createArtist(
            uuidV7Generator.generate(),
            "oauth-ready@example.com",
            null,
            "01011112222"
        );

        User savedUser = userRepository.saveAndFlush(user);

        assertNull(savedUser.getPasswordHash());
    }

    @Test
    void rejectsDuplicatedEmail() {
        String email = "duplicate@example.com";
        userRepository.saveAndFlush(User.createArtist(
            uuidV7Generator.generate(),
            email,
            "{bcrypt}first",
            "01011112222"
        ));

        User duplicatedUser = User.createArtist(
            uuidV7Generator.generate(),
            email,
            "{bcrypt}second",
            "01033334444"
        );

        assertThrows(
            DataIntegrityViolationException.class,
            () -> userRepository.saveAndFlush(duplicatedUser)
        );
    }
}
