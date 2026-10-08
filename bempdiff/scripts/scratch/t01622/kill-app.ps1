Get-Process -Name BempDiff -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.Id -Force }
Start-Sleep -Seconds 2
$side = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'com\.bempdiff\.Main server' }
foreach ($s in $side) { Stop-Process -Id $s.ProcessId -Force; "killed sidecar $($s.ProcessId)" }
"remaining BempDiff procs: $((Get-Process -Name BempDiff -ErrorAction SilentlyContinue | Measure-Object).Count)"
