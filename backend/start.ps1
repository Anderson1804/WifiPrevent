$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/.venv/Scripts/python.exe" "$PSScriptRoot/local_services.py"
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron iniciar los servicios locales.' }
