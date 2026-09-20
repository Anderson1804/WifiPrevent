param(
    [ValidateSet('Add', 'Remove', 'Status')]
    [string]$Action = 'Add'
)

$ErrorActionPreference = 'Stop'
$ruleName = 'WiFiPrevent local API (private network)'
$pythonPath = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'

if ($Action -eq 'Status') {
    $rule = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
    if ($null -eq $rule) {
        Write-Host 'La regla de WiFiPrevent no está instalada.'
    } else {
        Write-Host "Regla instalada: $($rule.Enabled), perfil: $($rule.Profile)"
    }
    exit 0
}

$currentIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($currentIdentity)
$isAdministrator = $principal.IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator
)

if (-not $isAdministrator) {
    throw 'Ejecuta PowerShell como administrador para modificar el firewall.'
}

Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue |
    Remove-NetFirewallRule

if ($Action -eq 'Add') {
    New-NetFirewallRule `
        -DisplayName $ruleName `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort 8001 `
        -Profile Private `
        -Program $pythonPath | Out-Null
    Write-Host 'Acceso del teléfono habilitado en redes privadas para el puerto 8001.'
} else {
    Write-Host 'Regla de WiFiPrevent retirada.'
}
