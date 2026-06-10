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
        if (rawUrl != null && !rawUrl.isBlank()) {
            try {
                URI uri = new URI(rawUrl.replace("postgresql://", "http://")
                                        .replace("postgres://", "http://"));
                String userInfo = uri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String user = userInfo.split(":", 2)[0];
                    String pass = userInfo.split(":", 2)[1];
                    String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort() + uri.getPath();
                    System.setProperty("spring.datasource.url", jdbcUrl);
                    System.setProperty("spring.datasource.username", user);
                    System.setProperty("spring.datasource.password", pass);
                }
            } catch (Exception e) {
                System.err.println("[DB] Error parseando DATABASE_URL: " + e.getMessage());
            }
        }
        SpringApplication.run(MundialPredictorApplication.class, args);
    }
}