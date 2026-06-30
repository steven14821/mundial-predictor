package com.mundial.predictor.service;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.Prediction;
import com.mundial.predictor.repository.MatchRepository; // Import MatchRepository
import com.mundial.predictor.model.User;
import com.mundial.predictor.repository.PredictionRepository;
import com.mundial.predictor.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository; // Declare MatchRepository

    public PredictionService(PredictionRepository predictionRepository, UserRepository userRepository, MatchRepository matchRepository) { // Inject MatchRepository
        this.predictionRepository = predictionRepository;
        this.userRepository = userRepository;
        this.matchRepository = matchRepository; // Initialize MatchRepository
    }

    public Optional<Prediction> findByUserAndMatch(User user, Match match) {
        return predictionRepository.findByUserAndMatch(user, match);
    }

    public Optional<Prediction> findPredictionForUserAndMatch(User user, Match match) {
        return Optional.ofNullable(resolvePredictionForMatch(predictionRepository.findByUser(user), match));
    }

    public List<Prediction> findByMatch(Match match) {
        return predictionRepository.findByMatch(match);
    }

    public Map<Long, Prediction> getPredictionsMapForMatch(Match match) {
        return predictionRepository.findByMatch(match).stream()
                .collect(Collectors.toMap(prediction -> prediction.getUser().getId(), prediction -> prediction));
    }

    public Map<Long, Map<Long, Prediction>> getPredictionsByMatchForUsers(List<User> users, List<Match> matches) {
        Map<Long, Map<Long, Prediction>> result = new HashMap<>();
        for (Match match : matches) {
            Map<Long, Prediction> byUser = new HashMap<>();
            for (User user : users) {
                predictionRepository.findByUserAndMatch(user, match)
                        .ifPresent(prediction -> byUser.put(user.getId(), prediction));
            }
            result.put(match.getId(), byUser);
        }
        return result;
    }

    @Transactional
    public void savePrediction(User user, Match match, int homeScore, int awayScore) {
        Prediction existing = resolvePredictionForMatch(predictionRepository.findByUser(user), match);
        Prediction prediction = existing != null ? existing : new Prediction();

        prediction.setUser(user);
        prediction.setMatch(match);
        prediction.setPredictedHomeScore(homeScore);
        prediction.setPredictedAwayScore(awayScore);
        prediction.setPointsEarned(null); // se recalcula cuando termina el partido

        predictionRepository.save(prediction);
    }

    /**
     * Calcula puntos para todas las predicciones de un partido y actualiza totales.
     * Llamado por el admin al registrar el resultado final.
     */
    @Transactional
    public void calculatePoints(Match match) {
        List<Prediction> predictions = predictionRepository.findByMatch(match);

        for (Prediction prediction : predictions) {
            int pts = computePoints(prediction, match);
            prediction.setPointsEarned(pts);
            predictionRepository.save(prediction);
        }

        recalculateAllTotals();
    }

    /**
     * Sistema de puntuación:
     * - Resultado exacto (ej. predijo 2-1, salió 2-1)                     → 3 puntos
     * - Ganador/empate correcto Y diferencia de gol (predijo 3-0, salió 4-1) → 2 puntos
     * - Ganador/empate correcto solamente (predijo 2-0, salió 4-1)          → 1 punto
     * - Incorrecto                                                         → 0 puntos
     */
    private int computePoints(Prediction p, Match m) {
        if (m.getHomeScore() == null || m.getAwayScore() == null) {
            return 0;
        }
        int pH = p.getPredictedHomeScore();
        int pA = p.getPredictedAwayScore();
        int rH = m.getHomeScore();
        int rA = m.getAwayScore();

        // 1. Resultado exacto
        if (pH == rH && pA == rA) return 3;

        // Validar si acertó al ganador o al empate
        if (Integer.compare(pH, pA) == Integer.compare(rH, rA)) {
            // 2. Ganador correcto Y diferencia de gol exacta (no aplica para empates)
            if (rH != rA && (pH - pA) == (rH - rA)) {
                return 2;
            }
            // 3. Ganador/empate correcto simple
            return 1;
        }

        // 4. Incorrecto
        return 0;
    }

    @Transactional
    public void recalculateAllTotals() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            List<Prediction> predictions = predictionRepository.findByUser(user);
            int total = 0;
            for (Prediction p : predictions) {
                Match m = p.getMatch();
                if (m.isFinished() && m.getHomeScore() != null && m.getAwayScore() != null) {
                    int pts = computePoints(p, m);
                    p.setPointsEarned(pts);
                    predictionRepository.save(p);
                    total += pts;
                }
            }
            user.setTotalPoints(total);
            userRepository.save(user);
        }
    }

    public Map<Long, Prediction> getPredictionsMapByMatch(User user, List<Match> matches) {
        List<Prediction> userPredictions = predictionRepository.findByUser(user);
        Map<Long, Prediction> map = new HashMap<>();
        for (Match match : matches) {
            Prediction prediction = resolvePredictionForMatch(userPredictions, match);
            if (prediction != null) {
                map.put(match.getId(), prediction);
            }
        }
        return map;
    }

    /**
     * Enlaza predicciones a cada partido para la vista de listado.
     * Resuelve duplicados de partido (p. ej. tras sync API) comparando IDs externos.
     */
    public void attachPredictionsForListView(User currentUser, List<User> duelPlayers, List<Match> matches) {
        List<Prediction> myPredictions = predictionRepository.findByUser(currentUser);
        Map<Long, List<Prediction>> predictionsByPlayerId = new HashMap<>();
        predictionsByPlayerId.put(currentUser.getId(), myPredictions);

        for (User player : duelPlayers) {
            if (!predictionsByPlayerId.containsKey(player.getId())) {
                predictionsByPlayerId.put(player.getId(), predictionRepository.findByUser(player));
            }
        }

        for (Match originalMatch : matches) {
            // Recargar el partido de la base de datos para asegurar que tenemos
            // los últimos resultados y el estado 'finished'.
            // Esto es crucial para reflejar las actualizaciones del admin inmediatamente.
            Optional<Match> freshMatchOpt = matchRepository.findById(originalMatch.getId());
            if (freshMatchOpt.isEmpty()) {
                // Si el partido no se encuentra (ej. fue eliminado), lo saltamos.
                // En un escenario normal, todos los partidos deberían existir.
                continue;
            }
            Match freshMatch = freshMatchOpt.get();

            originalMatch.setMyPrediction(resolvePredictionForMatch(myPredictions, freshMatch));

            if (originalMatch.getMyPrediction() != null || freshMatch.isFinished()) {
                if (duelPlayers.size() > 0) {
                    List<Prediction> p1 = predictionsByPlayerId.get(duelPlayers.get(0).getId());
                    originalMatch.setDuelPrediction1(resolvePredictionForMatch(p1, freshMatch));
                }
                if (duelPlayers.size() > 1) {
                    List<Prediction> p2 = predictionsByPlayerId.get(duelPlayers.get(1).getId());
                    originalMatch.setDuelPrediction2(resolvePredictionForMatch(p2, freshMatch));
                }
            }
        }
    }

    private Prediction resolvePredictionForMatch(List<Prediction> predictions, Match match) {
        if (predictions == null || predictions.isEmpty()) {
            return null;
        }
        for (Prediction prediction : predictions) {
            if (matchesSameFixture(prediction.getMatch(), match)) {
                return prediction;
            }
        }
        return null;
    }

    private boolean matchesSameFixture(Match stored, Match listed) {
        // 1. Mismo ID en BD
        if (stored.getId() != null && stored.getId().equals(listed.getId())) {
            return true;
        }
        // 2. Mismos IDs externos (equipos de la API)
        if (stored.getHomeTeamExternalId() != null && listed.getHomeTeamExternalId() != null
                && stored.getAwayTeamExternalId() != null && listed.getAwayTeamExternalId() != null
                && stored.getHomeTeamExternalId().equals(listed.getHomeTeamExternalId())
                && stored.getAwayTeamExternalId().equals(listed.getAwayTeamExternalId())) {
            return true;
        }
        // 3. Fallback: mismos nombres de equipos + misma fecha (cubre partidos re-creados sin externalId)
        if (stored.getHomeTeam() != null && stored.getAwayTeam() != null
                && stored.getMatchDate() != null && listed.getMatchDate() != null
                && stored.getHomeTeam().equalsIgnoreCase(listed.getHomeTeam())
                && stored.getAwayTeam().equalsIgnoreCase(listed.getAwayTeam())
                && stored.getMatchDate().toLocalDate().equals(listed.getMatchDate().toLocalDate())) {
            return true;
        }
        return false;
    }
}
