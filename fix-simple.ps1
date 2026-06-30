$path = "D:\TRABAJOS SPRINGBOOT\MundialPredictorApplication\src\main\resources\static\css\style.css"
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# Add overflow-x: hidden to body tag
$oldBody = "min-height: 100vh;"
$newBody = "min-height: 100vh;`n  overflow-x: hidden;"
$content = $content.Replace($oldBody, $newBody)

# Update m-main max-width from 520px to be more responsive
$content = $content.Replace("max-width: 520px;", "max-width: 640px;")

[System.IO.File]::WriteAllText($path, $content, [System.Text.Encoding]::UTF8)
Write-Host "Done - basic fixes applied"