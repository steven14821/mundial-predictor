package com.mundial.predictor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mundial.predictor.model.Match;
import com.mundial.predictor.repository.MatchRepository;
import com.mundial.predictor.service.dto.GroupStandingRow;
import com.mundial.predictor.service.dto.MatchContextData;
import com.mundial.predictor.service.dto.RecentMatchSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MatchContextService {

    private static final ZoneId COLOMBIA_ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String footballDataApiKey;
    private final String apiFootballApiKey;
    private final String competitionCode;
    private final int season;
    private final ObjectMapper objectMapper;
    private final MatchRepository matchRepository;
    private final HttpClient httpClient;
    private final Map<String, Integer> footballDataTeamIdCache = new HashMap<>();
    private final Map<String, Integer> apiFootballTeamIdCache = new HashMap<>();

    public MatchContextService(
            @Value("${football-data.api.key:}") String apiKey,
            @Value("${api-football.api.key:}") String apiFootballApiKey,
            @Value("${football-data.competition.code:WC}") String competitionCode,
            @Value("${football-data.season:2026}") int season,
            ObjectMapper objectMapper,
            MatchRepository matchRepository
    ) {
        this.footballDataApiKey = apiKey;
        this.apiFootballApiKey = apiFootballApiKey;
        this.competitionCode = competitionCode;
        this.season = season;
        this.objectMapper = objectMapper;
        this.matchRepository = matchRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public MatchContextData getContextForMatch(Match match) {
        List<RecentMatchSummary> homeRecentMatches = new ArrayList<>(buildLocalTeamMatches(match, match.getHomeTeam()));
        List<RecentMatchSummary> awayRecentMatches = new ArrayList<>(buildLocalTeamMatches(match, match.getAwayTeam()));
        List<GroupStandingRow> groupStandings = buildFallbackGroupStandings(match);

        if (homeRecentMatches.isEmpty()) {
            homeRecentMatches = buildNoRecentDataMessage();
        }
        if (awayRecentMatches.isEmpty()) {
            awayRecentMatches = buildNoRecentDataMessage();
        }

        return new MatchContextData(homeRecentMatches, awayRecentMatches, groupStandings);
    }

    private List<RecentMatchSummary> fetchApiFootballRecentMatches(Integer teamId, String teamName) throws Exception {
        if (teamId == null) {
            return List.of();
        }

        int currentYear = LocalDate.now(COLOMBIA_ZONE).getYear();
        List<JsonNode> rawFixtures = new ArrayList<>();
        try {
            rawFixtures.addAll(fetchRawApiFootballFixtures(teamId, currentYear));
            rawFixtures.addAll(fetchRawApiFootballFixtures(teamId, currentYear - 1));
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("do not have access to this season")) {
                // Fallback for Free plan: query 2024 and 2023
                rawFixtures.clear();
                rawFixtures.addAll(fetchRawApiFootballFixtures(teamId, 2024));
                rawFixtures.addAll(fetchRawApiFootballFixtures(teamId, 2023));
            } else {
                throw e;
            }
        }

        // Filter finished and sort descending by timestamp
        List<JsonNode> finishedFixtures = rawFixtures.stream()
                .filter(node -> {
                    String shortStatus = node.path("fixture").path("status").path("short").asText("");
                    return List.of("FT", "AET", "PEN").contains(shortStatus);
                })
                .sorted((n1, n2) -> Long.compare(
                        n2.path("fixture").path("timestamp").asLong(0),
                        n1.path("fixture").path("timestamp").asLong(0)
                ))
                .limit(5)
                .toList();

        List<RecentMatchSummary> matches = new ArrayList<>();
        for (JsonNode node : finishedFixtures) {
            JsonNode homeTeam = node.path("teams").path("home");
            JsonNode awayTeam = node.path("teams").path("away");
            String homeName = translateTeamName(homeTeam.path("name").asText(""));
            String awayName = translateTeamName(awayTeam.path("name").asText(""));
            boolean isHome = sameTeam(homeName, teamName);
            String opponent = isHome ? awayName : homeName;
            String venue = isHome ? "Local" : "Visitante";
            JsonNode goals = node.path("goals");
            String result = goals.path("home").asInt(0) + " - " + goals.path("away").asInt(0);

            matches.add(new RecentMatchSummary(
                    node.path("league").path("name").asText("Competicion"),
                    formatApiFootballDate(node.path("fixture").path("date").asText("")),
                    opponent,
                    venue,
                    result,
                    node.path("fixture").path("status").path("short").asText("FT")
            ));
        }

        return matches;
    }

    private List<JsonNode> fetchRawApiFootballFixtures(Integer teamId, int seasonYear) throws Exception {
        String url = "https://v3.football.api-sports.io/fixtures?team=" + teamId + "&season=" + seasonYear + "&timezone=America/Bogota";
        JsonNode root = executeApiFootballGet(url);
        List<JsonNode> list = new ArrayList<>();
        for (JsonNode node : root.path("response")) {
            list.add(node);
        }
        return list;
    }

    private List<RecentMatchSummary> fetchFootballDataRecentMatches(Integer teamId, String teamName) {
        if (teamId == null) {
            return List.of();
        }

        try {
            LocalDate today = LocalDate.now(COLOMBIA_ZONE);
            String url = "https://api.football-data.org/v4/teams/" + teamId
                    + "/matches?status=FINISHED"
                    + "&dateFrom=" + today.minusYears(2)
                    + "&dateTo=" + today
                    + "&limit=25";
            JsonNode root = executeFootballDataGet(url);
            List<RecentMatchSummary> matches = new ArrayList<>();
            List<JsonNode> apiMatches = new ArrayList<>();
            for (JsonNode node : root.path("matches")) {
                apiMatches.add(node);
            }

            apiMatches.sort(Comparator.comparing(
                    node -> node.path("utcDate").asText(""),
                    Comparator.reverseOrder()
            ));

            for (JsonNode node : apiMatches.stream().limit(5).toList()) {
                String homeName = node.path("homeTeam").path("name").asText("");
                String awayName = node.path("awayTeam").path("name").asText("");
                boolean isHome = homeName.equalsIgnoreCase(teamName) || normalize(homeName).equalsIgnoreCase(normalize(teamName));
                String opponent = isHome ? awayName : homeName;
                String venue = isHome ? "Local" : "Visitante";
                JsonNode fullTime = node.path("score").path("fullTime");
                String result = (fullTime.path("home").isNumber() ? fullTime.path("home").asInt() : 0)
                        + " - " +
                        (fullTime.path("away").isNumber() ? fullTime.path("away").asInt() : 0);

                matches.add(new RecentMatchSummary(
                        node.path("competition").path("name").asText("Competicion"),
                        formatColombiaDate(node.path("utcDate").asText("")),
                        opponent,
                        venue,
                        result,
                        node.path("status").asText("")
                ));
            }
            return matches;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Integer resolveApiFootballTeamId(String teamName) throws Exception {
        if (teamName == null || teamName.isBlank()) {
            return null;
        }

        String cacheKey = normalize(teamName);
        if (apiFootballTeamIdCache.containsKey(cacheKey)) {
            return apiFootballTeamIdCache.get(cacheKey);
        }

        String queryName = URLEncoder.encode(toEnglishTeamName(teamName), StandardCharsets.UTF_8);
        String url = "https://v3.football.api-sports.io/teams?name=" + queryName;
        JsonNode root = executeApiFootballGet(url);
        Integer fallbackId = null;

        for (JsonNode node : root.path("response")) {
            JsonNode team = node.path("team");
            String apiName = team.path("name").asText("");
            String country = team.path("country").asText("");
            if (sameTeam(apiName, teamName) || sameTeam(country, teamName)) {
                Integer resolvedId = team.path("id").isNumber() ? team.path("id").asInt() : null;
                apiFootballTeamIdCache.put(cacheKey, resolvedId);
                return resolvedId;
            }
            if (fallbackId == null && team.path("id").isNumber()) {
                fallbackId = team.path("id").asInt();
            }
        }

        apiFootballTeamIdCache.put(cacheKey, fallbackId);
        return fallbackId;
    }

    private Integer resolveFootballDataTeamId(Integer existingTeamId, String teamName) {
        if (existingTeamId != null) {
            return existingTeamId;
        }
        if (teamName == null || teamName.isBlank()) {
            return null;
        }

        String cacheKey = normalize(teamName);
        if (footballDataTeamIdCache.containsKey(cacheKey)) {
            return footballDataTeamIdCache.get(cacheKey);
        }

        try {
            String url = "https://api.football-data.org/v4/competitions/" + competitionCode + "/teams?season=" + season;
            JsonNode root = executeFootballDataGet(url);
            for (JsonNode team : root.path("teams")) {
                String apiName = team.path("name").asText("");
                String shortName = team.path("shortName").asText("");
                String tla = team.path("tla").asText("");
                if (sameTeam(apiName, teamName) || sameTeam(shortName, teamName) || sameTeam(tla, teamName)) {
                    Integer resolvedId = team.path("id").isNumber() ? team.path("id").asInt() : null;
                    footballDataTeamIdCache.put(cacheKey, resolvedId);
                    return resolvedId;
                }
            }
        } catch (Exception ignored) {
            return null;
        }

        footballDataTeamIdCache.put(cacheKey, null);
        return null;
    }

    private List<GroupStandingRow> fetchGroupStandings(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            return List.of();
        }

        try {
            String url = "https://api.football-data.org/v4/competitions/" + competitionCode + "/standings?season=" + season;
            JsonNode root = executeFootballDataGet(url);
            List<GroupStandingRow> rows = new ArrayList<>();

            for (JsonNode standing : root.path("standings")) {
                String rawGroup = standing.path("group").asText("");
                if (!normalizeGroup(rawGroup).equalsIgnoreCase(groupCode)) {
                    continue;
                }

                for (JsonNode row : standing.path("table")) {
                    JsonNode team = row.path("team");
                    rows.add(new GroupStandingRow(
                            row.path("position").asInt(),
                            team.path("name").asText("Equipo"),
                            team.path("crest").asText(null),
                            row.path("playedGames").asInt(),
                            row.path("won").asInt(),
                            row.path("draw").asInt(),
                            row.path("lost").asInt(),
                            row.path("points").asInt(),
                            row.path("goalsFor").asInt(),
                            row.path("goalsAgainst").asInt(),
                            row.path("goalDifference").asInt()
                    ));
                }
                break;
            }

            return rows;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<GroupStandingRow> buildFallbackGroupStandings(Match match) {
        Map<String, LocalStanding> standings = new LinkedHashMap<>();
        List<Match> groupMatches = match.getMatchGroup() == null || match.getMatchGroup().isBlank()
                ? List.of(match)
                : matchRepository.findByMatchGroupOrderByMatchDateAsc(match.getMatchGroup());

        for (Match groupMatch : groupMatches) {
            addTeam(standings, groupMatch.getHomeTeam(), groupMatch.getHomeFlag());
            addTeam(standings, groupMatch.getAwayTeam(), groupMatch.getAwayFlag());

            if (groupMatch.isFinished()
                    && groupMatch.getHomeScore() != null
                    && groupMatch.getAwayScore() != null) {
                applyResult(standings, groupMatch);
            }
        }

        addTeam(standings, match.getHomeTeam(), match.getHomeFlag());
        addTeam(standings, match.getAwayTeam(), match.getAwayFlag());

        List<LocalStanding> ordered = standings.values().stream()
                .sorted(Comparator
                        .comparingInt(LocalStanding::points).reversed()
                        .thenComparing(Comparator.comparingInt(LocalStanding::goalDifference).reversed())
                        .thenComparing(Comparator.comparingInt(LocalStanding::goalsFor).reversed())
                        .thenComparing(LocalStanding::teamName))
                .toList();

        List<GroupStandingRow> rows = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            LocalStanding standing = ordered.get(index);
            rows.add(new GroupStandingRow(
                    index + 1,
                    standing.teamName(),
                    standing.teamFlag(),
                    standing.playedGames(),
                    standing.won(),
                    standing.draw(),
                    standing.lost(),
                    standing.points(),
                    standing.goalsFor(),
                    standing.goalsAgainst(),
                    standing.goalDifference()
            ));
        }
        return rows;
    }

    private List<RecentMatchSummary> buildLocalTeamMatches(Match currentMatch, String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return List.of();
        }

        List<RecentMatchSummary> relatedMatches = matchRepository.findAllByOrderByMatchDateAsc().stream()
                .filter(match -> match.getId() == null || !match.getId().equals(currentMatch.getId()))
                .filter(match -> sameTeam(match.getHomeTeam(), teamName) || sameTeam(match.getAwayTeam(), teamName))
                .filter(Match::isFinished)
                .sorted(Comparator.comparing(
                        Match::getMatchDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(5)
                .map(match -> toLocalSummary(match, teamName))
                .toList();

        if (!relatedMatches.isEmpty()) {
            return relatedMatches;
        }

        return List.of();
    }

    private List<RecentMatchSummary> buildNoRecentDataMessage() {
        return List.of(new RecentMatchSummary(
                "Historial local",
                "",
                "Sin partidos jugados",
                "Local",
                "Los resultados aparecerán cuando juegues y finalices partidos en este simulador.",
                "Pendiente"
        ));
    }

    private RecentMatchSummary toLocalSummary(Match match, String teamName) {
        boolean isHome = sameTeam(match.getHomeTeam(), teamName);
        String opponent = isHome ? match.getAwayTeam() : match.getHomeTeam();
        String venue = isHome ? "Local" : "Visitante";
        String result = match.isFinished() && match.getHomeScore() != null && match.getAwayScore() != null
                ? match.getHomeScore() + " - " + match.getAwayScore()
                : "Pendiente";
        String status = match.isFinished() ? "Finalizado" : "Programado";

        return new RecentMatchSummary(
                match.getPhase() != null ? match.getPhase().getDisplayName() : "Mundial 2026",
                match.getMatchDate() != null ? match.getMatchDate().format(DATE_FORMATTER) : "",
                opponent,
                venue,
                result,
                status
        );
    }

    private void addTeam(Map<String, LocalStanding> standings, String teamName, String teamFlag) {
        if (teamName == null || teamName.isBlank()) {
            return;
        }

        standings.computeIfAbsent(normalize(teamName), ignored -> new LocalStanding(teamName, teamFlag));
    }

    private void applyResult(Map<String, LocalStanding> standings, Match match) {
        LocalStanding home = standings.get(normalize(match.getHomeTeam()));
        LocalStanding away = standings.get(normalize(match.getAwayTeam()));
        if (home == null || away == null) {
            return;
        }

        int homeScore = match.getHomeScore();
        int awayScore = match.getAwayScore();
        home.record(homeScore, awayScore);
        away.record(awayScore, homeScore);
    }

    private boolean sameTeam(String source, String target) {
        String normalizedSource = normalize(source);
        String normalizedTarget = normalize(target);
        return normalizedSource.equalsIgnoreCase(normalizedTarget)
                || normalizedSource.equalsIgnoreCase(normalize(toEnglishTeamName(target)))
                || normalize(toEnglishTeamName(source)).equalsIgnoreCase(normalizedTarget);
    }

    private String toEnglishTeamName(String teamName) {
        if (teamName == null) {
            return "";
        }

        return switch (normalize(teamName).toLowerCase()) {
            case "mexico" -> "Mexico";
            case "sudafrica" -> "South Africa";
            case "corea del sur" -> "Korea Republic";
            case "estados unidos" -> "United States";
            case "canada" -> "Canada";
            case "brasil" -> "Brazil";
            case "alemania" -> "Germany";
            case "espana" -> "Spain";
            case "francia" -> "France";
            case "inglaterra" -> "England";
            case "japon" -> "Japan";
            case "arabia saudita" -> "Saudi Arabia";
            case "paises bajos" -> "Netherlands";
            case "belgica" -> "Belgium";
            case "suiza" -> "Switzerland";
            case "marruecos" -> "Morocco";
            case "tunez" -> "Tunisia";
            case "camerun" -> "Cameroon";
            case "catar" -> "Qatar";
            case "nueva zelanda" -> "New Zealand";
            case "egipto" -> "Egypt";
            case "argelia" -> "Algeria";
            case "costa de marfil" -> "Cote d'Ivoire";
            case "turquia" -> "Turkey";
            case "irak" -> "Iraq";
            default -> teamName;
        };
    }

    private JsonNode executeFootballDataGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("X-Auth-Token", footballDataApiKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private JsonNode executeApiFootballGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("x-apisports-key", apiFootballApiKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.path("errors").isEmpty() && root.path("errors").size() > 0) {
            throw new IllegalStateException(root.path("errors").toString());
        }
        return root;
    }

    private String formatColombiaDate(String utcDate) {
        if (utcDate == null || utcDate.isBlank()) {
            return "";
        }
        return OffsetDateTime.parse(utcDate)
                .atZoneSameInstant(COLOMBIA_ZONE)
                .format(DATE_FORMATTER);
    }

    private String formatApiFootballDate(String date) {
        if (date == null || date.isBlank()) {
            return "";
        }
        return OffsetDateTime.parse(date)
                .atZoneSameInstant(COLOMBIA_ZONE)
                .format(DATE_FORMATTER);
    }

    private String translateTeamName(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return "Equipo";
        }

        return switch (normalize(teamName).toLowerCase()) {
            case "south africa" -> "Sudafrica";
            case "korea republic", "south korea" -> "Corea del Sur";
            case "united states", "usa" -> "Estados Unidos";
            case "brazil" -> "Brasil";
            case "germany" -> "Alemania";
            case "spain" -> "Espana";
            case "france" -> "Francia";
            case "england" -> "Inglaterra";
            case "japan" -> "Japon";
            case "saudi arabia" -> "Arabia Saudita";
            case "netherlands" -> "Paises Bajos";
            case "belgium" -> "Belgica";
            case "switzerland" -> "Suiza";
            case "morocco" -> "Marruecos";
            case "tunisia" -> "Tunez";
            case "cameroon" -> "Camerun";
            case "qatar" -> "Catar";
            case "new zealand" -> "Nueva Zelanda";
            case "egypt" -> "Egipto";
            case "algeria" -> "Argelia";
            case "cote d'ivoire", "ivory coast" -> "Costa de Marfil";
            case "turkey" -> "Turquia";
            case "iraq" -> "Irak";
            default -> teamName;
        };
    }

    private String normalizeGroup(String rawGroup) {
        if (rawGroup == null) {
            return "";
        }
        return rawGroup.replace("GROUP_", "");
    }

    private String normalize(String text) {
        return text == null ? "" : text
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                .replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ó", "O").replace("Ú", "U");
    }

    private static class LocalStanding {
        private final String teamName;
        private final String teamFlag;
        private int playedGames;
        private int won;
        private int draw;
        private int lost;
        private int points;
        private int goalsFor;
        private int goalsAgainst;

        LocalStanding(String teamName, String teamFlag) {
            this.teamName = teamName;
            this.teamFlag = teamFlag;
        }

        void record(int goalsFor, int goalsAgainst) {
            this.playedGames++;
            this.goalsFor += goalsFor;
            this.goalsAgainst += goalsAgainst;

            if (goalsFor > goalsAgainst) {
                this.won++;
                this.points += 3;
            } else if (goalsFor == goalsAgainst) {
                this.draw++;
                this.points += 1;
            } else {
                this.lost++;
            }
        }

        String teamName() { return teamName; }
        String teamFlag() { return teamFlag; }
        int playedGames() { return playedGames; }
        int won() { return won; }
        int draw() { return draw; }
        int lost() { return lost; }
        int points() { return points; }
        int goalsFor() { return goalsFor; }
        int goalsAgainst() { return goalsAgainst; }
        int goalDifference() { return goalsFor - goalsAgainst; }
    }
}
