package com.mundial.predictor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.Phase;
import com.mundial.predictor.repository.MatchRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorldCupSyncService {

    private static final ZoneId COLOMBIA_ZONE = ZoneId.of("America/Bogota");
    private static final Map<String, String> TEAM_NAME_ES = buildTeamNameMap();
    private static final Map<String, String> TEAM_FLAG_BY_TLA = buildTeamFlagMap();

    private final MatchRepository matchRepository;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String competitionCode;
    private final int season;
    private final HttpClient httpClient;

    public WorldCupSyncService(
            MatchRepository matchRepository,
            ObjectMapper objectMapper,
            @Value("${football-data.api.key:}") String apiKey,
            @Value("${football-data.competition.code:WC}") String competitionCode,
            @Value("${football-data.season:2026}") int season
    ) {
        this.matchRepository = matchRepository;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.competitionCode = competitionCode;
        this.season = season;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Transactional
    public SyncResult syncGroupStageMatches() {
        if (!isEnabled()) {
            return new SyncResult(false, "FOOTBALL_DATA_API_KEY no configurada.");
        }

        try {
            List<Match> fetchedMatches = fetchGroupStageMatches();
            if (fetchedMatches.isEmpty()) {
                return new SyncResult(false, "La API no devolvio partidos de fase de grupos.");
            }

            // Upsert seguro: actualizar existentes, insertar nuevos
            // NUNCA borrar partidos que tengan predicciones asociadas
            int updated = 0;
            int inserted = 0;
            for (Match fetched : fetchedMatches) {
                if (fetched.getHomeTeamExternalId() == null || fetched.getAwayTeamExternalId() == null) {
                    // Si no tiene IDs externos, solo insertar si no hay partidos de grupos aún
                    if (matchRepository.countByPhase(Phase.GRUPOS) == 0) {
                        matchRepository.save(fetched);
                        inserted++;
                    }
                    continue;
                }

                java.util.Optional<Match> existing = matchRepository
                        .findByHomeTeamExternalIdAndAwayTeamExternalId(
                                fetched.getHomeTeamExternalId(),
                                fetched.getAwayTeamExternalId());

                if (existing.isPresent()) {
                    // Actualizar datos del partido existente sin tocar predicciones
                    Match match = existing.get();
                    match.setHomeTeam(fetched.getHomeTeam());
                    match.setAwayTeam(fetched.getAwayTeam());
                    match.setHomeFlag(fetched.getHomeFlag());
                    match.setAwayFlag(fetched.getAwayFlag());
                    match.setMatchDate(fetched.getMatchDate());
                    match.setMatchGroup(fetched.getMatchGroup());
                    match.setPhase(fetched.getPhase());
                    // Solo actualizar resultado si el partido terminó
                    if (fetched.isFinished()) {
                        match.setFinished(true);
                        match.setHomeScore(fetched.getHomeScore());
                        match.setAwayScore(fetched.getAwayScore());
                    }
                    matchRepository.save(match);
                    updated++;
                } else {
                    matchRepository.save(fetched);
                    inserted++;
                }
            }

            return new SyncResult(true,
                    "Sincronizacion completada: " + inserted + " partidos nuevos, " + updated + " actualizados.");
        } catch (Exception ex) {
            return new SyncResult(false, "No se pudo sincronizar la fase de grupos: " + ex.getMessage());
        }
    }

    /**
     * Sincroniza SOLO los resultados finales de partidos ya existentes en la BD.
     * NO borra ni recrea partidos — hace upsert seguro preservando predicciones.
     * Devuelve los partidos que recién terminaron para que el caller calcule puntos.
     */
    @Transactional
    public SyncResultWithMatches syncResults() {
        if (!isEnabled()) {
            return new SyncResultWithMatches(false, "FOOTBALL_DATA_API_KEY no configurada.", List.of());
        }

        try {
            String url = "https://api.football-data.org/v4/competitions/" + competitionCode
                    + "/matches?season=" + season + "&status=FINISHED";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("X-Auth-Token", apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new SyncResultWithMatches(false,
                        "Error HTTP " + response.statusCode() + " al consultar la API.", List.of());
            }

            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response.body());
            List<Match> newlyFinished = new ArrayList<>();
            int updated = 0;

            for (com.fasterxml.jackson.databind.JsonNode node : root.path("matches")) {
                com.fasterxml.jackson.databind.JsonNode homeTeamNode = node.path("homeTeam");
                com.fasterxml.jackson.databind.JsonNode awayTeamNode = node.path("awayTeam");

                Integer homeExtId = homeTeamNode.path("id").isNumber() ? homeTeamNode.path("id").asInt() : null;
                Integer awayExtId = awayTeamNode.path("id").isNumber() ? awayTeamNode.path("id").asInt() : null;

                if (homeExtId == null || awayExtId == null) continue;

                com.fasterxml.jackson.databind.JsonNode fullTime = node.path("score").path("fullTime");
                if (!fullTime.path("home").isNumber() || !fullTime.path("away").isNumber()) continue;

                int homeScore = fullTime.path("home").asInt();
                int awayScore = fullTime.path("away").asInt();

                java.util.Optional<Match> existing =
                        matchRepository.findByHomeTeamExternalIdAndAwayTeamExternalId(homeExtId, awayExtId);

                if (existing.isEmpty()) continue; // partido no está en la BD, ignorar

                Match match = existing.get();
                boolean wasFinished = match.isFinished();

                match.setHomeScore(homeScore);
                match.setAwayScore(awayScore);
                match.setFinished(true);
                matchRepository.save(match);
                updated++;

                if (!wasFinished) {
                    newlyFinished.add(match); // solo los que recién terminaron
                }
            }

            String msg = updated == 0
                    ? "No hay resultados nuevos para actualizar."
                    : updated + " partido(s) actualizado(s). " + newlyFinished.size() + " recién finalizado(s) → puntos calculados.";

            return new SyncResultWithMatches(true, msg, newlyFinished);

        } catch (Exception ex) {
            return new SyncResultWithMatches(false, "Error al sincronizar resultados: " + ex.getMessage(), List.of());
        }
    }

    public record SyncResultWithMatches(boolean success, String message, List<Match> newlyFinishedMatches) {}


    private List<Match> fetchGroupStageMatches() throws Exception {
        Map<String, String> teamToGroupMap = fetchTeamToGroupMap();
        String url = "https://api.football-data.org/v4/competitions/" + competitionCode
                + "/matches?season=" + season + "&stage=GROUP_STAGE";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("X-Auth-Token", apiKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        List<Match> matches = new ArrayList<>();
        for (JsonNode node : root.path("matches")) {
            Match match = new Match();
            JsonNode homeTeamNode = node.path("homeTeam");
            JsonNode awayTeamNode = node.path("awayTeam");

            match.setHomeTeam(translateTeamName(homeTeamNode.path("name").asText("Por definir")));
            match.setAwayTeam(translateTeamName(awayTeamNode.path("name").asText("Por definir")));
            match.setHomeFlag(resolveFlagUrl(homeTeamNode));
            match.setAwayFlag(resolveFlagUrl(awayTeamNode));
            match.setHomeTeamExternalId(homeTeamNode.path("id").isNumber() ? homeTeamNode.path("id").asInt() : null);
            match.setAwayTeamExternalId(awayTeamNode.path("id").isNumber() ? awayTeamNode.path("id").asInt() : null);
            match.setPhase(Phase.GRUPOS);
            match.setMatchGroup(resolveGroup(node, teamToGroupMap));
            match.setMatchDate(parseUtcDate(node.path("utcDate").asText()));

            String status = node.path("status").asText("");
            if ("FINISHED".equals(status)) {
                match.setFinished(true);
                if (!node.path("score").path("fullTime").isMissingNode()) {
                    JsonNode fullTime = node.path("score").path("fullTime");
                    match.setHomeScore(fullTime.path("home").isNumber() ? fullTime.path("home").asInt() : null);
                    match.setAwayScore(fullTime.path("away").isNumber() ? fullTime.path("away").asInt() : null);
                }
            }

            matches.add(match);
        }
        return matches;
    }

    private Map<String, String> fetchTeamToGroupMap() {
        Map<String, String> teamToGroup = new HashMap<>();

        try {
            String url = "https://api.football-data.org/v4/competitions/" + competitionCode
                    + "/standings?season=" + season;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("X-Auth-Token", apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return teamToGroup;
            }

            JsonNode root = objectMapper.readTree(response.body());
            for (JsonNode standing : root.path("standings")) {
                String rawGroup = standing.path("group").asText("");
                String parsedGroup = parseGroup(rawGroup);
                if (parsedGroup == null) {
                    continue;
                }

                for (JsonNode row : standing.path("table")) {
                    String teamName = row.path("team").path("name").asText("");
                    if (!teamName.isBlank()) {
                        teamToGroup.put(teamName, parsedGroup);
                    }
                }
            }
        } catch (Exception ignored) {
            return teamToGroup;
        }

        return teamToGroup;
    }

    private LocalDateTime parseUtcDate(String utcDate) {
        if (utcDate == null || utcDate.isBlank()) {
            return LocalDateTime.now();
        }
        return OffsetDateTime.parse(utcDate)
                .atZoneSameInstant(COLOMBIA_ZONE)
                .toLocalDateTime();
    }

    private String parseGroup(String groupValue) {
        if (groupValue == null || groupValue.isBlank()) {
            return null;
        }
        return groupValue.replace("GROUP_", "");
    }

    private String resolveGroup(JsonNode node, Map<String, String> teamToGroupMap) {
        String directGroup = parseGroup(node.path("group").asText(""));
        if (directGroup != null) {
            return directGroup;
        }

        String homeTeam = node.path("homeTeam").path("name").asText("");
        String awayTeam = node.path("awayTeam").path("name").asText("");

        String homeGroup = teamToGroupMap.get(homeTeam);
        String awayGroup = teamToGroupMap.get(awayTeam);

        if (homeGroup != null && homeGroup.equals(awayGroup)) {
            return homeGroup;
        }

        if (homeGroup != null) {
            return homeGroup;
        }

        return awayGroup;
    }

    private String translateTeamName(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return "Por definir";
        }
        return TEAM_NAME_ES.getOrDefault(sourceName, sourceName);
    }

    private String resolveFlagUrl(JsonNode teamNode) {
        String tla = teamNode.path("tla").asText("");
        if (!tla.isBlank()) {
            String mapped = TEAM_FLAG_BY_TLA.get(tla);
            if (mapped != null) {
                return mapped;
            }
        }

        String crest = teamNode.path("crest").asText("");
        return crest.isBlank() ? null : crest;
    }

    private static Map<String, String> buildTeamNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put("Mexico", "Mexico");
        map.put("South Africa", "Sudafrica");
        map.put("South Korea", "Corea del Sur");
        map.put("Korea Republic", "Corea del Sur");
        map.put("United States", "Estados Unidos");
        map.put("Canada", "Canada");
        map.put("Brazil", "Brasil");
        map.put("Argentina", "Argentina");
        map.put("Germany", "Alemania");
        map.put("Spain", "Espana");
        map.put("France", "Francia");
        map.put("England", "Inglaterra");
        map.put("Colombia", "Colombia");
        map.put("Japan", "Japon");
        map.put("Australia", "Australia");
        map.put("Saudi Arabia", "Arabia Saudita");
        map.put("Poland", "Polonia");
        map.put("Croatia", "Croacia");
        map.put("Netherlands", "Paises Bajos");
        map.put("Portugal", "Portugal");
        map.put("Belgium", "Belgica");
        map.put("Switzerland", "Suiza");
        map.put("Denmark", "Dinamarca");
        map.put("Morocco", "Marruecos");
        map.put("Tunisia", "Tunez");
        map.put("Cameroon", "Camerun");
        map.put("Senegal", "Senegal");
        map.put("Ghana", "Ghana");
        map.put("Ecuador", "Ecuador");
        map.put("Uruguay", "Uruguay");
        map.put("Paraguay", "Paraguay");
        map.put("Chile", "Chile");
        map.put("Peru", "Peru");
        map.put("Costa Rica", "Costa Rica");
        map.put("Panama", "Panama");
        map.put("Honduras", "Honduras");
        map.put("Jamaica", "Jamaica");
        map.put("Iran", "Iran");
        map.put("Iraq", "Irak");
        map.put("Qatar", "Catar");
        map.put("United Arab Emirates", "Emiratos Arabes Unidos");
        map.put("New Zealand", "Nueva Zelanda");
        map.put("Nigeria", "Nigeria");
        map.put("Egypt", "Egipto");
        map.put("Algeria", "Argelia");
        map.put("Cote d'Ivoire", "Costa de Marfil");
        map.put("Ivory Coast", "Costa de Marfil");
        map.put("Serbia", "Serbia");
        map.put("Turkey", "Turquia");
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildTeamFlagMap() {
        Map<String, String> map = new HashMap<>();
        map.put("MEX", "https://flagcdn.com/w80/mx.png");
        map.put("RSA", "https://flagcdn.com/w80/za.png");
        map.put("KOR", "https://flagcdn.com/w80/kr.png");
        map.put("USA", "https://flagcdn.com/w80/us.png");
        map.put("CAN", "https://flagcdn.com/w80/ca.png");
        map.put("BRA", "https://flagcdn.com/w80/br.png");
        map.put("ARG", "https://flagcdn.com/w80/ar.png");
        map.put("GER", "https://flagcdn.com/w80/de.png");
        map.put("ESP", "https://flagcdn.com/w80/es.png");
        map.put("FRA", "https://flagcdn.com/w80/fr.png");
        map.put("ENG", "https://flagcdn.com/w80/gb-eng.png");
        map.put("COL", "https://flagcdn.com/w80/co.png");
        map.put("JPN", "https://flagcdn.com/w80/jp.png");
        map.put("AUS", "https://flagcdn.com/w80/au.png");
        map.put("KSA", "https://flagcdn.com/w80/sa.png");
        map.put("POL", "https://flagcdn.com/w80/pl.png");
        map.put("CRO", "https://flagcdn.com/w80/hr.png");
        map.put("NED", "https://flagcdn.com/w80/nl.png");
        map.put("POR", "https://flagcdn.com/w80/pt.png");
        map.put("BEL", "https://flagcdn.com/w80/be.png");
        map.put("SUI", "https://flagcdn.com/w80/ch.png");
        map.put("DEN", "https://flagcdn.com/w80/dk.png");
        map.put("MAR", "https://flagcdn.com/w80/ma.png");
        map.put("TUN", "https://flagcdn.com/w80/tn.png");
        map.put("CMR", "https://flagcdn.com/w80/cm.png");
        map.put("SEN", "https://flagcdn.com/w80/sn.png");
        map.put("GHA", "https://flagcdn.com/w80/gh.png");
        map.put("ECU", "https://flagcdn.com/w80/ec.png");
        map.put("URU", "https://flagcdn.com/w80/uy.png");
        map.put("PAR", "https://flagcdn.com/w80/py.png");
        map.put("CHI", "https://flagcdn.com/w80/cl.png");
        map.put("PER", "https://flagcdn.com/w80/pe.png");
        map.put("CRC", "https://flagcdn.com/w80/cr.png");
        map.put("PAN", "https://flagcdn.com/w80/pa.png");
        map.put("HON", "https://flagcdn.com/w80/hn.png");
        map.put("JAM", "https://flagcdn.com/w80/jm.png");
        map.put("IRN", "https://flagcdn.com/w80/ir.png");
        map.put("IRQ", "https://flagcdn.com/w80/iq.png");
        map.put("QAT", "https://flagcdn.com/w80/qa.png");
        map.put("UAE", "https://flagcdn.com/w80/ae.png");
        map.put("NZL", "https://flagcdn.com/w80/nz.png");
        map.put("NGA", "https://flagcdn.com/w80/ng.png");
        map.put("EGY", "https://flagcdn.com/w80/eg.png");
        map.put("ALG", "https://flagcdn.com/w80/dz.png");
        map.put("CIV", "https://flagcdn.com/w80/ci.png");
        map.put("SRB", "https://flagcdn.com/w80/rs.png");
        map.put("TUR", "https://flagcdn.com/w80/tr.png");
        return Collections.unmodifiableMap(map);
    }

    public record SyncResult(boolean success, String message) {
    }
}
