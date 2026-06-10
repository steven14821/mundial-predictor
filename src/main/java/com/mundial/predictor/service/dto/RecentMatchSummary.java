package com.mundial.predictor.service.dto;

public record RecentMatchSummary(
        String competition,
        String utcDateLabel,
        String opponent,
        String venue,
        String result,
        String status
) {
}
