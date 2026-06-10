package com.mundial.predictor.service.dto;

public record GroupStandingRow(
        int position,
        String teamName,
        String teamFlag,
        int playedGames,
        int won,
        int draw,
        int lost,
        int points,
        int goalsFor,
        int goalsAgainst,
        int goalDifference
) {
}
