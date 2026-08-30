# 调试 classpath 构建
$Lib = 'D:\code\otherProjects\18_comparePakage\bempdiff\dist_input\app\lib'
$Cfr = 'D:\code\otherProjects\18_comparePakage\bempdiff\dist_input\app\cfr.jar'
$CpJars = @($Cfr) + @(Get-ChildItem -Path $Lib -Filter *.jar | Select-Object -ExpandProperty FullName)
Write-Host "CpJars count: $($CpJars.Count)"
$CpJars | ForEach-Object { Write-Host "  $_" }
$Cp = $CpJars -join [IO.Path]::PathSeparator
Write-Host "Cp length: $($Cp.Length)"
Write-Host "Cp head: $($Cp.Substring(0, [Math]::Min(200, $Cp.Length)))"
