package com.mundial.predictor.config;

import com.mundial.predictor.model.Role;
import com.mundial.predictor.model.User;
import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.Phase;
import com.mundial.predictor.repository.MatchRepository;
import com.mundial.predictor.repository.UserRepository;
import com.mundial.predictor.service.WorldCupSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedUsers(
            UserRepository userRepository,
            MatchRepository matchRepository,
            WorldCupSyncService worldCupSyncService,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword,
            @Value("${app.player1.username}") String player1Username,
            @Value("${app.player1.password}") String player1Password,
            @Value("${app.player2.username}") String player2Username,
            @Value("${app.player2.password}") String player2Password,
            @Value("${app.seed.world-cup.enabled:true}") boolean worldCupSeedEnabled
    ) {
        return args -> {
            createUserIfMissing(userRepository, passwordEncoder, adminUsername, adminPassword, Role.ADMIN, "Administrador");
            createUserIfMissing(userRepository, passwordEncoder, player1Username, player1Password, Role.PLAYER, "Jugador 1");
            createUserIfMissing(userRepository, passwordEncoder, player2Username, player2Password, Role.PLAYER, "Jugador 2");

            if (worldCupSeedEnabled && matchRepository.count() == 0) {
                matchRepository.saveAll(buildWorldCup2026Schedule());
            }

            if (worldCupSyncService.isEnabled()) {
                worldCupSyncService.syncGroupStageMatches();
            }
        };
    }

    private void createUserIfMissing(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            String username,
            String rawPassword,
            Role role,
            String displayName
    ) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }

        User user = new User(username, passwordEncoder.encode(rawPassword), role, displayName);
        userRepository.save(user);
    }

    private List<Match> buildWorldCup2026Schedule() {
        List<Match> matches = new ArrayList<>();
        seedGroupStage(matches);
        seedRoundOf32(matches);
        seedRoundOf16(matches);
        seedQuarterFinals(matches);
        seedSemiFinals(matches);
        seedFinalWeekend(matches);
        return matches;
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
                matches.add(buildMatch(pairing[0], pairing[1], Phase.GRUPOS, String.valueOf(group), date.atTime(time)));
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
            matches.add(buildMatch("Ganador llave R32-" + (i * 2 - 1), "Ganador llave R32-" + (i * 2), Phase.RONDA32, null, date.atTime(time)));
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
            matches.add(buildMatch("Ganador R32-" + (i * 2 - 1), "Ganador R32-" + (i * 2), Phase.OCTAVOS, null, date.atTime(time)));
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
            matches.add(buildMatch("Ganador OCT-" + (i * 2 - 1), "Ganador OCT-" + (i * 2), Phase.CUARTOS, null, date.atTime(time)));
        }
    }

    private void seedSemiFinals(List<Match> matches) {
        matches.add(buildMatch("Ganador CUA-1", "Ganador CUA-2", Phase.SEMIFINAL, null, LocalDateTime.of(2026, 7, 14, 19, 0)));
        matches.add(buildMatch("Ganador CUA-3", "Ganador CUA-4", Phase.SEMIFINAL, null, LocalDateTime.of(2026, 7, 15, 19, 0)));
    }

    private void seedFinalWeekend(List<Match> matches) {
        matches.add(buildMatch("Perdedor SEMI-1", "Perdedor SEMI-2", Phase.TERCER_PUESTO, null, LocalDateTime.of(2026, 7, 18, 18, 0)));
        matches.add(buildMatch("Ganador SEMI-1", "Ganador SEMI-2", Phase.FINAL, null, LocalDateTime.of(2026, 7, 19, 18, 0)));
    }

    private Match buildMatch(String homeTeam, String awayTeam, Phase phase, String group, LocalDateTime dateTime) {
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
