package com.mundial.predictor.service;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.Prediction;
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

    public PredictionService(PredictionRepository predictionRepository, UserRepository userRepository) {
        this.predictionRepository = predictionRepository;
        this.userRepository = userRepository;
    }

    public Optional<Prediction> findByUserAndMatch(User user, Match match) {
        return predictionRepository.findByUserAndMatch(user, match);
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
        Prediction prediction = predictionRepository.findByUserAndMatch(user, match)
                .orElse(new Prediction());

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
            // 2. Ganador/empate correcto Y diferencia de gol exacta
            if ((pH - pA) == (rH - rA)) {
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
                    if (p.getPointsEarned() == null) {
                        int pts = computePoints(p, m);
                        p.setPointsEarned(pts);
                        predictionRepository.save(p);
                    }
                    total += p.getPointsEarned();
                }
            }
            user.setTotalPoints(total);
            userRepository.save(user);
        }
    }

    public Map<Long, Prediction> getPredictionsMapByMatch(User user, List<Match> matches) {
        Map<Long, Prediction> map = new HashMap<>();
        for (Match match : matches) {
            predictionRepository.findByUserAndMatch(user, match)
                    .ifPresent(p -> map.put(match.getId(), p));
        }
        return map;
    }
}
