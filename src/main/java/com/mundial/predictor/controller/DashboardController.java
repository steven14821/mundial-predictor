package com.mundial.predictor.controller;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.Phase;
import com.mundial.predictor.model.User;
import com.mundial.predictor.service.MatchService;
import com.mundial.predictor.service.UserService;
import com.mundial.predictor.service.PredictionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private final UserService userService;
    private final MatchService matchService;
    private final PredictionService predictionService;

    public DashboardController(UserService userService, MatchService matchService, PredictionService predictionService) {
        this.userService = userService;
        this.matchService = matchService;
        this.predictionService = predictionService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        predictionService.recalculateAllTotals(); // Auto-recalcular puntos al cargar dashboard

        User currentUser = userService.findByUsername(userDetails.getUsername());
        List<User> players = userService.findAllPlayers();
        List<Match> upcoming = matchService.findUpcoming();
        User leader = players.isEmpty() ? null : players.get(0);
        Integer scoreGap = players.size() >= 2
                ? Math.abs(players.get(0).getTotalPoints() - players.get(1).getTotalPoints())
                : null;

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("players", players);
        model.addAttribute("upcomingMatches", upcoming);
        model.addAttribute("leader", leader);
        model.addAttribute("scoreGap", scoreGap);
        return "dashboard";
    }

    @GetMapping("/playoffs")
    public String playoffs(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User currentUser = userService.findByUsername(userDetails.getUsername());
        List<Match> allMatches = matchService.findAll();

        List<Match> ronda32 = allMatches.stream()
                .filter(m -> m.getPhase() == Phase.RONDA32)
                .collect(Collectors.toList());
        List<Match> octavos = allMatches.stream()
                .filter(m -> m.getPhase() == Phase.OCTAVOS)
                .collect(Collectors.toList());
        List<Match> cuartos = allMatches.stream()
                .filter(m -> m.getPhase() == Phase.CUARTOS)
                .collect(Collectors.toList());
        List<Match> semifinal = allMatches.stream()
                .filter(m -> m.getPhase() == Phase.SEMIFINAL)
                .collect(Collectors.toList());
        List<Match> finals = allMatches.stream()
                .filter(m -> m.getPhase() == Phase.FINAL || m.getPhase() == Phase.TERCER_PUESTO)
                .collect(Collectors.toList());

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("ronda32", ronda32);
        model.addAttribute("octavos", octavos);
        model.addAttribute("cuartos", cuartos);
        model.addAttribute("semifinal", semifinal);
        model.addAttribute("finals", finals);
        return "playoffs";
    }
}
