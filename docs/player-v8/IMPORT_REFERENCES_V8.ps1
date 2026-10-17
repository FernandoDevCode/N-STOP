# NØNSTOP v8: import original approved PNG references with exact SHA-256 verification.
# Run from any directory: powershell -NoProfile -ExecutionPolicy Bypass -File .\docs\player-v8\IMPORT_REFERENCES_V8.ps1 -Publish
param([switch]$Publish)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$branch = (& git -C $root branch --show-current).Trim()
if ($branch -ne 'feat/nonstop-v8-art-lab') {
  throw "Branch errada: $branch. Use feat/nonstop-v8-art-lab."
}
$dest = Join-Path $PSScriptRoot 'references'
New-Item -ItemType Directory -Path $dest -Force | Out-Null
$specs = @(
  @{ Name = 'MASTER_PINK_FLOYD.png'; Source = '02_PLAYER_PINK_FLOYD_PALETA-SELETIVA.png'; Sha = '6b7cbd4da0fa601379b2d8d790d70aac2b532d22bb35739370fc3958fec430f0' },
  @{ Name = 'MASTER_MEGADETH.png'; Source = '03_PLAYER_MEGAdeth_PALETA-AZUL.png'; Sha = '3547184210edca28399a0945dc28192e65341ebd1545be070873593004e3d68d' }
)
$srcDirs = @(
  (Join-Path $root '02_CHECKPOINT_2'),
  (Join-Path $env:USERPROFILE 'Documents\GitHub\Spotify\Stash\02_CHECKPOINT_2'),
  (Join-Path $env:USERPROFILE 'Downloads\02_CHECKPOINT_2'),
  (Join-Path $env:USERPROFILE 'Desktop\02_CHECKPOINT_2')
) | Select-Object -Unique
$zipDirs = @($root, (Join-Path $env:USERPROFILE 'Downloads'), (Join-Path $env:USERPROFILE 'Desktop')) | Select-Object -Unique
$zipName = 'NONSTOP_checkpoint_2_player_v7_referencias.zip'
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Sha256([string]$file) { return (Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash.ToLowerInvariant() }
foreach ($s in $specs) {
  $target = Join-Path $dest $s.Name
  if (Test-Path -LiteralPath $target) {
    if ((Sha256 $target) -ne $s.Sha) { throw "Referencia existente nao confere: $target. Nao sobrescrevi." }
    Write-Host "Verificado: $($s.Name)" -ForegroundColor Green
    continue
  }
  $found = $false
  foreach ($dir in $srcDirs) {
    $path = Join-Path $dir $s.Source
    if (Test-Path -LiteralPath $path) {
      if ((Sha256 $path) -ne $s.Sha) { throw "Fonte local com hash diferente: $path" }
      Copy-Item -LiteralPath $path -Destination $target
      $found = $true; break
    }
  }
  if (-not $found) {
    foreach ($dir in $zipDirs) {
      $zipPath = Join-Path $dir $zipName
      if (-not (Test-Path -LiteralPath $zipPath)) { continue }
      $z = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
      try {
        $entry = $z.Entries | Where-Object { $_.FullName.Replace('\','/').EndsWith('/' + $s.Source) } | Select-Object -First 1
        if ($null -ne $entry) {
          [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target)
          if ((Sha256 $target) -ne $s.Sha) { Remove-Item -LiteralPath $target -Force; throw "PNG extraido com hash incorreto: $zipPath" }
          $found = $true; break
        }
      } finally { $z.Dispose() }
    }
  }
  if (-not $found) {
    throw "Referencia $($s.Source) nao encontrada. Confira a pasta local 02_CHECKPOINT_2 ou o ZIP original em Downloads."
  }
  if ((Sha256 $target) -ne $s.Sha) { throw "Hash final diferente: $target" }
  Write-Host "Importado e validado: $($s.Name)" -ForegroundColor Green
}
Write-Host "As duas imagens mestras estao em $dest" -ForegroundColor Cyan
if ($Publish) {
  $paths = @('docs/player-v8/references/MASTER_PINK_FLOYD.png','docs/player-v8/references/MASTER_MEGADETH.png')
  & git -C $root add -- $paths
  if ($LASTEXITCODE -ne 0) { throw 'git add falhou' }
  $changes = @(& git -C $root diff --cached --name-only -- $paths)
  if ($changes.Count -gt 0) {
    & git -C $root commit --only -m 'docs(v8): importa as referencias mestras verificadas' -- $paths
    if ($LASTEXITCODE -ne 0) { throw 'git commit falhou' }
    & git -C $root push origin 'feat/nonstop-v8-art-lab'
    if ($LASTEXITCODE -ne 0) { throw 'git push falhou' }
    Write-Host 'Publicadas no GitHub: apenas as duas referencias mestras.' -ForegroundColor Green
  } else { Write-Host 'As referencias ja estavam versionadas, nada para publicar.' }
}
