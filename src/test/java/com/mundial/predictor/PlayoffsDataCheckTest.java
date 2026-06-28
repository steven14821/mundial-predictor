package com.mundial.predictor;

import com.mundial.predictor.model.Phase;
import com.mundial.predictor.repository.MatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlayoffsDataCheckTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MatchRepository matchRepository;

    @Test
    @WithUserDetails("Steven")
    void playoffsPageShouldRenderKnockoutMatches() throws Exception {
        long ronda32 = matchRepository.countByPhase(Phase.RONDA32);
        long octavos = matchRepository.countByPhase(Phase.OCTAVOS);
        long cuartos = matchRepository.countByPhase(Phase.CUARTOS);
        long semifinal = matchRepository.countByPhase(Phase.SEMIFINAL);
        long finals = matchRepository.countByPhase(Phase.FINAL) + matchRepository.countByPhase(Phase.TERCER_PUESTO);

        long expected = ronda32 + octavos + cuartos + semifinal + finals;

        String html = mockMvc.perform(get("/playoffs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long cardCount = html.split("class=\"playoff-card-wrapper\"", -1).length - 1;

        if (expected > 0) {
            assertTrue(cardCount >= expected,
                    "Se esperaban al menos " + expected + " cards de playoffs, pero se renderizaron " + cardCount);
        }
    }
}
