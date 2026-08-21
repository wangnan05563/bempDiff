# Silent background launcher for BempDiff.
#
# This file is intentionally ASCII-only: the target Chinese script name is built
# from Unicode code points instead of literal text, so the file decodes correctly
# regardless of BOM / console codepage.  This sidesteps the GBK corruption that
# occurs when a Chinese path is passed from cmd.exe to a new PowerShell process.
#
# It runs the target launcher (.bat) in a *hidden* console with the 'ghost'
# argument.  Inside that script, 'ghost' means "do the real background startup,
# then exit without holding a console window".

param([string]$Mode = 'start_service')

if ($Mode -eq 'desktop_shell') {
    # Forces the backend launcher into Electron-shell mode so it opens the
    # native window instead of a browser.
    $env:BEMPDIFF_SHELL = 'electron'
}

# name = 启动服务  (built from code points to keep this source ASCII-safe)
$svc = -join [char[]]@(0x542F, 0x52A8, 0x670D, 0x52A1)
$bat = Join-Path $PSScriptRoot ($svc + '.bat')

if (-not (Test-Path -LiteralPath $bat)) {
    Write-Output "launcher not found: $bat"
    exit 3
}

Start-Process -WindowStyle Hidden `
    -FilePath $env:ComSpec `
    -ArgumentList '/c','call',"`"$bat`"",'ghost' | Out-Null
exit 0