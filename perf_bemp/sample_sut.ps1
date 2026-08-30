# Sample backend process resources (pure ASCII to avoid PS ANSI decoding issues)
$TargetPid = 7048
$IntervalSec = 2
$Out = "d:/code/otherProjects/18_comparePakage/output/results/sut_resource.csv"
$MaxN = 150
Write-Output ("sampler-start pid=" + $TargetPid)
$proc = Get-Process -Id $TargetPid -ErrorAction SilentlyContinue
if (-not $proc) { Write-Output "PROC_NOT_FOUND"; exit 1 }
if (-not (Test-Path $Out)) { "ts,ws_mb,private_mb,cpu_pct" | Set-Content $Out -Encoding ascii }
$c1 = $proc.CPU; $t1 = Get-Date; $n = 0
while ($true) {
  Start-Sleep -Seconds $IntervalSec
  $p2 = Get-Process -Id $TargetPid -ErrorAction SilentlyContinue
  if (-not $p2) { break }
  $c2 = $p2.CPU; $t2 = Get-Date
  $cpuPct = 0
  if (($t2 - $t1).TotalSeconds -gt 0) { $cpuPct = [Math]::Round((($c2 - $c1) / ($t2 - $t1).TotalSeconds) * 100.0, 1) }
  $c1 = $c2; $t1 = $t2
  $line = (Get-Date -Format 'HH:mm:ss') + "," + [Math]::Round($p2.WorkingSet64 / 1MB) + "," + [Math]::Round($p2.PrivateMemorySize64 / 1MB) + "," + $cpuPct
  Add-Content $Out $line -Encoding ascii
  $n++
  if ($n -ge $MaxN) { break }
}