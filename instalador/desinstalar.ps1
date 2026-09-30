<#
    desinstalar.ps1 - Saca los servicios de Visum de esta maquina (ADR-0022)

    QUE BORRA: los dos servicios y los accesos directos.

    QUE NO BORRA: la carpeta de datos ni los secretos. Es a proposito. Ahi vive el
    registro de asistencia de la institucion y la clave con la que se descifran los
    modelos faciales; un script de desinstalacion que se lleve eso por delante
    convierte un "lo saco de esta maquina" en una perdida de datos irreversible.

    Para borrarlos de verdad hay que hacerlo a mano, mirando lo que se borra. El
    script dice donde estan.

    Hay que correrlo como Administrador.
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$raizInstalador = Split-Path -Parent $MyInvocation.MyCommand.Path
$raiz           = Split-Path -Parent $raizInstalador
$datos          = Join-Path $raiz 'datos'
$servicioExe    = Join-Path $raizInstalador 'visum-servicio.exe'
$servicioXml    = Join-Path $raizInstalador 'visum-servicio.xml'
$mariadb        = Join-Path $raiz 'mariadb'

$NOMBRE_SERVICIO_BASE = 'VisumDB'
$NOMBRE_SERVICIO_APP  = 'VisumApp'

function EsAdministrador {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($id)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (EsAdministrador)) {
    throw 'Hay que correr este script como Administrador: da de baja servicios de Windows.'
}

Write-Host ""
Write-Host 'Visum - desinstalacion' -ForegroundColor Cyan

# --- La aplicacion ---
if (Get-Service -Name $NOMBRE_SERVICIO_APP -ErrorAction SilentlyContinue) {
    Write-Host '  Deteniendo y quitando el servicio de la aplicacion...'
    if (Test-Path $servicioExe) {
        & $servicioExe stop | Out-Null
        & $servicioExe uninstall | Out-Null
    } else {
        # Sin WinSW al lado, el ultimo recurso es sc.exe: el servicio igual tiene que irse.
        Stop-Service -Name $NOMBRE_SERVICIO_APP -Force -ErrorAction SilentlyContinue
        & sc.exe delete $NOMBRE_SERVICIO_APP | Out-Null
    }
} else {
    Write-Host '  El servicio de la aplicacion no estaba registrado.'
}

# --- La base ---
if (Get-Service -Name $NOMBRE_SERVICIO_BASE -ErrorAction SilentlyContinue) {
    Write-Host '  Deteniendo y quitando el servicio de la base...'
    Stop-Service -Name $NOMBRE_SERVICIO_BASE -Force -ErrorAction SilentlyContinue
    $mysqld = Join-Path $mariadb 'bin\mysqld.exe'
    if (Test-Path $mysqld) {
        & $mysqld --remove $NOMBRE_SERVICIO_BASE | Out-Null
    } else {
        & sc.exe delete $NOMBRE_SERVICIO_BASE | Out-Null
    }
} else {
    Write-Host '  El servicio de la base no estaba registrado.'
}

# --- Los accesos directos ---
Remove-Item -Path (Join-Path $env:PUBLIC 'Desktop\Visum.url') -ErrorAction SilentlyContinue
Remove-Item -Path (Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs\Visum.url') -ErrorAction SilentlyContinue
Write-Host '  Accesos directos quitados.'

Write-Host ""
Write-Host 'Listo. Lo que queda, a proposito:' -ForegroundColor Yellow
Write-Host "  Datos de la institucion   $datos"
Write-Host "  Secretos del servicio     $servicioXml"
Write-Host ""
Write-Host '  Ahi esta el registro de asistencia y la clave con la que se descifran los'
Write-Host '  modelos faciales. Si de verdad hay que borrarlos, hacelo a mano: sacar un'
Write-Host '  respaldo antes es la unica forma de volver atras.'
Write-Host ""
