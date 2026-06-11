package com.mundial.predictor.service;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    public Match setScore(Long matchId, int homeScore, int awayScore) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Partido no encontrado: " + matchId));
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        match.setFinished(true);
        return matchRepository.save(match);
    }
}