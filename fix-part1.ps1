$path = "D:\TRABAJOS SPRINGBOOT\MundialPredictorApplication\src\main\resources\static\css\style.css"
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# Find old responsive section
$respIdx = $content.IndexOf("Responsive")
if ($respIdx -lt 0) { Write-Host "ERROR: Responsive not found"; exit 1 }
$startIdx = $content.LastIndexOf("`r`n", $respIdx) + 2
Write-Host "Start index: $startIdx"

# Find NEW BEM CLASSES
$endSearch = $content.IndexOf("NEW BEM CLASSES")
if ($endSearch -lt 0) { Write-Host "ERROR: NEW BEM CLASSES not found"; exit 1 }
$prevNewLine = $content.LastIndexOf("`r`n", $endSearch - 10)
$endIdx = $prevNewLine + 2
Write-Host "End index: $endIdx"

$oldBlock = $content.Substring($startIdx, $endIdx - $startIdx)
Write-Host "Old block length: $($oldBlock.Length)"
Write-Host "Old block starts with: '$($oldBlock.Substring(0, [Math]::Min(50, $oldBlock.Length)))'"

# Save start/end indices for part2
$startIdx | Out-File "D:\TRABAJOS SPRINGBOOT\MundialPredictorApplication\_start.txt"
$endIdx | Out-File "D:\TRABAJOS SPRINGBOOT\MundialPredictorApplication\_end.txt"
Write-Host "Indices saved"
