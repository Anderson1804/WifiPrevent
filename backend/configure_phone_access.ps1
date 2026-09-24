param(
    [ValidateSet('Add', 'Remove', 'Status')]
    [string]$Action = 'Add',
    [string]$PhoneIp
)

$ErrorActionPreference = 'Stop'
$apiRuleName = 'WiFiPrevent local API (private network)'
$socksRuleName = 'WiFiPrevent SOCKS5 for phone (private network)'
$udpRuleName = 'WiFiPrevent UDP relay for phone (private network)'
$pythonPath = Join-Path $PSScriptRoot '.venv\Scripts\python.exe'
$phoneAccessPath = Join-Path $PSScriptRoot '.local\phone-access.json'

if ($Action -eq 'Status') {
    $rules = Get-NetFirewallRule -DisplayName 'WiFiPrevent *' -ErrorAction SilentlyContinue
    if ($null -eq $rules) {
        Write-Host 'Las reglas de WiFiPrevent no están instaladas.'
    } else {
        $rules | ForEach-Object {
            Write-Host "Regla: $($_.DisplayName), activa: $($_.Enabled), perfil: $($_.Profile)"
        }
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

@($apiRuleName, $socksRuleName, $udpRuleName) | ForEach-Object {
    Get-NetFirewallRule -DisplayName $_ -ErrorAction SilentlyContinue |
        Remove-NetFirewallRule
}

if ($Action -eq 'Add') {
    $parsedIp = $null
    if (-not [Net.IPAddress]::TryParse($PhoneIp, [ref]$parsedIp)) {
        throw 'Indica la IP privada del teléfono con -PhoneIp, por ejemplo 192.168.18.50.'
    }
    if ($parsedIp.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) {
        throw 'La dirección del teléfono debe ser IPv4.'
    }
    New-NetFirewallRule `
        -DisplayName $apiRuleName `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort 8001 `
        -Profile Private `
        -Program $pythonPath | Out-Null
    New-NetFirewallRule `
        -DisplayName $socksRuleName `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort 1080 `
        -RemoteAddress $PhoneIp `
        -Profile Private `
        -Program $pythonPath | Out-Null
    New-NetFirewallRule `
        -DisplayName $udpRuleName `
        -Direction Inbound `
        -Action Allow `
        -Protocol UDP `
        -LocalPort 1081 `
        -RemoteAddress $PhoneIp `
        -Profile Private `
        -Program $pythonPath | Out-Null
    @{ phone_ip = $PhoneIp } | ConvertTo-Json |
        Set-Content -LiteralPath $phoneAccessPath -Encoding UTF8
    Write-Host "Acceso habilitado para ${PhoneIp}: API 8001/TCP, SOCKS5 1080/TCP y relé 1081/UDP."
    Write-Host 'Reinicia los servicios locales para aplicar la lista permitida del relé.'
} else {
    Remove-Item -LiteralPath $phoneAccessPath -ErrorAction SilentlyContinue
    Write-Host 'Regla de WiFiPrevent retirada.'
}
