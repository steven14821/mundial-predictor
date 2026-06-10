package com.mundial.predictor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FootballDataService {

    private final String apiKey;

    public FootballDataService(@Value("${football-data.api.key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public String buildFormContext(String teamName) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }

        // Punto de extension para integrar football-data.org sin romper la app
        return "No se encontraron estadisticas estructuradas adicionales para " + teamName + ".";
    }
}
