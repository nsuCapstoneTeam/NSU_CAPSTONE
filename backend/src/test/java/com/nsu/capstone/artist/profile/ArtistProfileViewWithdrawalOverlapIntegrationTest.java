package com.nsu.capstone.artist.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

/** 열람과 탈퇴가 겹치는 경우(09 §4.2, §9 "열람과 탈퇴 겹침"). 프로필 조회를 감싸 그 사이에 탈퇴를 커밋한다. */
class ArtistProfileViewWithdrawalOverlapIntegrationTest extends ArtistProfileIntegrationTestBase {

    @MockitoSpyBean
    ArtistProfileTable table;

    @Test
    void 대상_확인_뒤_프로필_조회_전에_탈퇴가_커밋되면_404이다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());
        withdrawJustBeforeProfileRead(artist);

        view(partner, artist)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
    }

    @Test
    void 본인_ARTIST의_열람은_탈퇴가_겹쳐도_계정_상태를_보지_않고_200_빈_값이다() throws Exception {
        putProfile(artist, validProfile()).andExpect(status().isOk());
        withdrawJustBeforeProfileRead(artist);

        view(artist, artist)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value((Object) null))
                .andExpect(jsonPath("$.artistTypes").isEmpty());
    }

    /** 탈퇴 UPDATE가 자동 커밋되고, 같은 트랜잭션에서 트리거가 프로필을 지운 뒤에 실제 조회가 돈다. */
    private void withdrawJustBeforeProfileRead(User target) {
        doAnswer(invocation -> {
                    changeStatus(target, "WITHDRAWN");
                    return invocation.callRealMethod();
                })
                .when(table)
                .find(any());
    }

    private ResultActions view(User caller, User target) throws Exception {
        return mockMvc.perform(
                get("/api/v1/artists/{artistId}/profile", target.getId()).header("Authorization", bearer(caller)));
    }
}
