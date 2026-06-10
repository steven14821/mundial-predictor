package com.mundial.predictor.model;

public enum Phase {
    GRUPOS("Fase de Grupos"),
    RONDA32("Ronda de 32"),
    OCTAVOS("Octavos de Final"),
    CUARTOS("Cuartos de Final"),
    SEMIFINAL("Semifinal"),
    TERCER_PUESTO("Tercer Puesto"),
    FINAL("Gran Final");

    private final String displayName;

    Phase(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
