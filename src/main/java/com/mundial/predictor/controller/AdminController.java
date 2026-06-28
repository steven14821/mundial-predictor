package com.mundial.predictor.controller;

import com.mundial.predictor.model.Match;
import com.mundial.predictor.model.Phase;
import com.mundial.predictor.service.MatchService;
import com.mundial.predictor.service.PredictionService;
import com.mundial.predictor.service.WorldCupSyncService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final MatchService matchService;
    private final PredictionService predictionService;
    private final WorldCupSyncService worldCupSyncService;

    public AdminController(MatchService matchService, PredictionService predictionService, WorldCupSyncService worldCupSyncService) {
        this.matchService = matchService;
        this.predictionService = predictionService;
        this.worldCupSyncService = worldCupSyncService;
    }

    @GetMapping("/matches")
    public String manageMatches(Model model) {
        model.addAttribute("matches", matchService.findAll());
        return "admin/matches";
    }

    @GetMapping("/matches/new")
    public String newMatchForm(Model model) {
        model.addAttribute("match", new Match());
        model.addAttribute("phases", Phase.values());
        return "admin/match-form";
    }

    @PostMapping("/matches/new")
    public String createMatch(@ModelAttribute Match match, RedirectAttributes ra) {
        matchService.save(match);
        ra.addFlashAttribute("success", "Partido creado correctamente.");
        return "redirect:/admin/matches";
    }

    @PostMapping("/matches/init-schedule")
    public String initSchedule(RedirectAttributes ra) {
        try {
            matchService.seedAllMatchesIfMissing();
            ra.addFlashAttribute("success", "Calendario de partidos inicializado localmente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al inicializar el calendario: " + e.getMessage());
        }
        return "redirect:/admin/matches";
    }

    @PostMapping("/matches/sync-group-stage")
    public String syncGroupStage(RedirectAttributes ra) {
        WorldCupSyncService.SyncResult result = worldCupSyncService.syncGroupStageMatches();
        if (result.success()) {
            ra.addFlashAttribute("success", result.message());
        } else {
            ra.addFlashAttribute("error", result.message());
        }
        return "redirect:/admin/matches";
    }

    @PostMapping("/matches/sync-knockout")
    public String syncKnockout(RedirectAttributes ra) {
        WorldCupSyncService.SyncResult result = worldCupSyncService.syncKnockoutMatches();
        if (result.success()) {
            ra.addFlashAttribute("success", result.message());
        } else {
            ra.addFlashAttribute("error", result.message());
        }
        return "redirect:/admin/matches";
    }

    @PostMapping("/matches/sync-results")
    public String syncResults(RedirectAttributes ra) {
        WorldCupSyncService.SyncResultWithMatches result = worldCupSyncService.syncResults();

        // Calcular puntos para cada partido que recién terminó
        for (com.mundial.predictor.model.Match match : result.newlyFinishedMatches()) {
            predictionService.calculatePoints(match);
        }

        if (result.success()) {
            ra.addFlashAttribute("success", "[OK] " + result.message());
        } else {
            ra.addFlashAttribute("error", "[Error] " + result.message());
        }
        return "redirect:/admin/matches";
    }


    @GetMapping("/matches/{id}/edit")
    public String editMatchForm(@PathVariable Long id, Model model) {
        Match match = matchService.findById(id)
                .orElseThrow(() -> new RuntimeException("Partido no encontrado: " + id));
        model.addAttribute("match", match);
        model.addAttribute("phases", Phase.values());
        return "admin/match-form";
    }

    @PostMapping("/matches/{id}/edit")
    public String updateMatch(@PathVariable Long id,
                              @ModelAttribute Match matchDetails,
                              RedirectAttributes ra) {
        Match match = matchService.findById(id)
                .orElseThrow(() -> new RuntimeException("Partido no encontrado: " + id));
        match.setHomeTeam(matchDetails.getHomeTeam());
        match.setAwayTeam(matchDetails.getAwayTeam());
        match.setHomeFlag(matchDetails.getHomeFlag());
        match.setAwayFlag(matchDetails.getAwayFlag());
        match.setPhase(matchDetails.getPhase());
        if (matchDetails.getPhase() != null && matchDetails.getPhase().isGroupStage()) {
            match.setMatchGroup(matchDetails.getMatchGroup());
        } else {
            match.setMatchGroup(null);
        }
        match.setMatchDate(matchDetails.getMatchDate());
        matchService.save(match);
        ra.addFlashAttribute("success", "Partido actualizado correctamente.");
        return "redirect:/admin/matches";
    }

    @GetMapping("/matches/{id}/score")
    public String scoreForm(@PathVariable Long id, Model model) {
        model.addAttribute("match", matchService.findById(id)
                .orElseThrow(() -> new RuntimeException("Partido no encontrado")));
        return "admin/score-form";
    }

    @PostMapping("/matches/{id}/score")
    public String setScore(@PathVariable Long id,
                           @RequestParam int homeScore,
                           @RequestParam int awayScore,
                           RedirectAttributes ra) {
        Match match = matchService.setScore(id, homeScore, awayScore);
        predictionService.calculatePoints(match);
        ra.addFlashAttribute("success", "Resultado registrado y puntos calculados.");
        return "redirect:/admin/matches";
    }
}
