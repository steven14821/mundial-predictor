package com.mundial.predictor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.net.URI;

@SpringBootApplication
public class MundialPredictorApplication {
    public static void main(String[] args) {
        // Railway provee DATABASE_URL como: postgresql://user:pass@host:port/db
        // Spring Boot necesita: jdbc:postgresql://host:port/db + user + pass por separado
        String rawUrl = System.getenv("DATABASE_URL");
        System.out.println("[DB] Checking for DATABASE_URL environment variable...");
        if (rawUrl != null && !rawUrl.isBlank()) {
            System.out.println("[DB] DATABASE_URL found. Parsing...");
            if (rawUrl.startsWith("jdbc:")) {
                System.setProperty("spring.datasource.url", rawUrl);
                System.out.println("[DB] DATABASE_URL is already in JDBC format.");
            } else {
                try {
                    URI uri = new URI(rawUrl.replace("postgresql://", "http://")
                                            .replace("postgres://", "http://"));
                    String userInfo = uri.getUserInfo();
                    if (userInfo != null && userInfo.contains(":")) {
                        String user = userInfo.split(":", 2)[0];
                        String pass = userInfo.split(":", 2)[1];
                        
                        String host = uri.getHost();
                        int port = uri.getPort();
                        String path = uri.getPath();
                        
                        String jdbcUrl = "jdbc:postgresql://" + host + ":" + (port == -1 ? "5432" : port) + path;
                        System.setProperty("spring.datasource.url", jdbcUrl);
                        System.setProperty("spring.datasource.username", user);
                        System.setProperty("spring.datasource.password", pass);
                        
                        System.out.println("[DB] Configured JDBC URL: " + jdbcUrl);
                        System.out.println("[DB] Configured Username: " + user);
                    } else {
                        System.err.println("[DB] ERROR: DATABASE_URL userInfo is invalid or missing credentials.");
                    }
                } catch (Exception e) {
                    System.err.println("[DB] ERROR: Error parseando DATABASE_URL: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } else {
            System.err.println("[DB] WARNING: DATABASE_URL environment variable is NOT set.");
            String activeProfile = System.getProperty("spring.profiles.active");
            if (activeProfile == null) {
                activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
            }
            System.out.println("[DB] Active profile: " + activeProfile);
            if ("prod".equals(activeProfile)) {
                System.err.println("[DB] ERROR: Running in 'prod' profile but no DATABASE_URL was found. The application will fail to connect or fallback incorrectly.");
            }
        }
        SpringApplication.run(MundialPredictorApplication.class, args);
    }
}