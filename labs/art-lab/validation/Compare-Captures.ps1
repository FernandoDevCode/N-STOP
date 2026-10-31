param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$captureRoot = Join-Path $repositoryRoot 'labs/art-lab/build/captures'
$evidenceRoot = Join-Path $repositoryRoot 'docs/player-v8/validation/phase-1a'
New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null

# These are copies/contact sheets of real UI captures, never a substitute renderer.
Get-ChildItem -LiteralPath $captureRoot -Filter '*.png' | Where-Object {
    $_.Name -ne 'catalog-01-864x1536.png' # obsolete first-cycle size; final PF matches its original
} | Copy-Item -Destination $evidenceRoot
Copy-Item -LiteralPath (Join-Path $repositoryRoot 'labs/art-lab/build/exports/catalog-manifest.json') -Destination $evidenceRoot

foreach ($pair in @(
    @('MEGADETH','catalog-02-864x1536.png'),
    @('PINK_FLOYD','catalog-01-941x1672.png')
)) {
    $masterPath = Join-Path $repositoryRoot "docs/player-v8/references/MASTER_$($pair[0]).png"
    $master = [System.Drawing.Bitmap]::FromFile($masterPath)
    $actual = [System.Drawing.Bitmap]::FromFile((Join-Path $evidenceRoot $pair[1]))
    try {
        if ($master.Width -ne $actual.Width -or $master.Height -ne $actual.Height) { throw 'Reference/capture size mismatch' }
        $comparison = [System.Drawing.Bitmap]::new($master.Width*2,$master.Height)
        $graphics = [System.Drawing.Graphics]::FromImage($comparison)
        try {
            $graphics.DrawImageUnscaled($master,0,0)
            $graphics.DrawImageUnscaled($actual,$master.Width,0)
            $comparison.Save((Join-Path $evidenceRoot "comparison-$($pair[0]).png"),[System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $graphics.Dispose(); $comparison.Dispose() }
    } finally { $master.Dispose(); $actual.Dispose() }
}

function Write-ContactSheet([string[]] $names,[string] $output,[int] $columns) {
    $cellW = 236; $cellH = 440
    $rows = [int][math]::Ceiling($names.Count / $columns)
    $sheet = [System.Drawing.Bitmap]::new($cellW*$columns,$cellH*$rows)
    $g = [System.Drawing.Graphics]::FromImage($sheet)
    $font = [System.Drawing.Font]::new('Segoe UI',9)
    try {
        $g.Clear([System.Drawing.Color]::FromArgb(18,22,30))
        for ($i=0; $i -lt $names.Count; $i++) {
            $src = [System.Drawing.Bitmap]::FromFile((Join-Path $evidenceRoot $names[$i]))
            try {
                $x=($i % $columns)*$cellW; $y=[int][math]::Floor($i/$columns)*$cellH
                $g.DrawString($names[$i],$font,[System.Drawing.Brushes]::White,$x+3,$y+2)
                $ratio=[math]::Min(228/$src.Width,410/$src.Height)
                $g.DrawImage($src,$x+4,$y+24,[int]($src.Width*$ratio),[int]($src.Height*$ratio))
            } finally { $src.Dispose() }
        }
        $sheet.Save((Join-Path $evidenceRoot $output),[System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $font.Dispose(); $g.Dispose(); $sheet.Dispose() }
}
$catalog = @(Get-ChildItem -LiteralPath $evidenceRoot -Filter 'catalog-*.png' | Sort-Object Name | Select-Object -ExpandProperty Name)
Write-ContactSheet $catalog 'catalog-contact-sheet.png' 4
Write-ContactSheet @('progress-0.png','progress-25.png','progress-50.png','progress-100.png') 'progress-contact-sheet.png' 4

# At a fixed already-revealed coordinate, all nonzero seek screenshots must retain the same color.
$samples = foreach ($percent in @(25,50,100)) {
    $bmp=[System.Drawing.Bitmap]::FromFile((Join-Path $evidenceRoot "progress-$percent.png"))
    try { $bmp.GetPixel(200,1010).ToArgb() } finally { $bmp.Dispose() }
}
if (@($samples | Select-Object -Unique).Count -ne 1) { throw 'Progress gradient changed at a fixed coordinate' }
"Fixed-coordinate progress check: $($samples -join ', ')" | Set-Content (Join-Path $evidenceRoot 'pixel-check.txt')
Write-Output "Evidence written to $evidenceRoot"
