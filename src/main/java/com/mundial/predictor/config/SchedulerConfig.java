package com.mundial.predictor.config;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.service.PredictionService;
import com.mundial.predictor.service.WorldCupSyncService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Sincroniza resultados automáticamente cada 10 minutos.
 * Solo actúa si FOOTBALL_DATA_API_KEY está configurada.
 * Calcula puntos automáticamente para los partidos que recién terminaron.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    private final WorldCupSyncService worldCupSyncService;
    private final PredictionService predictionService;

    public SchedulerConfig(WorldCupSyncService worldCupSyncService, PredictionService predictionService) {
        this.worldCupSyncService = worldCupSyncService;
        this.predictionService = predictionService;
    }

    // Cada 10 minutos: cron = "0 */10 * * * *"
    @Scheduled(cron = "0 */10 * * * *")
    public void autoSyncResults() {
        if (!worldCupSyncService.isEnabled()) {
            return; // No hay API key, no hacer nada
        }

        System.out.println("[Scheduler] Sincronizando resultados automaticamente...");

        WorldCupSyncService.SyncResultWithMatches result = worldCupSyncService.syncResults();

        // Calcular puntos para los partidos que recién terminaron
        for (Match match : result.newlyFinishedMatches()) {
            predictionService.calculatePoints(match);
            System.out.println("[Scheduler] Puntos calculados para: "
                    + match.getHomeTeam() + " vs " + match.getAwayTeam());
        }

        System.out.println("[Scheduler] " + result.message());
    }
}
