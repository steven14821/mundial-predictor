package com.mundial.predictor;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.Phase;
import com.mundial.predictor.repository.MatchRepository;
import com.mundial.predictor.service.WorldCupSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class KnockoutSyncIntegrationTest {

    @Autowired
    private WorldCupSyncService worldCupSyncService;

    @Autowired
    private MatchRepository matchRepository;

    @Test
    void syncKnockoutShouldFillKnownTeamsFromApi() {
        if (!worldCupSyncService.isEnabled()) {
            return;
        }

        WorldCupSyncService.SyncResult result = worldCupSyncService.syncKnockoutMatches();
        assertTrue(result.success(), result.message());

        List<Match> ronda32 = matchRepository.findByPhaseOrderByMatchDateAsc(Phase.RONDA32);
        long withRealTeams = ronda32.stream()
                .filter(m -> !m.getHomeTeam().startsWith("Ganador") && !"Por definir".equals(m.getHomeTeam())
                        || !m.getAwayTeam().startsWith("Ganador") && !"Por definir".equals(m.getAwayTeam()))
                .count();

        assertTrue(withRealTeams >= 5,
                "Se esperaban al menos 5 cruces de ronda 32 con equipos reales, hubo: " + withRealTeams);
    }
}
