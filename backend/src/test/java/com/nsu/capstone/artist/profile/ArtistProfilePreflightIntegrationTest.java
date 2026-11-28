package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** ADR artist_profile/00 Consequences "실행으로 확인할 것" 중 DB 배열 바인딩과 package-private record 읽기. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ArtistProfilePreflightIntegrationTest.ProbeController.class})
class ArtistProfilePreflightIntegrationTest {

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UuidV7Generator uuidV7Generator;

    private final List<UUID> createdUserIds = new ArrayList<>();

    @AfterEach
    void deleteCreatedUsers() {
        userRepository.deleteAllById(createdUserIds);
    }

    @Test
    void bindsStringArrayAsUuidArrayInOneStatement() {
        UUID first = saveUser();
        UUID second = saveUser();
        UUID missing = UUID.randomUUID();

        List<UUID> found = jdbcClient
                .sql("SELECT id FROM users WHERE id = ANY(CAST(:ids AS uuid[]))")
                .param("ids", new String[] {first.toString(), second.toString(), missing.toString()})
                .query(UUID.class)
                .list();

        assertThat(found).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void readsVarcharArrayColumnAsStringArray() {
        List<String> read = jdbcClient
                .sql("SELECT CAST(:types AS varchar(32)[])")
                .param("types", new String[] {"SOLO", "DJ"})
                .query((rs, rowNum) -> List.of((String[]) rs.getArray(1).getArray()))
                .single();

        assertThat(read).containsExactly("SOLO", "DJ");
    }

    @Test
    void jacksonAndSpringdocReadPackagePrivateRecords() throws Exception {
        mockMvc.perform(post("/probe/echo")
                        .with(jwt())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"가나다\",\"items\":[\"x\",\"y\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("가나다"))
                .andExpect(jsonPath("$.items[1]").value("y"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ProbeBody.properties.name.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.ProbeBody.properties.items.type")
                        .value("array"));
    }

    private UUID saveUser() {
        UUID id = uuidV7Generator.generate();
        userRepository.saveAndFlush(User.createArtist(id, id + "@example.com", null, "01000000000"));
        createdUserIds.add(id);
        return id;
    }

    @RestController
    public static class ProbeController {

        @PostMapping("/probe/echo")
        ProbeBody echo(@RequestBody ProbeBody body) {
            return body;
        }
    }

    record ProbeBody(String name, List<String> items) {}
}
