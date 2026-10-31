param([ValidateRange(1,3)][int]$Cycle=1)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$repositoryRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$captureRoot=Join-Path $repositoryRoot "labs/art-lab/build/captures-1b/cycle-$Cycle"
$evidenceRoot=Join-Path $repositoryRoot "docs/player-v8/validation/phase-1b/cycle-$Cycle"
if(-not(Test-Path (Join-Path $captureRoot 'complete.txt'))) { throw 'Capture suite has not completed' }
New-Item -ItemType Directory -Force $evidenceRoot | Out-Null
Copy-Item (Join-Path $captureRoot '*') $evidenceRoot
foreach($pair in @(@('PINK_FLOYD','reference-1-941x1672'),@('MEGADETH','reference-2-864x1536'))) {
    $master=[System.Drawing.Bitmap]::FromFile((Join-Path $repositoryRoot "docs/player-v8/references/MASTER_$($pair[0]).png"))
    $actual=[System.Drawing.Bitmap]::FromFile((Join-Path $evidenceRoot "$($pair[1]).png"))
    try {
        if($master.Width -ne $actual.Width -or $master.Height -ne $actual.Height) { throw 'Reference and live capture sizes differ' }
        $output=[System.Drawing.Bitmap]::new($master.Width*2,$master.Height)
        $g=[System.Drawing.Graphics]::FromImage($output)
        try {
            $g.DrawImageUnscaled($master,0,0); $g.DrawImageUnscaled($actual,$master.Width,0)
            $output.Save((Join-Path $evidenceRoot "comparison-$($pair[0]).png"),[System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $g.Dispose(); $output.Dispose() }
    } finally { $master.Dispose(); $actual.Dispose() }
}
Write-Output "Evidence: $evidenceRoot (master left / live renderer right, same pixels)"
foreach($sample in @(@(1,1010),@(2,926))) {
    if(Test-Path (Join-Path $evidenceRoot "reference-$($sample[0])-progress-25.png")) {
        $values=foreach($percent in @(25,50,100)) {
            $bmp=[System.Drawing.Bitmap]::FromFile((Join-Path $evidenceRoot "reference-$($sample[0])-progress-$percent.png"))
            try { $bmp.GetPixel(200,$sample[1]).ToArgb() } finally { $bmp.Dispose() }
        }
        if(@($values | Select-Object -Unique).Count -ne 1) { throw 'Seek rescaled the gradient' }
        "Reference $($sample[0]) fixed coordinate (200,$($sample[1])): $($values -join ', ')" | Set-Content (Join-Path $evidenceRoot "reference-$($sample[0])-pixel-check.txt")
    }
}
