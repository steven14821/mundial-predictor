package com.mundial.predictor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mundial.predictor.model.Match;
import com.mundial.predictor.service.dto.MatchContextData;
import com.mundial.predictor.service.dto.MatchInsights;
import com.mundial.predictor.service.dto.RecentMatchSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchInsightsService {

    private final String geminiApiKey;
    private final String geminiModel;
    private final ObjectMapper objectMapper;
    private final FootballDataService footballDataService;
    private final HttpClient httpClient;
    private final Map<String, MatchInsights> cache = new ConcurrentHashMap<>();

    public MatchInsightsService(
            @Value("${gemini.api.key:}") String geminiApiKey,
            @Value("${gemini.model:gemini-2.0-flash}") String geminiModel,
            ObjectMapper objectMapper,
            FootballDataService footballDataService
    ) {
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
        this.objectMapper = objectMapper;
        this.footballDataService = footballDataService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public MatchInsights getInsights(Match match) {
        return getInsights(match, null);
    }

    public MatchInsights getInsights(Match match, MatchContextData matchContext) {
        String cacheKey = match.getHomeTeam() + "|" + match.getAwayTeam() + "|" + match.getPhase();
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        String homeContext = footballDataService.buildFormContext(match.getHomeTeam());
        String awayContext = footballDataService.buildFormContext(match.getAwayTeam());
        String analysis = buildFallbackAnalysis(match, homeContext, awayContext, matchContext);
        boolean fromAi = false;

        String aiAnalysis = fetchGeminiAnalysis(match, homeContext, awayContext, matchContext);
        if (aiAnalysis != null && !aiAnalysis.isBlank()) {
            analysis = aiAnalysis;
            fromAi = true;
        }

        MatchInsights insights = new MatchInsights(analysis, fromAi);
        cache.put(cacheKey, insights);
        return insights;
    }

    private String fetchGeminiAnalysis(
            Match match,
            String homeContext,
            String awayContext,
            MatchContextData matchContext
    ) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return null;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of(
                                    "text", buildPrompt(match, homeContext, awayContext, matchContext)
                            ))
                    )),
                    "tools", List.of(Map.of(
                            "google_search", Map.of()
                    ))
            ));

            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // Error de API (503 alta demanda, 429 rate limit, etc.) — silencioso, usar análisis local
                System.err.println("[Gemini] HTTP " + response.statusCode() + " — usando análisis local de respaldo.");
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("error")) {
                // Error en respuesta JSON — silencioso, usar análisis local
                System.err.println("[Gemini] Error en respuesta: " + root.path("error").path("message").asText("Desconocido") + " — usando análisis local.");
                return null;
            }
            
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText(null);
                }
            }
        } catch (Exception e) {
            return "Excepcion al conectar con Gemini: " + e.getMessage();
        }

        return "No se pudo extraer el analisis de la respuesta de Gemini.";
    }

    private String buildPrompt(
            Match match,
            String homeContext,
            String awayContext,
            MatchContextData matchContext
    ) {
        String today = java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota"))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new java.util.Locale("es", "ES")));

        return """
                [INSTRUCCIÓN CRÍTICA DE NO PREDICCIÓN]
                Está ESTRICTAMENTE PROHIBIDO sugerir o predecir marcadores exactos, pronosticar qué equipo va a ganar o empatar, o dar probabilidades porcentuales de victoria.
                Tu tarea es realizar ÚNICAMENTE un análisis táctico y listar el historial de forma neutral, objetiva y profesional.
                
                Contexto temporal: la fecha actual en la que se realiza esta consulta es %s. 
                Usa Google Search para buscar y listar los últimos partidos reales jugados por cada selección nacional en el mundo real hasta el día de hoy (%s).
                Busca y lista de forma muy organizada los últimos 5 partidos jugados (ya sean amistosos o competencia oficial) por cada una de las dos selecciones.
                Usa el contexto y la web de forma prudente. No inventes datos.

                Partido del torneo: %s vs %s
                Fase: %s

                Contexto adicional equipo local:
                %s

                Contexto adicional equipo visitante:
                %s

                Estructura obligatoria para tu respuesta (respeta estrictamente los encabezados):

                ### Últimos Partidos %s
                (Lista organizada con viñetas de los últimos 5 partidos reales de esta selección en el mundo real: fecha, rival, torneo -amistoso u oficial- y marcador final)

                ### Últimos Partidos %s
                (Lista organizada con viñetas de los últimos 5 partidos reales de esta selección en el mundo real: fecha, rival, torneo -amistoso u oficial- y marcador final)

                ### Análisis Táctico
                - Escribe 1 o 2 párrafos cortos resumiendo el estado táctico actual de ambos equipos de cara al encuentro.
                - RECUERDA: NO des predicciones de marcadores, no sugieras quién ganará ni des pronósticos del resultado final. Limítate a analizar objetivamente cómo llegan los equipos y sus fortalezas/debilidades.
                """.formatted(
                today,
                today,
                match.getHomeTeam(),
                match.getAwayTeam(),
                match.getPhase() != null ? match.getPhase().getDisplayName() : "Sin fase",
                homeContext.isBlank() ? "Sin datos adicionales." : homeContext,
                awayContext.isBlank() ? "Sin datos adicionales." : awayContext,
                match.getHomeTeam(),
                match.getAwayTeam()
        );
    }

    private String buildFallbackAnalysis(
            Match match,
            String homeContext,
            String awayContext,
            MatchContextData matchContext
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Resumen\n\n")
                .append(match.getHomeTeam())
                .append(" vs ")
                .append(match.getAwayTeam())
                .append(" promete un cruce interesante en ")
                .append(match.getPhase() != null ? match.getPhase().getDisplayName().toLowerCase() : "el torneo")
                .append(". ");

        if (!homeContext.isBlank() || !awayContext.isBlank()) {
            builder.append("Hay contexto adicional para orientar la lectura del partido. ");
        }

        builder.append("\n\nForma Reciente\n\n")
                .append(match.getHomeTeam()).append(":\n")
                .append(recentMatchesToPrompt(matchContext != null ? matchContext.homeRecentMatches() : List.of()))
                .append("\n")
                .append(match.getAwayTeam()).append(":\n")
                .append(recentMatchesToPrompt(matchContext != null ? matchContext.awayRecentMatches() : List.of()))
                .append("\nEstadisticas Cara a Cara\n\n")
                .append("No hay datos de enfrentamientos directos cargados en el contexto actual.\n\n")
                .append("Atributos Clave de los Equipos\n\n")
                .append(match.getHomeTeam()).append(":\n")
                .append("- Revisar forma reciente antes de predecir.\n")
                .append("- Medir solidez defensiva por goles recibidos.\n")
                .append("- Valorar eficacia en pelota quieta y transiciones.\n\n")
                .append(match.getAwayTeam()).append(":\n")
                .append("- Revisar forma reciente antes de predecir.\n")
                .append("- Medir solidez defensiva por goles recibidos.\n")
                .append("- Valorar eficacia en pelota quieta y transiciones.\n\n")
                .append("Panorama Tactico\n\n")
                .append("Sin una API de analisis activa, la recomendacion es comparar los ultimos resultados, la diferencia de gol reciente y el volumen ofensivo antes de guardar la prediccion.");
        return builder.toString();
    }

    private String recentMatchesToPrompt(List<RecentMatchSummary> matches) {
        if (matches == null || matches.isEmpty()) {
            return "Sin resultados recientes disponibles.\n";
        }

        StringBuilder builder = new StringBuilder();
        for (RecentMatchSummary match : matches) {
            builder.append("- ")
                    .append(match.venue())
                    .append(" vs ")
                    .append(match.opponent())
                    .append(": ")
                    .append(match.result())
                    .append(match.utcDateLabel() == null || match.utcDateLabel().isBlank() ? "" : " (" + match.utcDateLabel() + ")")
                    .append(match.competition() == null || match.competition().isBlank() ? "" : " - " + match.competition())
                    .append("\n");
        }
        return builder.toString();
    }
}
