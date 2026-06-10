package com.mundial.predictor.model;

import jakarta.persistence.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

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
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime matchDate;

    private Integer homeScore;
    private Integer awayScore;

    private boolean finished = false;

    public Match() {}

    public boolean isLocked() {
        return finished || (matchDate != null && LocalDateTime.now().isAfter(matchDate.minusMinutes(30)));
    }

    public boolean isPredictionOpen() {
        return !finished && matchDate != null && LocalDateTime.now().isBefore(matchDate.minusMinutes(30));
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

    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }
}
