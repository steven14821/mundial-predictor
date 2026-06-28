package com.mundial.predictor;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.Prediction;
import com.mundial.predictor.model.User;
import com.mundial.predictor.model.Role;
import com.mundial.predictor.model.Phase;
import com.mundial.predictor.repository.MatchRepository;
import com.mundial.predictor.repository.PredictionRepository;
import com.mundial.predictor.repository.UserRepository;
import com.mundial.predictor.service.PredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithUserDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class MundialPredictorApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@WithUserDetails("Steven")
	void testMatchesPage() throws Exception {
		mockMvc.perform(get("/matches"))
				.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails("Steven")
	void testMatchesPageShowsPredictionBadge() throws Exception {
		User user = userRepository.findByUsername("Steven").orElseThrow();
		Match match = createMatch("Colombia", "Brasil", 0, 0);
		match.setFinished(false);
		match.setMatchDate(LocalDateTime.now().plusDays(3));
		match.setHomeTeamExternalId(1001);
		match.setAwayTeamExternalId(1002);
		match = matchRepository.save(match);

		Prediction prediction = new Prediction();
		prediction.setUser(user);
		prediction.setMatch(match);
		prediction.setPredictedHomeScore(2);
		prediction.setPredictedAwayScore(1);
		predictionRepository.save(prediction);

		String html = mockMvc.perform(get("/matches"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertTrue(html.contains("Ya predijiste"), "Debe mostrar badge cuando el usuario ya predijo");
		assertTrue(html.contains("2 - 1"), "Debe mostrar el marcador predicho");
	}

	@Test
	@WithUserDetails("Steven")
	void testMatchesPageFindsPredictionOnDuplicateFixture() throws Exception {
		User user = userRepository.findByUsername("Steven").orElseThrow();

		Match oldRow = createMatch("Colombia", "Brasil", 0, 0);
		oldRow.setFinished(false);
		oldRow.setMatchDate(LocalDateTime.now().plusDays(3));
		oldRow.setHomeTeamExternalId(2001);
		oldRow.setAwayTeamExternalId(2002);
		oldRow = matchRepository.save(oldRow);

		Match listedRow = createMatch("Colombia", "Brasil", 0, 0);
		listedRow.setFinished(false);
		listedRow.setMatchDate(LocalDateTime.now().plusDays(3));
		listedRow.setHomeTeamExternalId(2001);
		listedRow.setAwayTeamExternalId(2002);
		listedRow = matchRepository.save(listedRow);

		Prediction prediction = new Prediction();
		prediction.setUser(user);
		prediction.setMatch(oldRow);
		prediction.setPredictedHomeScore(3);
		prediction.setPredictedAwayScore(2);
		predictionRepository.save(prediction);

		String html = mockMvc.perform(get("/matches"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertTrue(html.contains("Ya predijiste"), "Debe reconocer prediccion aunque este en otro id de partido");
		assertTrue(html.contains("3 - 2"), "Debe mostrar marcador de la prediccion existente");
	}

	@Test
	@WithUserDetails("Steven")
	void testPlayoffsPage() throws Exception {
		mockMvc.perform(get("/playoffs"))
				.andExpect(status().isOk());
	}


	@Test
	@WithUserDetails("admin")
	void testEditMatch() throws Exception {
		// Crear match de prueba
		Match match = createMatch("LocalTest", "VisitaTest", 0, 0);
		match.setFinished(false);
		match = matchRepository.save(match);

		// 1. Probar GET edit form
		mockMvc.perform(get("/admin/matches/" + match.getId() + "/edit"))
				.andExpect(status().isOk());

		// 2. Probar POST edit
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/edit")
				.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
				.param("homeTeam", "Colombia")
				.param("awayTeam", "Alemania")
				.param("phase", "RONDA32")
				.param("matchDate", "2026-06-29T13:00"))
				.andExpect(status().is3xxRedirection())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/admin/matches"));

		// Verificar que se actualizó en la BD
		Match updated = matchRepository.findById(match.getId()).orElseThrow();
		assertEquals("Colombia", updated.getHomeTeam());
	}

	@Test
	@WithUserDetails("admin")
	void testInitSchedule() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/matches/init-schedule")
				.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/admin/matches"));
	}




	@Autowired
	private PredictionService predictionService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private PredictionRepository predictionRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void testScoringLogic() {
		// Crear usuario de prueba
		User user = new User("tester", "testPass", Role.PLAYER, "Tester");
		user = userRepository.save(user);

		// 1. Caso Empate Exacto (ej. real 1-1, predijo 1-1) -> 3 puntos
		Match match1 = createMatch("Local1", "Visita1", 1, 1);
		Prediction pred1 = createPrediction(user, match1, 1, 1);
		predictionService.calculatePoints(match1);
		Prediction savedPred1 = predictionRepository.findById(pred1.getId()).orElseThrow();
		assertEquals(3, savedPred1.getPointsEarned(), "Empate exacto debe dar 3 puntos");

		// 2. Caso Empate Incorrecto (ej. real 1-1, predijo 2-2) -> 1 punto (NO 2 puntos)
		Match match2 = createMatch("Local2", "Visita2", 1, 1);
		Prediction pred2 = createPrediction(user, match2, 2, 2);
		predictionService.calculatePoints(match2);
		Prediction savedPred2 = predictionRepository.findById(pred2.getId()).orElseThrow();
		assertEquals(1, savedPred2.getPointsEarned(), "Empate no exacto debe dar 1 punto (no aplica dif. gol)");

		// 3. Caso Ganador Exacto (ej. real 2-1, predijo 2-1) -> 3 puntos
		Match match3 = createMatch("Local3", "Visita3", 2, 1);
		Prediction pred3 = createPrediction(user, match3, 2, 1);
		predictionService.calculatePoints(match3);
		Prediction savedPred3 = predictionRepository.findById(pred3.getId()).orElseThrow();
		assertEquals(3, savedPred3.getPointsEarned(), "Ganador exacto debe dar 3 puntos");

		// 4. Caso Ganador + Dif. Gol (ej. real 2-1, predijo 3-2) -> 2 puntos
		Match match4 = createMatch("Local4", "Visita4", 2, 1);
		Prediction pred4 = createPrediction(user, match4, 3, 2);
		predictionService.calculatePoints(match4);
		Prediction savedPred4 = predictionRepository.findById(pred4.getId()).orElseThrow();
		assertEquals(2, savedPred4.getPointsEarned(), "Ganador + dif gol exacto debe dar 2 puntos");

		// 5. Caso Ganador Simple (ej. real 2-1, predijo 3-0) -> 1 punto
		Match match5 = createMatch("Local5", "Visita5", 2, 1);
		Prediction pred5 = createPrediction(user, match5, 3, 0);
		predictionService.calculatePoints(match5);
		Prediction savedPred5 = predictionRepository.findById(pred5.getId()).orElseThrow();
		assertEquals(1, savedPred5.getPointsEarned(), "Ganador correcto sin dif gol debe dar 1 punto");

		// 6. Caso Incorrecto (ej. real 2-1, predijo 1-2) -> 0 puntos
		Match match6 = createMatch("Local6", "Visita6", 2, 1);
		Prediction pred6 = createPrediction(user, match6, 1, 2);
		predictionService.calculatePoints(match6);
		Prediction savedPred6 = predictionRepository.findById(pred6.getId()).orElseThrow();
		assertEquals(0, savedPred6.getPointsEarned(), "Ganador incorrecto debe dar 0 puntos");
	}

	private Match createMatch(String home, String away, int homeScore, int awayScore) {
		Match match = new Match();
		match.setHomeTeam(home);
		match.setAwayTeam(away);
		match.setMatchDate(LocalDateTime.now().plusDays(1));
		match.setPhase(Phase.GRUPOS);
		match.setFinished(true);
		match.setHomeScore(homeScore);
		match.setAwayScore(awayScore);
		return matchRepository.save(match);
	}

	private Prediction createPrediction(User user, Match match, int homeScore, int awayScore) {
		Prediction prediction = new Prediction();
		prediction.setUser(user);
		prediction.setMatch(match);
		prediction.setPredictedHomeScore(homeScore);
		prediction.setPredictedAwayScore(awayScore);
		return predictionRepository.save(prediction);
	}
}
