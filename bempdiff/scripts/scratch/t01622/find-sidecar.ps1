Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'com\.bempdiff\.Main server' } | ForEach-Object {
  "{0} :: {1}" -f $_.ProcessId, $_.CommandLine
}
Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'BempDiff' } | ForEach-Object {
  "APP {0} :: {1}" -f $_.ProcessId, $_.Name
}
