$path = "D:\TRABAJOS SPRINGBOOT\MundialPredictorApplication\src\main\resources\static\css\style.css"
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

$insertIdx = 31918

# PART A of the new responsive CSS
$newBlockPart1 = @"
/* ═══════════════════════════════════════════════════════════════
   RESPONSIVE — Mobile-First con breakpoints consistentes
   ═══════════════════════════════════════════════════════════════ */

/* ── Móviles muy pequeños (< 400px) ─────────────────────────── */
@media (max-width: 399px) {
  .m-main {
    padding: 0 12px 60px;
  }
  .m-greeting,
  .m-duel-header,
  .m-duel-footer,
  .m-duel,
  .m-match-header {
    margin-left: -12px !important;
    margin-right: -12px !important;
  }
  .m-greeting-name,
  .m-greeting__name {
    font-size: 2.4rem !important;
  }
  .m-greeting::after {
    font-size: 100px !important;
  }
  .m-duel-pts,
  .m-duel__score {
    font-size: 3rem !important;
  }
  .m-match-title {
    font-size: 1.5rem !important;
    flex-wrap: wrap;
    word-break: break-word;
  }
  .m-match-teams {
    font-size: 1.1rem !important;
    gap: 6px;
  }
  .m-ranking-pos {
    font-size: 1.6rem !important;
    min-width: 22px;
  }
  .m-ranking-pts {
    font-size: 1.6rem !important;
  }
  .m-section-title {
    font-size: 1.25rem !important;
  }
  .m-pred-grid {
    max-width: 100% !important;
    gap: 10px;
  }
  .m-form-input {
    font-size: 2rem !important;
  }
  .playoff-card-right {
    width: auto !important;
    min-width: 60px;
    padding: 6px 8px !important;
  }
  .playoff-time {
    font-size: 1.1rem !important;
  }
  .playoff-team-name {
    font-size: 0.85rem !important;
  }
  .m-login-box {
    padding: 28px 20px !important;
    margin: 0 8px !important;
  }
  .m-login-title {
    font-size: 1.8rem !important;
  }
}

/* ── Móviles (400px - 599px) ────────────────────────────────── */
@media (min-width: 400px) and (max-width: 599px) {
  .m-greeting-name,
  .m-greeting__name {
    font-size: 2.8rem !important;
  }
  .m-greeting::after {
    font-size: 120px !important;
  }
  .m-duel-pts,
  .m-duel__score {
    font-size: 3.5rem !important;
  }
  .m-match-title {
    font-size: 1.75rem !important;
  }
  .m-match-teams {
    font-size: 1.25rem !important;
  }
  .m-form-input {
    font-size: 2.2rem !important;
  }
  .playoff-card-right {
    width: auto !important;
    min-width: 70px;
  }
  .playoff-time {
    font-size: 1.25rem !important;
  }
}

"@

$content = $content.Substring(0, $insertIdx) + $newBlockPart1 + $content.Substring($insertIdx)

# Update insertIdx for part2
$insertIdx += $newBlockPart1.Length

# PART B of the new responsive CSS
$newBlockPart2 = @"
/* ── Móviles en general (< 600px) ───────────────────────────── */
@media (max-width: 599px) {
  .m-greeting,
  .m-duel-header,
  .m-duel-footer,
  .m-duel,
  .m-match-header {
    margin-left: -16px;
    margin-right: -16px;
  }
  .m-duel-grid,
  .m-duel__grid {
    grid-template-columns: 1fr !important;
    gap: 16px !important;
    text-align: center !important;
  }
  .m-duel-player,
  .m-duel__player {
    text-align: center !important;
    align-items: center !important;
    padding-bottom: 0 !important;
  }
  .m-duel-player--right,
  .m-duel__player--right {
    text-align: center !important;
    align-items: center !important;
  }
  .m-scoreboard,
  .m-duel__vs {
    align-self: center !important;
    margin: 8px auto 16px !important;
  }
  .m-fixture__top {
    flex-direction: column !important;
    align-items: stretch !important;
    gap: 12px !important;
  }
  .m-fixture__teams {
    justify-content: center !important;
  }
  .m-fixture__top .btn {
    width: 100% !important;
    text-align: center !important;
  }
  .m-fixture__duel {
    margin: 0 -18px -20px !important;
    padding: 12px 14px !important;
  }
  .m-fixture__duel-grid {
    grid-template-columns: 1fr auto 1fr !important;
    gap: 8px !important;
    text-align: left !important;
  }
  .m-fixture__duel-player {
    text-align: left !important;
    align-items: flex-start !important;
    padding-bottom: 0 !important;
  }
  .m-fixture__duel-player--right {
    text-align: right !important;
    align-items: flex-end !important;
  }
  .m-fixture__duel-name {
    font-size: 0.8rem !important;
  }
  .m-fixture__duel-pick {
    font-size: 0.7rem !important;
  }
  .m-fixture__duel-vs {
    font-size: 0.8rem !important;
    padding: 3px 6px !important;
    margin: 0 !important;
    align-self: center !important;
  }
  .m-match-teams {
    font-size: 1.2rem;
    gap: 8px;
  }
  .m-pred-grid {
    max-width: 280px;
  }
  .m-form-input {
    font-size: 2rem;
  }
  .m-section-title {
    font-size: 1.25rem;
  }
  .bracket-container {
    gap: 0;
    padding-top: 10px;
    min-height: auto;
  }
  .bracket-column {
    display: none;
    width: 100%;
  }
  .bracket-column.active-column {
    display: flex;
  }
  .matches-list {
    gap: 15px;
  }
  .m-login-box {
    padding: 36px 24px;
  }
  .m-login-title {
    font-size: 2rem;
  }
}

"@

$content = $content.Substring(0, $insertIdx) + $newBlockPart2 + $content.Substring($insertIdx)
$insertIdx += $newBlockPart2.Length

# PART C of the new responsive CSS
$newBlockPart3 = @"
/* ── Tabletas (600px - 767px) ───────────────────────────────── */
@media (min-width: 600px) and (max-width: 767px) {
  .m-main {
    padding-left: 28px;
    padding-right: 28px;
  }
  .m-greeting-name,
  .m-greeting__name {
    font-size: 3.2rem;
  }
  .m-greeting::after {
    font-size: 150px;
  }
  .m-duel-pts,
  .m-duel__score {
    font-size: 4.5rem;
  }
}

/* ── Tablet landscape / Desktop pequeño (≥ 600px) ────────── */
"@

$content = $content.Substring(0, $insertIdx) + $newBlockPart3 + $content.Substring($insertIdx)

[System.IO.File]::WriteAllText($path, $content, [System.Text.Encoding]::UTF8)

Write-Host "New responsive section inserted! File length: $($content.Length)"
