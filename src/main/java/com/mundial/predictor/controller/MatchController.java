package com.mundial.predictor.controller;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.Prediction;
import com.mundial.predictor.model.User;
import com.mundial.predictor.service.MatchService;
import com.mundial.predictor.service.MatchInsightsService;
import com.mundial.predictor.service.MatchContextService;
import com.mundial.predictor.service.PredictionService;
import com.mundial.predictor.service.UserService;
import com.mundial.predictor.service.dto.MatchContextData;
import com.mundial.predictor.service.dto.MatchInsights;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/matches")
public class MatchController {

    private final MatchService matchService;
    private final UserService userService;
    private final PredictionService predictionService;
    private final MatchInsightsService matchInsightsService;
    private final MatchContextService matchContextService;

    public MatchController(MatchService matchService, UserService userService, PredictionService predictionService, MatchInsightsService matchInsightsService, MatchContextService matchContextService) {
        this.matchService = matchService;
        this.userService = userService;
        this.predictionService = predictionService;
        this.matchInsightsService = matchInsightsService;
        this.matchContextService = matchContextService;
    }

    @GetMapping
    public String listMatches(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User currentUser = userService.findByUsername(userDetails.getUsername());
        List<Match> matches = matchService.findAll();
        List<User> duelPlayers = userService.findAllPlayers();
        Map<Long, Prediction> predictions = predictionService.getPredictionsMapByMatch(currentUser, matches);
        Map<Long, Map<Long, Prediction>> duelPredictions = predictionService.getPredictionsByMatchForUsers(duelPlayers, matches);

        java.time.LocalDate todayDate = java.time.LocalDate.now(java.time.ZoneId.of("America/Bogota"));
        Map<java.time.LocalDate, List<Match>> matchesByDate = matches.stream()
                .filter(match -> match.getMatchDate() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        match -> match.getMatchDate().toLocalDate(),
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        boolean hasTodayGroup = matchesByDate.containsKey(todayDate);
        java.time.LocalDate firstDate = matchesByDate.isEmpty() ? null : matchesByDate.keySet().iterator().next();

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("matches", matches);
        model.addAttribute("matchesByDate", matchesByDate);
        model.addAttribute("todayDate", todayDate);
        model.addAttribute("hasTodayGroup", hasTodayGroup);
        model.addAttribute("firstDate", firstDate);
        model.addAttribute("predictions", predictions);
        model.addAttribute("duelPlayers", duelPlayers);
        model.addAttribute("duelPredictions", duelPredictions);
        return "matches";
    }

    @GetMapping("/{id}")
    public String matchDetail(@PathVariable Long id,
                              @AuthenticationPrincipal UserDetails userDetails,
                              Model model) {
        User currentUser = userService.findByUsername(userDetails.getUsername());
        Match match = matchService.findById(id)
                .orElseThrow(() -> new RuntimeException("Partido no encontrado"));
        List<User> duelPlayers = userService.findAllPlayers();

        Optional<Prediction> myPrediction = predictionService.findByUserAndMatch(currentUser, match);
        List<Prediction> allPredictions = match.isFinished()
                ? predictionService.findByMatch(match)
                : List.of();
        Map<Long, Prediction> predictionsByUser = predictionService.getPredictionsMapForMatch(match);
        MatchContextData matchContext = matchContextService.getContextForMatch(match);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("match", match);
        model.addAttribute("myPrediction", myPrediction.orElse(null));
        model.addAttribute("allPredictions", allPredictions);
        model.addAttribute("duelPlayers", duelPlayers);
        model.addAttribute("predictionsByUser", predictionsByUser);
        model.addAttribute("matchContext", matchContext);
        return "match-detail";
    }

    @GetMapping("/{id}/insights")
    @ResponseBody
    public MatchInsights getInsightsJson(@PathVariable Long id) {
        Match match = matchService.findById(id)
                .orElseThrow(() -> new RuntimeException("Partido no encontrado"));
        MatchContextData matchContext = matchContextService.getContextForMatch(match);
        return matchInsightsService.getInsights(match, matchContext);
    }

    @PostMapping("/{id}/predict")
    public String savePrediction(@PathVariable Long id,
                                 @RequestParam int homeScore,
                                 @RequestParam int awayScore,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes ra) {
        User currentUser = userService.findByUsername(userDetails.getUsername());
        Match match = matchService.findById(id)
                .orElseThrow(() -> new RuntimeException("Partido no encontrado"));

        if (match.isLocked()) {
            ra.addFlashAttribute("error", "Este partido ya no acepta predicciones.");
            return "redirect:/matches/" + id;
        }

        predictionService.savePrediction(currentUser, match, homeScore, awayScore);
        ra.addFlashAttribute("success", "¡Predicción guardada!");
        return "redirect:/matches/" + id;
    }
}
