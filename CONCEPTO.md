# ⚽ Mundial Predictor 2026
### App de predicciones del Mundial — competencia 1 vs 1

---

## ¿Qué es esta app?

**Mundial Predictor** es una aplicación web mobile-first donde **dos personas compiten** prediciendo el marcador de cada partido del Mundial FIFA 2026. Cada usuario predice el resultado antes de que empiece el partido, y al final gana quien acumule más puntos correctos.

La app también ofrece **análisis con IA** de cada enfrentamiento y **noticias recientes** de ambas selecciones, para que los usuarios puedan hacer predicciones más informadas y no solo adivinar.

---

## ¿Para quién es?

Principalmente para **dos amigos, familiares o compañeros** que quieran vivirla el Mundial de una forma más emocionante. No es una plataforma masiva — es íntima, directa y competitiva entre dos personas que se conocen.

---

## Flujo general de la app

```
1. Admin crea/gestiona los partidos del torneo
2. Jugador 1 y Jugador 2 inician sesión
3. Antes de cada partido → cada uno predice el marcador
4. El partido se juega (en la vida real)
5. Admin registra el resultado real
6. La app calcula los puntos automáticamente
7. El marcador global se actualiza
8. Al final del torneo → gana quien tenga más puntos
```

---

## Sistema de puntuación

| Tipo de predicción | Ejemplo | Puntos |
|---|---|---|
| **Resultado exacto** | Predijo 2-1, salió 2-1 | ⭐ 3 pts |
| **Ganador/empate correcto** | Predijo 3-0, salió 1-0 (Colombia gana igual) | ✅ 1 pt |
| **Incorrecto** | Predijo empate, ganó alguien | ❌ 0 pts |

### Bonus opcionales (se pueden activar o no)
| Bonus | Momento | Puntos |
|---|---|---|
| Campeón del torneo | Antes del primer partido | +10 pts si atinas |
| Goleador del torneo | Antes del primer partido | +5 pts si atinas |
| Todos los clasificados de un grupo | Antes que empiece la fase | +3 pts por grupo |

### Reglas de bloqueo
- Las predicciones **se bloquean 30 minutos antes** del inicio de cada partido
- Una vez que el partido termina, los puntos se calculan automáticamente
- Si el partido termina con la predicción bloqueada y no hiciste la tuya → 0 puntos

---

## Pantallas principales

### 🏠 Dashboard
- Marcador actual (Jugador 1 vs Jugador 2) con puntos totales
- Lista de próximos partidos con countdown
- Indicador de quién va ganando

### ⚽ Lista de partidos
- Todos los partidos organizados por fase y fecha
- Estado de cada partido: Próximo / En predicción / Bloqueado / Terminado
- Indicador de si ya hiciste tu predicción o no
- Diferencia de puntos por partido (quién ganó ese partido específico)

### 📋 Detalle de partido
- Info del partido: selecciones, fecha, hora, fase, grupo
- Formulario para predecir (marcador home / away)
- Después de que termina: resultado real + las dos predicciones + puntos ganados por cada uno
- **Sección de análisis IA**: resumen del estado actual de ambas selecciones (forma reciente, lesionados, historial entre ellos)
- **Noticias recientes**: últimos 3-4 titulares de cada selección

### ⚙️ Panel Admin
- Crear/editar partidos (selecciones, fecha, fase, grupo)
- Registrar resultado final de cada partido
- Al registrar el resultado → se calculan los puntos automáticamente

---

## Funcionalidad de IA y Noticias

### Análisis IA por partido
Cuando el usuario abre el detalle de un partido, puede solicitar un análisis generado por IA. Este análisis:

- Se genera consultando la **API de Claude (Anthropic)**
- Recibe como contexto: nombre de ambas selecciones, fase del torneo, estadísticas recientes (si están disponibles via API de fútbol)
- Genera un texto tipo periodístico con:
  - Forma reciente de cada selección (últimos 5 partidos)
  - Jugadores clave o ausencias importantes
  - Historial de enfrentamientos (H2H)
  - Una proyección o análisis del posible resultado

> **Ejemplo de output:** *"Colombia llega con 4 victorias consecutivas en eliminatoria. Brasil sin Vinicius desde la jornada 3 por lesión. El H2H favorece históricamente a Brasil (7-3), pero esta versión de Colombia presenta un bloque defensivo sólido. El partido podría definirse por pelota parada..."*

### Noticias recientes
- Se consumen desde **NewsAPI** (plan gratuito)
- Se busca por nombre de cada selección (ej. `"Colombia selección"`, `"Brazil national team"`)
- Se muestran máximo 4 titulares por selección con enlace a la nota original
- Se actualizan cada vez que se abre el detalle del partido

---

## Arquitectura técnica

### Stack
| Capa | Tecnología |
|---|---|
| Backend | Spring Boot 3.3 (Java 21) |
| Frontend | Thymeleaf + Bootstrap 5 |
| Base de datos (dev) | H2 (en memoria) |
| Base de datos (prod) | PostgreSQL |
| Autenticación | Spring Security (sesiones con login propio) |
| IA | API de Claude (Anthropic) via HTTP |
| Noticias | NewsAPI |
| Datos de fútbol | football-data.org (opcional) |
| Deploy | Railway o Render |

### Estructura de paquetes
```
com.mundial.predictor
├── config/
│   ├── DataInitializer.java     ← carga usuarios y partidos iniciales
│   ├── PasswordConfig.java      ← BCryptPasswordEncoder
│   └── SecurityConfig.java      ← rutas protegidas, login, logout
├── controller/
│   ├── DashboardController.java
│   ├── MatchController.java
│   └── AdminController.java
├── model/
│   ├── User.java                ← entidad usuario
│   ├── Match.java               ← entidad partido
│   ├── Prediction.java          ← entidad predicción
│   ├── Role.java                ← enum: PLAYER, ADMIN
│   └── Phase.java               ← enum: GRUPOS, OCTAVOS, ...
├── repository/
│   ├── UserRepository.java
│   ├── MatchRepository.java
│   └── PredictionRepository.java
└── service/
    ├── UserService.java         ← UserDetailsService para Spring Security
    ├── MatchService.java
    └── PredictionService.java   ← lógica de puntuación
```

### Modelo de datos (relaciones)
```
User (1) ────────── (N) Prediction (N) ────────── (1) Match
 - id                    - id                          - id
 - username              - predictedHomeScore           - homeTeam
 - password              - predictedAwayScore           - awayTeam
 - role                  - pointsEarned                 - matchDate
 - displayName           - user (FK)                    - homeScore (real)
 - totalPoints           - match (FK)                   - awayScore (real)
                                                        - finished
                                                        - phase
```

---

## Roles de usuario

| Rol | Acceso |
|---|---|
| `PLAYER` | Dashboard, lista de partidos, predecir, ver resultados y análisis |
| `ADMIN` | Todo lo anterior + crear partidos, registrar resultados finales |

El admin **no compite** — es quien arbitra y carga los datos. Puede ser una tercera persona o uno de los jugadores dependiendo del nivel de confianza.

---

## Puntaje de bloqueo — regla detallada

```
Ahora mismo es: 2026-06-15 20:35
Partido: Colombia vs Alemania — 2026-06-15 21:00

¿Se puede predecir? NO → bloqueado (faltan menos de 30 min)
```

El método `isLocked()` en la entidad `Match` evalúa:
```java
public boolean isLocked() {
    return finished || LocalDateTime.now().isAfter(matchDate.minusMinutes(30));
}
```

---

## Roadmap de desarrollo

### Fase 1 — MVP (actual)
- [x] Modelo de datos (User, Match, Prediction)
- [x] Autenticación con Spring Security
- [x] CRUD de partidos (admin)
- [x] Sistema de predicciones con bloqueo automático
- [x] Cálculo de puntos al registrar resultado
- [x] Dashboard con marcador
- [ ] Templates Thymeleaf completos
- [ ] CSS mobile-first

### Fase 2 — IA y Noticias
- [ ] Integración con Claude API (análisis por partido)
- [ ] Integración con NewsAPI (noticias por selección)
- [ ] Integración con football-data.org (estadísticas reales)
- [ ] Caché de análisis para no llamar la API en cada visita

### Fase 3 — Mejoras UX
- [ ] Countdown en tiempo real hasta el inicio del partido
- [ ] Notificaciones (recordatorio para predecir antes del bloqueo)
- [ ] Historial detallado de puntos partido por partido
- [ ] Estadísticas: % de exactos, % de ganadores correctos, racha

### Fase 4 — Deploy
- [ ] Migración a PostgreSQL
- [ ] Variables de entorno para credenciales y API keys
- [ ] Deploy en Railway
- [ ] Dominio personalizado (opcional)

---

## Variables de entorno en producción

```properties
# Base de datos
DATABASE_URL=jdbc:postgresql://...
DATABASE_USERNAME=...
DATABASE_PASSWORD=...

# Usuarios (cambiar las contraseñas por defecto)
APP_ADMIN_USERNAME=admin
APP_ADMIN_PASSWORD=********
APP_PLAYER1_USERNAME=jugador1
APP_PLAYER1_PASSWORD=********
APP_PLAYER2_USERNAME=jugador2
APP_PLAYER2_PASSWORD=********

# APIs externas (Fase 2)
ANTHROPIC_API_KEY=sk-ant-...
NEWS_API_KEY=...
FOOTBALL_DATA_API_KEY=...
```

---

## Credenciales por defecto (desarrollo)

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `admin123` | Administrador |
| `jugador1` | `pass123` | Jugador |
| `jugador2` | `pass123` | Jugador |

> ⚠️ **Cambiar estas credenciales antes de cualquier deploy a producción.**

---

## Dependencias clave añadidas vs el pom.xml original

| Dependencia | Razón |
|---|---|
| `spring-boot-starter-security` | Login, roles, sesiones |
| `thymeleaf-extras-springsecurity6` | `sec:authorize` en templates |
| `lombok` | Elimina boilerplate (getters, setters, constructores) |
| `spring-boot-starter-web` | Reemplaza `webmvc` que no existe como nombre |

> Las dependencias de test que tenías (`data-jpa-test`, `thymeleaf-test`, etc.) **no existen** como artifacts en Maven. El correcto es solo `spring-boot-starter-test`.

---

*Proyecto desarrollado con Spring Boot 3.3 · Java 21 · Thymeleaf · Bootstrap 5*