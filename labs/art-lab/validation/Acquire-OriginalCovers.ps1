param([switch]$Refresh)
$ErrorActionPreference = 'Stop'
$labRoot = Split-Path -Parent $PSScriptRoot
$manifestPath = Join-Path $labRoot 'cover-sources.json'
$sources = @(Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)
$editions = @(
    @('Nirvana', 'Nevermind', 1440783617, 'NIRVANA'),
    @('Michael Jackson', 'Thriller', 269572838, 'THRILLER'),
    @('The Beatles', 'Abbey Road', 1441164426, 'ABBEY_ROAD'),
    @('Daft Punk', 'Random Access Memories', 617154241, 'DAFT_PUNK'),
    @('Joy Division', 'Unknown Pleasures', 1476702180, 'JOY_DIVISION'),
    @('Kendrick Lamar', 'To Pimp a Butterfly', 1440871877, 'KENDRICK'),
    @('Travis Scott', 'Astroworld', 1421658111, 'ASTROWORLD'),
    @('Frank Ocean', 'Blonde', 1146195596, 'BLONDE'),
    @('Metallica', 'Master of Puppets', 1275600311, 'METALLICA'),
    @('Kanye West', 'Graduation', 1442845779, 'GRADUATION')
)
Add-Type -AssemblyName System.Drawing
foreach ($edition in $editions) {
    $existing = $sources | Where-Object { $_.collectionId -eq $edition[2] }
    if ($existing -and !$Refresh) {
        $existingFile = Join-Path $labRoot ('src/desktopMain/resources/' + $existing.file)
        if ((Test-Path -LiteralPath $existingFile) -and
            (Get-FileHash -LiteralPath $existingFile -Algorithm SHA256).Hash.ToLowerInvariant() -eq $existing.sha256) { continue }
    }
    $lookupUrl = 'https://itunes.apple.com/lookup?id=' + $edition[2]
    $raw = Invoke-WebRequest -Uri $lookupUrl
    $response = $raw.Content | ConvertFrom-Json
    $record = $response.results | Where-Object { $_.collectionId -eq $edition[2] -and $_.artistName -eq $edition[0] } | Select-Object -First 1
    if (!$record -or !$record.collectionName.StartsWith($edition[1], [StringComparison]::OrdinalIgnoreCase)) {
        throw "Official lookup does not match intended edition: $($edition[0]) / $($edition[1])"
    }
    $downloadUrl = $record.artworkUrl100 -replace '/100x100bb\.', '/1200x1200bb.'
    $resource = 'covers/' + $edition[3] + '.jpg'
    $destination = Join-Path $labRoot ('src/desktopMain/resources/' + $resource)
    Invoke-WebRequest -Uri $downloadUrl -OutFile $destination
    $image = [System.Drawing.Image]::FromFile($destination)
    try { $imageWidth=$image.Width; $imageHeight=$image.Height } finally { $image.Dispose() }
    $evidenceDir = Join-Path $PSScriptRoot 'sources'
    New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
    [IO.File]::WriteAllText((Join-Path $evidenceDir ($edition[2].ToString()+'.json')), $raw.Content)
    $entry = [pscustomobject][ordered]@{
        artist=$edition[0]; album=$edition[1]; edition=$record.collectionName; collectionId=$record.collectionId
        lookupUrl=$lookupUrl; albumUrl=$record.collectionViewUrl; downloadUrl=$downloadUrl; copyright=$record.copyright
        file=$resource; sha256=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        status='available'; sourceKind='official-album-artwork'; retrievedAt='2026-10-18'
        usage='Local experimental validation; not a grant of redistribution rights'; width=$imageWidth; height=$imageHeight
    }
    $sources = @($sources | Where-Object { $_.collectionId -ne $edition[2] }) + $entry
    $sources | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    Write-Output "$($edition[0]) / $($record.collectionName): ${imageWidth}x${imageHeight} $($entry.sha256)"
}
