$path = "D:\TRABAJOS SPRINGBOOT\MundialPredictorApplication\src\main\resources\static\css\style.css"
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# Fix the merged comment at line 1625
$badText = "/* ── Tablet landscape / Desktop pequeño (≥ 600px) ────────── *//* ═══════════════════════════════════════════════════════════════"
$goodText = "/* ── Tablet landscape / Desktop pequeño (≥ 600px) ────────── */
@media (min-width: 600px) {
  .m-main {
    padding-left: 28px;
    padding-right: 28px;
  }

  .m-ranking {
    grid-template-columns: 1fr 1fr;
  }

  .m-greeting {
    margin-left: -28px;
    margin-right: -28px;
    padding-left: 32px;
    padding-right: 32px;
  }

  .m-duel-header,
  .m-duel-footer {
    margin-left: -28px;
    margin-right: -28px;
    padding-left: 28px;
    padding-right: 28px;
  }

  .m-match-header {
    margin-left: -28px;
    margin-right: -28px;
    padding-left: 28px;
    padding-right: 28px;
  }

  .m-duel-pts { font-size: 6rem; }
  .m-match-teams { font-size: 1.5rem; }
  .m-greeting-name { font-size: 4rem; }
  .m-greeting::after { font-size: 180px; }

  .m-login-box {
    max-width: 420px;
    margin: 0 auto;
  }
}

/* ── Desktop (≥ 768px) ──────────────────────────────────────── */
@media (min-width: 768px) {
  .m-main {
    max-width: 900px;
  }

  .bracket-container {
    gap: 40px;
    padding-top: 30px;
    min-height: 80vh;
  }

  .bracket-column {
    display: flex !important;
  }

  .playoff-tabs {
    display: none !important;
  }
}

/* ── Desktop grande (≥ 992px) ────────────────────────────── */
@media (min-width: 992px) {
  .m-main {
    max-width: 1120px;
  }

  .m-grid-2 {
    grid-template-columns: 1fr 1fr;
  }

  .m-login {
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .m-login-box {
    max-width: 440px;
  }

  .m-duel-pts { font-size: 7rem; }
}

/* ── Desktop extra grande (≥ 1200px) ─────────────────────── */
@media (min-width: 1200px) {
  .m-main {
    max-width: 1200px;
  }

  .m-duel-pts { font-size: 8rem; }
  .m-greeting-name { font-size: 4.5rem; }
  .m-greeting::after { font-size: 220px; }
}

/* ═══════════════════════════════════════════════════════════════
   NEW BEM CLASSES — Layout & Dashboard
   ═══════════════════════════════════════════════════════════════ */"

$content = $content.Replace($badText, $goodText)

if ($content.Contains($badText)) {
    Write-Host "ERROR: Text still found after replace"
} else {
    Write-Host "Successfully replaced the merged comment!"
}

[System.IO.File]::WriteAllText($path, $content, [System.Text.Encoding]::UTF8)
Write-Host "Done! File length: $($content.Length)"
