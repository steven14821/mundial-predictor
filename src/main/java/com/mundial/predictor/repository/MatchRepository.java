package com.mundial.predictor.repository;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.Phase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findAllByOrderByMatchDateAsc();
    List<Match> findByFinishedFalseAndMatchDateAfterOrderByMatchDateAsc(LocalDateTime date);
    List<Match> findByPhaseOrderByMatchDateAsc(Phase phase);
    List<Match> findByPhase(Phase phase);
    List<Match> findByMatchGroupOrderByMatchDateAsc(String matchGroup);
    long countByPhase(Phase phase);
    void deleteByPhase(Phase phase);
    Optional<Match> findByHomeTeamExternalIdAndAwayTeamExternalId(Integer homeId, Integer awayId);
}
