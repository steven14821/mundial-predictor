package com.mundial.predictor.service;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.Phase;
import com.mundial.predictor.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MatchService {

    private final MatchRepository matchRepository;

    public MatchService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    public List<Match> findAll() {
        return matchRepository.findAllByOrderByMatchDateAsc();
    }

    public Optional<Match> findById(Long id) {
        return matchRepository.findById(id);
    }

    public List<Match> findUpcoming() {
        return matchRepository.findByFinishedFalseAndMatchDateAfterOrderByMatchDateAsc(
                LocalDateTime.now(java.time.ZoneId.of("America/Bogota"))
        );
    }

    public Match save(Match match) {
        return matchRepository.save(match);
    }

    @Transactional
    public Match setScore(Long matchId, int homeScore, int awayScore, Integer homePenaltyScore, Integer awayPenaltyScore) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Partido no encontrado: " + matchId));
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setFinished(true);
        if (homePenaltyScore != null && awayPenaltyScore != null) {
            match.setHomePenaltyScore(homePenaltyScore);
            match.setAwayPenaltyScore(awayPenaltyScore);
        }
        return matchRepository.save(match);
    }

    @Transactional
    public int resetKnockoutMatches() {
        List<Phase> knockoutPhases = List.of(
                com.mundial.predictor.model.Phase.RONDA32,
                com.mundial.predictor.model.Phase.OCTAVOS,
                com.mundial.predictor.model.Phase.CUARTOS,
                com.mundial.predictor.model.Phase.SEMIFINAL,
                com.mundial.predictor.model.Phase.TERCER_PUESTO,
                com.mundial.predictor.model.Phase.FINAL
        );

        int affected = 0;

        // Helper: sobrescribe existentes o crea faltantes, y elimina sobrantes
        // 1. Ronda de 32
        List<Match> existing = matchRepository.findByPhaseOrderByMatchDateAsc(Phase.RONDA32);
        List<Match> correct = new ArrayList<>();
        seedRoundOf32(correct);
        affected += syncPhaseToSeed(existing, correct);

        // 2. Octavos
        existing = matchRepository.findByPhaseOrderByMatchDateAsc(Phase.OCTAVOS);
        correct = new ArrayList<>();
        seedRoundOf16(correct);
        affected += syncPhaseToSeed(existing, correct);

        // 3. Cuartos
        existing = matchRepository.findByPhaseOrderByMatchDateAsc(Phase.CUARTOS);
        correct = new ArrayList<>();
        seedQuarterFinals(correct);
        affected += syncPhaseToSeed(existing, correct);

        // 4. Semifinales
        existing = matchRepository.findByPhaseOrderByMatchDateAsc(Phase.SEMIFINAL);
        correct = new ArrayList<>();
        seedSemiFinals(correct);
        affected += syncPhaseToSeed(existing, correct);

        // 5. Tercer puesto
        existing = matchRepository.findByPhaseOrderByMatchDateAsc(Phase.TERCER_PUESTO);
        correct = new ArrayList<>();
        correct.add(buildMatch("Perdedor SEMI-1", "Perdedor SEMI-2", Phase.TERCER_PUESTO, null, LocalDateTime.of(2026, 7, 18, 18, 0)));
        affected += syncPhaseToSeed(existing, correct);

        // 6. Final
        existing = matchRepository.findByPhaseOrderByMatchDateAsc(Phase.FINAL);
        correct = new ArrayList<>();
        correct.add(buildMatch("Ganador SEMI-1", "Ganador SEMI-2", Phase.FINAL, null, LocalDateTime.of(2026, 7, 19, 18, 0)));
        affected += syncPhaseToSeed(existing, correct);

        matchRepository.flush();
        return affected;
    }

    private int syncPhaseToSeed(List<Match> existing, List<Match> correct) {
        int count = 0;
        java.util.HashSet<Long> usedIds = new java.util.HashSet<>();
    
        for (Match seed : correct) {
            // Try to find an existing match that exactly matches the seed's home team, away team, and date
            Optional<Match> foundExisting = existing.stream()
                    .filter(candidate -> !usedIds.contains(candidate.getId()))
                    .filter(candidate ->
                            seed.getHomeTeam().equals(candidate.getHomeTeam()) &&
                            seed.getAwayTeam().equals(candidate.getAwayTeam()) &&
                            seed.getMatchDate().equals(candidate.getMatchDate()))
                    .findFirst();
    
            Match matchToSave;
            if (foundExisting.isPresent()) {
                matchToSave = foundExisting.get();
                overwriteWithSeed(matchToSave, seed);
                usedIds.add(matchToSave.getId());
            } else {
                matchToSave = seed; // This is a new match
            }
            matchRepository.save(matchToSave);
            count++;
        }
    
        for (Match leftover : existing) {
            if (!usedIds.contains(leftover.getId())) {
                matchRepository.delete(leftover);
                System.out.println("Deleted leftover match: " + leftover.getHomeTeam() + " vs " + leftover.getAwayTeam() + " (" + leftover.getPhase() + ")");
            }
        }
        return count;
    }

    private void overwriteWithSeed(Match target, Match seed) {
        target.setHomeTeam(seed.getHomeTeam());
        target.setAwayTeam(seed.getAwayTeam());
        target.setMatchDate(seed.getMatchDate());
        target.setPhase(seed.getPhase());
        target.setMatchGroup(seed.getMatchGroup());
        target.setHomeFlag(null);
        target.setAwayFlag(null);
        target.setHomeTeamExternalId(null);
        target.setAwayTeamExternalId(null);
        target.setHomeScore(null);
        target.setAwayScore(null);
        target.setHomeExtraTimeScore(null);
        target.setAwayExtraTimeScore(null);
        target.setHomePenaltyScore(null);
        target.setAwayPenaltyScore(null);
        target.setFinished(false);
    }

    @Transactional
    public void seedAllMatchesIfMissing() {
        if (matchRepository.count() == 0) {
            List<Match> matches = new ArrayList<>();
            seedGroupStage(matches);
            seedRoundOf32(matches);
            seedRoundOf16(matches);
            seedQuarterFinals(matches);
            seedSemiFinals(matches);
            seedFinalWeekend(matches);
            matchRepository.saveAll(matches);
        } else if (matchRepository.countByPhase(com.mundial.predictor.model.Phase.RONDA32) == 0) {
            List<Match> playoffs = new ArrayList<>();
            seedRoundOf32(playoffs);
            seedRoundOf16(playoffs);
            seedQuarterFinals(playoffs);
            seedSemiFinals(playoffs);
            seedFinalWeekend(playoffs);
            matchRepository.saveAll(playoffs);
        }
    }

    private void seedGroupStage(List<Match> matches) {
        LocalDate startDate = LocalDate.of(2026, 6, 11);
        LocalTime[] slots = {
                LocalTime.of(13, 0),
                LocalTime.of(16, 0),
                LocalTime.of(19, 0),
                LocalTime.of(22, 0)
        };
        int globalIndex = 0;

        for (char group = 'A'; group <= 'L'; group++) {
            String prefix = "Grupo " + group;
            String[][] pairings = {
                    {prefix + " 1", prefix + " 2"},
                    {prefix + " 3", prefix + " 4"},
                    {prefix + " 1", prefix + " 3"},
                    {prefix + " 2", prefix + " 4"},
                    {prefix + " 1", prefix + " 4"},
                    {prefix + " 2", prefix + " 3"}
            };

            for (String[] pairing : pairings) {
                LocalDate date = startDate.plusDays(globalIndex / slots.length);
                LocalTime time = slots[globalIndex % slots.length];
                matches.add(buildMatch(pairing[0], pairing[1], com.mundial.predictor.model.Phase.GRUPOS, String.valueOf(group), date.atTime(time)));
                globalIndex++;
            }
        }
    }

    private void seedRoundOf32(List<Match> matches) {
        LocalDate startDate = LocalDate.of(2026, 6, 29);
        LocalTime[] slots = {
                LocalTime.of(13, 0),
                LocalTime.of(17, 0),
                LocalTime.of(20, 0),
                LocalTime.of(23, 0)
        };

        for (int i = 1; i <= 16; i++) {
            LocalDate date = startDate.plusDays((i - 1) / slots.length);
            LocalTime time = slots[(i - 1) % slots.length];
            matches.add(buildMatch("Ganador llave R32-" + (i * 2 - 1), "Ganador llave R32-" + (i * 2), com.mundial.predictor.model.Phase.RONDA32, null, date.atTime(time)));
        }
    }

    private void seedRoundOf16(List<Match> matches) {
        LocalDate startDate = LocalDate.of(2026, 7, 4);
        LocalTime[] slots = {
                LocalTime.of(15, 0),
                LocalTime.of(20, 0)
        };

        for (int i = 1; i <= 8; i++) {
            LocalDate date = startDate.plusDays((i - 1) / slots.length);
            LocalTime time = slots[(i - 1) % slots.length];
            matches.add(buildMatch("Ganador R32-" + (i * 2 - 1), "Ganador R32-" + (i * 2), com.mundial.predictor.model.Phase.OCTAVOS, null, date.atTime(time)));
        }
    }

    private void seedQuarterFinals(List<Match> matches) {
        LocalDate startDate = LocalDate.of(2026, 7, 9);
        LocalTime[] slots = {
                LocalTime.of(16, 0),
                LocalTime.of(21, 0)
        };

        for (int i = 1; i <= 4; i++) {
            LocalDate date = startDate.plusDays((i - 1) / slots.length);
            LocalTime time = slots[(i - 1) % slots.length];
            matches.add(buildMatch("Ganador OCT-" + (i * 2 - 1), "Ganador OCT-" + (i * 2), com.mundial.predictor.model.Phase.CUARTOS, null, date.atTime(time)));
        }
    }

    private void seedSemiFinals(List<Match> matches) {
        matches.add(buildMatch("Ganador CUA-1", "Ganador CUA-2", com.mundial.predictor.model.Phase.SEMIFINAL, null, LocalDateTime.of(2026, 7, 14, 19, 0)));
        matches.add(buildMatch("Ganador CUA-3", "Ganador CUA-4", com.mundial.predictor.model.Phase.SEMIFINAL, null, LocalDateTime.of(2026, 7, 15, 19, 0)));
    }

    private void seedFinalWeekend(List<Match> matches) {
        matches.add(buildMatch("Perdedor SEMI-1", "Perdedor SEMI-2", com.mundial.predictor.model.Phase.TERCER_PUESTO, null, LocalDateTime.of(2026, 7, 18, 18, 0)));
        matches.add(buildMatch("Ganador SEMI-1", "Ganador SEMI-2", com.mundial.predictor.model.Phase.FINAL, null, LocalDateTime.of(2026, 7, 19, 18, 0)));
    }

    private Match buildMatch(String homeTeam, String awayTeam, com.mundial.predictor.model.Phase phase, String group, LocalDateTime dateTime) {
        Match match = new Match();
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setPhase(phase);
        match.setMatchGroup(group);
        match.setMatchDate(dateTime);
        match.setFinished(false);
        return match;
    }
}