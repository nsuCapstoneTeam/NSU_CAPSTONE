package com.nsu.capstone;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.support.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LoginFlowIntegrationTest.ProtectedController.class})
class LoginFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UuidV7Generator uuidV7Generator;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void authenticatesWithDatabasePasswordAndUsesIssuedTokenThroughRealDecoder() throws Exception {
        User user = userRepository.saveAndFlush(User.createArtist(
            uuidV7Generator.generate(),
            "artist-login@example.com",
            passwordEncoder.encode("password"),
            "01012345678"
        ));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"email":"artist-login@example.com","password":"password"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expiresIn").value(1800))
            .andExpect(jsonPath("$.userId").value(user.getId().toString()))
            .andExpect(jsonPath("$.role").value("ARTIST"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();

        String token = JsonPath.read(
            loginResult.getResponse().getContentAsString(),
            "$.accessToken"
        );

        mockMvc.perform(get("/test/integration/artist")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @RestController
    static class ProtectedController {

        @GetMapping("/test/integration/artist")
        @PreAuthorize("hasRole('ARTIST')")
        String artist() {
            return "artist";
        }
    }
}
