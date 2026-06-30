$path = "D:\TRABAJOS SPRINGBOOT\MundialPredictorApplication\src\main\resources\static\css\style.css"

# Read the file content
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# Define the old responsive block start marker (using the special Unicode dash character)
$oldStart = "/* " + [char]0x2502 + [char]0x2502 + " Responsive " + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + [char]0x2502 + " */"

# Try alternative: the ─ character might be U+2500
$oldStart2 = "/* " + [char]0x2500 + [char]0x2500 + " Responsive " + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + [char]0x2500 + " */"

Write-Host "Looking for oldStart..."
$idx1 = $content.IndexOf($oldStart)
Write-Host "Index 1: $idx1"

Write-Host "Looking for oldStart2..."
$idx2 = $content.IndexOf($oldStart2)
Write-Host "Index 2: $idx2"

# Find the closing brace of the 767px media query
$closeMarker = "  .m-match-item-score,"
$idxClose = $content.IndexOf($closeMarker)
Write-Host "Close marker index: $idxClose"

# Show context around the close block
$closeBrace = "}`n`n/* " + [char]0x2550 + [char]0x2550 + [char]0x2550 + [char]0x2550
$idxEnd = $content.IndexOf($closeBrace)
Write-Host "End block index: $idxEnd"

# Try a different approach - find by line number
$lines = $content -split [Environment]::NewLine
Write-Host "Line count: $($lines.Length)"
if ($lines.Length -ge 1390) {
    Write-Host "Line 1391: '$($lines[1390])'"
    $lineChars = $lines[1390].ToCharArray()
    Write-Host "Chars: $($lineChars -join ' ')"
}
