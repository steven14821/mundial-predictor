package com.mundial.predictor.controller;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.User;
import com.mundial.predictor.service.MatchService;
import com.mundial.predictor.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    private final UserService userService;
    private final MatchService matchService;

    public DashboardController(UserService userService, MatchService matchService) {
        this.userService = userService;
        this.matchService = matchService;
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
}
