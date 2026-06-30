$path = "D:\TRABAJOS SPRINGBOOT\MundialPredictorApplication\src\main\resources\static\css\style.css"
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

$startIdx = 31918
$endIdx = 35375

Write-Host "Removing old block ($startIdx to $endIdx)..."
$content = $content.Substring(0, $startIdx) + $content.Substring($endIdx)
[System.IO.File]::WriteAllText($path, $content, [System.Text.Encoding]::UTF8)
Write-Host "Deleted! File length now: $($content.Length)"
