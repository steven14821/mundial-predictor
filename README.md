# ⚽ Mundial Predictor 2026

Aplicación web fullstack para predicciones de partidos del **Mundial FIFA 2026**. Dos jugadores compiten prediciendo marcadores de cada partido; quien acumule más puntos al final del torneo, gana.

## ¿Qué hace?

- **Predicciones 1 vs 1** — cada jugador ingresa su marcador antes de que el partido comience.
- **Bloqueo automático** — las predicciones se cierran 30 minutos antes del inicio del partido.
- **Puntuación automática** — resultado exacto = 3 pts, ganador/empate correcto = 1 pt, fallo = 0 pts.
- **Dashboard en tiempo real** — marcador global con la competencia entre ambos jugadores.
- **Análisis con IA** — cada partido incluye un análisis generado por Gemini (forma reciente, H2H, jugadores clave).
- **Sincronización FIFA** — los partidos se importan automáticamente desde football-data.org.
- **Panel de administración** — crear partidos, registrar resultados finales.

## Tecnologías

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.3 |
| Web | Spring MVC |
| Seguridad | Spring Security 6 (sesiones, roles, BCrypt) |
| Persistencia | Spring Data JPA / Hibernate |
| Frontend | Thymeleaf + Bootstrap 5 |
| BD desarrollo | H2 (en memoria) |
| BD producción | PostgreSQL |
| IA | Gemini API (Google) |
| Datos FIFA | football-data.org API |
| Build | Maven |
| Contenedor | Docker (multi-stage, Eclipse Temurin 21) |
| Utilidades | Lombok, Spring DevTools, Spring Validation |

### Credenciales por defecto (desarrollo)

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `admin123` | Administrador |
| `Jairo` | `jairo123` | Jugador 1 |
| `Steven` | `steven123` | Jugador 2 |


## Variables de entorno (producción)

| Variable | Descripción |
|---|---|
| `DATABASE_URL` | URL de conexión PostgreSQL |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | Credenciales del administrador |
| `PLAYER1_USERNAME` / `PLAYER1_PASSWORD` | Credenciales del jugador 1 |
| `PLAYER2_USERNAME` / `PLAYER2_PASSWORD` | Credenciales del jugador 2 |
| `GEMINI_API_KEY` | API key de Google Gemini |
| `FOOTBALL_DATA_API_KEY` | API key de football-data.org |

## Estructura del proyecto

```
com.mundial.predictor/
├── config/          → Seguridad, inicialización de datos, scheduler
├── controller/      → DashboardController, MatchController, AdminController
├── model/           → Entidades JPA (User, Match, Prediction, Role, Phase)
├── repository/      → Interfaces Spring Data JPA
└── service/         → Lógica de negocio, integración IA, sincronización FIFA
```

## Licencia

Proyecto personal con fines educativos y de portafolio.
