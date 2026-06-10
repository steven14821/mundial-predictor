package com.mundial.predictor.service.dto;

import java.util.List;

public record MatchContextData(
        List<RecentMatchSummary> homeRecentMatches,
        List<RecentMatchSummary> awayRecentMatches,
        List<GroupStandingRow> groupStandings
) {
}
