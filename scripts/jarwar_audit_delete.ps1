$root = "D:\code\otherProjects\18_comparePakage"
$ts = (Get-Date -Format "yyyyMMdd-HHmmss")
$manifest = Join-Path $root ("logs\jarwar-delete-manifest-" + $ts + ".log")
$files = @(
  "prototype\dist_input\app.jar",
  "prototype\dist_input\bootstrapfx-core-0.4.0.jar",
  "prototype\dist_input\ikonli-bootstrapicons-pack-12.3.1.jar",
  "prototype\dist_input\ikonli-core-12.3.1.jar",
  "prototype\dist_input\ikonli-javafx-12.3.1.jar",
  "prototype\dist_input\javafx-base-21-win.jar",
  "prototype\dist_input\javafx-controls-21-win.jar",
  "prototype\dist_input\javafx-fxml-21-win.jar",
  "prototype\dist_input\javafx-graphics-21-win.jar",
  "prototype\fixtures\big200_v1.jar",
  "prototype\fixtures\big200_v2.jar",
  "prototype\fixtures\big500_v1.jar",
  "prototype\fixtures\big500_v2.jar",
  "prototype\fixtures\small_real_v1.jar",
  "prototype\fixtures\small_real_v2.jar"
)
$sha = [System.Security.Cryptography.SHA256]::Create()
$ok = 0; $fail = 0
Add-Content -Path $manifest -Value ("# JAR/WAR audit delete manifest " + $ts)
Add-Content -Path $manifest -Value ("# total_candidates=" + $files.Count)
foreach ($rel in $files) {
    $p = Join-Path $root $rel
    if (-not (Test-Path $p)) { Add-Content -Path $manifest -Value ("MISSING`t" + $rel); continue }
    try {
        $bytes = [System.IO.File]::ReadAllBytes($p)
        $hash = ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
        $len = $bytes.Length
        [System.IO.File]::Delete($p)
        Add-Content -Path $manifest -Value ("DELETED`t" + $hash + "`t" + $len + "`t" + $rel)
        $ok++
    } catch {
        Add-Content -Path $manifest -Value ("FAIL`t" + $rel + "`t" + $_.Exception.Message)
        $fail++
    }
}
foreach ($d in @("prototype\dist_input","prototype\fixtures")) {
    $dp = Join-Path $root $d
    try { [System.IO.Directory]::Delete($dp); Add-Content -Path $manifest -Value ("DIR_REMOVED`t" + $d) }
    catch { Add-Content -Path $manifest -Value ("DIR_KEEP`t" + $d + "`t" + $_.Exception.Message) }
}
Add-Content -Path $manifest -Value ("# ok=" + $ok + " fail=" + $fail)
Add-Content -Path $manifest -Value ("# manifest_path=" + $manifest)
Write-Output ("DONE ok=" + $ok + " fail=" + $fail + " manifest=" + $manifest)
