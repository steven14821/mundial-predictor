package com.mundial.predictor.model;

import jakarta.persistence.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String homeTeam;

    @Column(nullable = false)
    private String awayTeam;

    private String homeFlag;
    private String awayFlag;
    private Integer homeTeamExternalId;
    private Integer awayTeamExternalId;

    @Enumerated(EnumType.STRING)
    private Phase phase;

    private String matchGroup;

    @Column(nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd\'T\'HH:mm")
    private LocalDateTime matchDate;

    private Integer homeScore;
    private Integer awayScore;

    private Integer homeExtraTimeScore;
    private Integer awayExtraTimeScore;

    private Integer homePenaltyScore;
    private Integer awayPenaltyScore;

    private boolean finished = false;

    @Transient
    private Prediction myPrediction;

    @Transient
    private Prediction duelPrediction1;

    @Transient
    private Prediction duelPrediction2;

    public Match() {}

    // Zona horaria Colombia (UTC-5) - las fechas de partidos se guardan en hora Colombia
    private static final ZoneId COLOMBIA_ZONE = ZoneId.of("America/Bogota");

    public boolean isLocked() {
        return finished || (matchDate != null && LocalDateTime.now(COLOMBIA_ZONE).isAfter(matchDate.minusMinutes(30)));
    }

    public boolean isPredictionOpen() {
        return !finished && matchDate != null && LocalDateTime.now(COLOMBIA_ZONE).isBefore(matchDate.minusMinutes(30));
    }

    public String getStatusLabel() {
        if (finished) {
            return "Terminado";
        }
        if (isLocked()) {
            return "Bloqueado";
        }
        return "En prediccion";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }

    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }

    public String getHomeFlag() { return homeFlag; }
    public void setHomeFlag(String homeFlag) { this.homeFlag = homeFlag; }

    public String getAwayFlag() { return awayFlag; }
    public void setAwayFlag(String awayFlag) { this.awayFlag = awayFlag; }

    public Integer getHomeTeamExternalId() { return homeTeamExternalId; }
    public void setHomeTeamExternalId(Integer homeTeamExternalId) { this.homeTeamExternalId = homeTeamExternalId; }

    public Integer getAwayTeamExternalId() { return awayTeamExternalId; }
    public void setAwayTeamExternalId(Integer awayTeamExternalId) { this.awayTeamExternalId = awayTeamExternalId; }

    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }

    public String getMatchGroup() { return matchGroup; }
    public void setMatchGroup(String matchGroup) { this.matchGroup = matchGroup; }

    public LocalDateTime getMatchDate() { return matchDate; }
    public void setMatchDate(LocalDateTime matchDate) { this.matchDate = matchDate; }

    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }

    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }

    public Integer getHomeExtraTimeScore() { return homeExtraTimeScore; }
    public void setHomeExtraTimeScore(Integer homeExtraTimeScore) { this.homeExtraTimeScore = homeExtraTimeScore; }

    public Integer getAwayExtraTimeScore() { return awayExtraTimeScore; }
    public void setAwayExtraTimeScore(Integer awayExtraTimeScore) { this.awayExtraTimeScore = awayExtraTimeScore; }

    public Integer getHomePenaltyScore() { return homePenaltyScore; }
    public void setHomePenaltyScore(Integer homePenaltyScore) { this.homePenaltyScore = homePenaltyScore; }

    public Integer getAwayPenaltyScore() { return awayPenaltyScore; }
    public void setAwayPenaltyScore(Integer awayPenaltyScore) { this.awayPenaltyScore = awayPenaltyScore; }

    public boolean hasPenalties() {
        return homePenaltyScore != null && awayPenaltyScore != null;
    }

    public boolean hasExtraTime() {
        return homeExtraTimeScore != null && awayExtraTimeScore != null;
    }

    public String getDisplayScore() {
        if (homeScore == null || awayScore == null) return "-";

        // Armar score final: 90min + ET (si hubo)
        int displayHome = homeScore + (homeExtraTimeScore != null ? homeExtraTimeScore : 0);
        int displayAway = awayScore + (awayExtraTimeScore != null ? awayExtraTimeScore : 0);
        String base = displayHome + " - " + displayAway;

        if (hasPenalties()) {
            base += " (pen: " + homePenaltyScore + " - " + awayPenaltyScore + ")";
        } else if (hasExtraTime()) {
            base += " (aet)";
        }

        return base;
    }

    /**
     * Retorna el nombre del equipo ganador (quien avanzó).
     * En caso de penales, el ganador es quien tenga más goles en la tanda.
     * Si el partido no terminó o está empatado sin penales, retorna null.
     */
    public String getWinner() {
        if (!finished || homeScore == null || awayScore == null) return null;

        if (hasPenalties()) {
            if (homePenaltyScore > awayPenaltyScore) return homeTeam;
            if (awayPenaltyScore > homePenaltyScore) return awayTeam;
            return null;
        }

        // Comparar score total (90min + ET si hubo)
        int totalHome = homeScore + (homeExtraTimeScore != null ? homeExtraTimeScore : 0);
        int totalAway = awayScore + (awayExtraTimeScore != null ? awayExtraTimeScore : 0);

        if (totalHome > totalAway) return homeTeam;
        if (totalAway > totalHome) return awayTeam;
        return null; // empate sin penales (fase de grupos)
    }

    /**
     * true si el equipo local avanzó (ganó en 90min o en penales).
     */
    public boolean isHomeWinner() {
        return homeTeam != null && homeTeam.equals(getWinner());
    }

    /**
     * true si el equipo visitante avanzó (ganó en 90min o en penales).
     */
    public boolean isAwayWinner() {
        return awayTeam != null && awayTeam.equals(getWinner());
    }

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }

    public Prediction getMyPrediction() { return myPrediction; }
    public void setMyPrediction(Prediction myPrediction) { this.myPrediction = myPrediction; }

    public Prediction getDuelPrediction1() { return duelPrediction1; }
    public void setDuelPrediction1(Prediction duelPrediction1) { this.duelPrediction1 = duelPrediction1; }

    public Prediction getDuelPrediction2() { return duelPrediction2; }
    public void setDuelPrediction2(Prediction duelPrediction2) { this.duelPrediction2 = duelPrediction2; }
}

