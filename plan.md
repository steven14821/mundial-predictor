### 📋 Plan de cambios

#### 1. `Match.java` — Agregar campos de penales
- Añadir `private Integer homePenaltyScore;`
- Añadir `private Integer awayPenaltyScore;`
- Getters/setters para ambos
- Método `hasPenalties()` → `homePenaltyScore != null && awayPenaltyScore != null`
- Método `getDisplayScore()` → retorna string formateado, ej: `"1 - 1 (4 - 2)"`

#### 2. `WorldCupSyncService.java` — Sincronizar penales desde API
- En `applyApiNodeToMatch()`: después de leer `fullTime`, leer `score.penalties.home` y `score.penalties.away`
- En `syncResults()`: igual, leer penalities del nodo de la API
- La API de football-data.org devuelve `score.penalties` solo cuando hubo penales

#### 3. `MatchService.java` y `AdminController.java` — Permitir al admin cargar penales
- Modificar `setScore()` para aceptar `homePenaltyScore` y `awayPenaltyScore` opcionales
- Modificar endpoint `/admin/matches/{id}/score` para recibir parámetros extra

#### 4. `admin/score-form.html` — Agregar campos opcionales de penales
- Dos inputs numéricos para penales locales y visitantes (opcionales)

#### 5. Templates frontend — Mostrar resultado con penales
- **`matches.html`**: Donde se muestra el score del partido terminado, si `match.hasPenalties()` mostrar `"1 - 1 (4 - 2)"`
- **`match-detail.html`**: Ídem
- **`playoff-card.html`**: Ídem

#### 6. `PredictionService.java` — Sin cambios
- `computePoints()` debe seguir usando `homeScore`/`awayScore` (resultado reglamentario)

#### 7. Base de datos
- Como usan `spring.jpa.hibernate.ddl-auto=update`, JPA agregará automáticamente las columnas `home_penalty_score` y `away_penalty_score`

---

### 📐 Ejemplo de comportamiento

| Partido | homeScore | awayScore | homePenalty | awayPenalty | Muestra |
|---------|-----------|-----------|-------------|-------------|---------|
| Alemania vs Argentina | 1 | 1 | 4 | 2 | `1 - 1 (4 - 2)` |
| Brasil vs Uruguay | 2 | 0 | null | null | `2 - 0` |

---