Add-Type -AssemblyName System.Drawing
# Escanea los 72 frames FINALES (1080x1920) que se suben a Play, no las crudas.
# El movil va embebido en x=188..892, y=~360..1920. Dos defectos a cazar:
#  - dialogo ANR del sistema: gris NEUTRO (B-R<=10) en la banda central
#  - mapa sin cargar: casi negro (todos los canales bajos) donde deberia haber tiles
$root = "C:\Users\rndev\Documents\AndroidProjects\Paparcar\docs\release\play-listing\assets\screenshots"
foreach ($L in @("en","es","it","pt","fr","de","nl","pl","ro")) {
  foreach ($n in 1..8) {
    $p = Join-Path $root "$L\play-shot-$n.png"
    if (-not (Test-Path $p)) { Write-Output "FALTA $L/$n"; continue }
    $img = [System.Drawing.Bitmap]::FromFile($p)
    $gray = 0; $black = 0; $tot = 0
    for ($x = 260; $x -le 820; $x += 40) {
      for ($y = 700; $y -le 1500; $y += 40) {
        $c = $img.GetPixel($x, $y)
        $tot++
        if ((($c.B - $c.R) -le 10) -and $c.R -ge 15 -and $c.R -le 45 -and $c.G -ge 15 -and $c.G -le 45) { $gray++ }
        if ($c.R -le 12 -and $c.G -le 12 -and $c.B -le 14) { $black++ }
      }
    }
    $img.Dispose()
    $g = [math]::Round(100.0 * $gray / $tot); $b = [math]::Round(100.0 * $black / $tot)
    if ($g -ge 35) { Write-Output "ANR?   $L/play-shot-$n -> $g pct gris neutro" }
    elseif ($b -ge 45) { Write-Output "NEGRO? $L/play-shot-$n -> $b pct casi negro" }
  }
}
Write-Output "scan final done"
