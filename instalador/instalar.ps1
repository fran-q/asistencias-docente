<#
    instalar.ps1 - Deja Visum andando en esta maquina (ADR-0022)

    Que hace, en orden:
      1. Inicializa la MariaDB que viene en el paquete, con su propia carpeta de datos
         y su propio puerto, y la registra como servicio.
      2. Crea la base del sistema, la del historial de Flyway y el usuario de la aplicacion.
      3. Genera los secretos de ESTA instalacion: la contrasena de la base y la clave con
         la que se cifran los modelos faciales.
      4. Registra Visum como servicio de Windows con WinSW.
      5. Deja un acceso directo que abre http://localhost:8080

    SE PUEDE VOLVER A CORRER. Es lo que pasa al instalar una version nueva encima de una
    que ya tiene datos, y por eso nada de lo de arriba se rehace si ya existe:

      - La carpeta de datos no se toca. Las migraciones nuevas las aplica Flyway al arrancar.
      - LOS SECRETOS NO SE REGENERAN. Si la clave de cifrado cambiara, los modelos faciales
        ya registrados no se podrian descifrar y habria que volver a tomarle el rostro a
        cada docente. Se leen del servicio anterior y se reusan tal cual.

    Hay que correrlo como Administrador: registra servicios.
#>

[CmdletBinding()]
param(
    [int]$Puerto = 8080,
    [int]$PuertoBase = 3307,
    [string]$UsuarioBase = 'visum'
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
#  Ubicaciones. Todo cuelga de la carpeta donde quedo instalada la aplicacion.
# ---------------------------------------------------------------------------
$raizInstalador = Split-Path -Parent $MyInvocation.MyCommand.Path
$raiz           = Split-Path -Parent $raizInstalador

$ejecutable   = Join-Path $raiz 'Visum.exe'
$mariadb      = Join-Path $raiz 'mariadb'
$datos        = Join-Path $raiz 'datos'
$logs         = Join-Path $raiz 'logs'
$servicioXml  = Join-Path $raizInstalador 'visum-servicio.xml'
$servicioExe  = Join-Path $raizInstalador 'visum-servicio.exe'
$winsw        = Join-Path $raizInstalador 'WinSW.exe'
$plantillaXml = Join-Path $raizInstalador 'visum-servicio.xml.plantilla'
$plantillaIni = Join-Path $raizInstalador 'my.ini.plantilla'
$rootFile     = Join-Path $raizInstalador 'base-root.txt'

$NOMBRE_SERVICIO_BASE = 'VisumDB'
$NOMBRE_SERVICIO_APP  = 'VisumApp'

# ---------------------------------------------------------------------------
#  Ayudas
# ---------------------------------------------------------------------------
function Escribir($texto)      { Write-Host "  $texto" }
function Titulo($texto)        { Write-Host ""; Write-Host $texto -ForegroundColor Cyan }
function Fallar($texto)        { throw $texto }

function EsAdministrador {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($id)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

# Al azar de verdad: Get-Random no sirve para un secreto, su generador es predecible.
function TextoAlAzar([int]$largo, [string]$alfabeto) {
    $bytes = New-Object 'System.Byte[]' $largo
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $sb = New-Object System.Text.StringBuilder
    foreach ($b in $bytes) {
        [void]$sb.Append($alfabeto[$b % $alfabeto.Length])
    }
    return $sb.ToString()
}

function ClaveAlAzar([int]$largo) {
    # Sin comillas, barras ni & : estos valores viajan adentro de un XML y de una linea de
    # comandos. Un caracter de mas rompe el archivo o el comando, y el error aparece lejos.
    return TextoAlAzar $largo 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789-_'
}

function HexAlAzar([int]$largo) {
    # El salt de la biometria tiene que ser hexadecimal: lo exige Spring Security Crypto.
    return TextoAlAzar $largo '0123456789abcdef'
}

# Lee un <env name="X" value="Y"/> del servicio anterior, para no regenerar secretos.
function ValorAnterior([string]$nombre) {
    if (-not (Test-Path $servicioXml)) { return $null }
    $xml = [xml](Get-Content $servicioXml -Raw)
    $nodo = $xml.service.env | Where-Object { $_.name -eq $nombre }
    if ($null -eq $nodo) { return $null }
    return $nodo.value
}

function ServicioExiste([string]$nombre) {
    $s = Get-Service -Name $nombre -ErrorAction SilentlyContinue
    return $null -ne $s
}

function DetenerSiCorre([string]$nombre) {
    if (ServicioExiste $nombre) {
        $s = Get-Service -Name $nombre
        if ($s.Status -ne 'Stopped') {
            Escribir "Deteniendo $nombre..."
            Stop-Service -Name $nombre -Force
            $s.WaitForStatus('Stopped', '00:01:00')
        }
    }
}

# ---------------------------------------------------------------------------
#  0. Lo que tiene que estar antes de empezar
# ---------------------------------------------------------------------------
Titulo 'Visum - instalacion'

if (-not (EsAdministrador)) {
    Fallar 'Hay que correr este script como Administrador: registra servicios de Windows.'
}
if (-not (Test-Path $ejecutable)) {
    Fallar "No se encontro Visum.exe en $raiz. Este script va adentro de la carpeta instalada."
}
if (-not (Test-Path (Join-Path $mariadb 'bin\mysqld.exe'))) {
    Fallar "Falta MariaDB en $mariadb. Ver instalador\README.md: la version portable se copia ahi antes de empaquetar."
}
if (-not (Test-Path $winsw)) {
    Fallar "Falta WinSW.exe en $raizInstalador. Ver instalador\README.md."
}

$primeraVez = -not (Test-Path $datos)
if ($primeraVez) {
    Escribir 'Instalacion nueva.'
} else {
    Escribir 'Ya hay una instalacion con datos: se conservan la base y los secretos.'
}

New-Item -ItemType Directory -Force -Path $logs | Out-Null

# Con la version vieja corriendo no se pueden reemplazar archivos ni tocar la base.
DetenerSiCorre $NOMBRE_SERVICIO_APP

# ---------------------------------------------------------------------------
#  1. La base: carpeta de datos, configuracion y servicio
# ---------------------------------------------------------------------------
Titulo '1. Base de datos'

$rootPassword = $null

if ($primeraVez) {
    $rootPassword = ClaveAlAzar 28

    Escribir 'Inicializando la base (esto tarda un momento)...'
    $instalarDb = Join-Path $mariadb 'bin\mysql_install_db.exe'
    & $instalarDb --datadir="$datos" --service="$NOMBRE_SERVICIO_BASE" --port=$PuertoBase --password="$rootPassword"
    if ($LASTEXITCODE -ne 0) { Fallar "mysql_install_db fallo con codigo $LASTEXITCODE" }

    # La contrasena de root no la usa la aplicacion --se conecta con la suya-- pero sin ella
    # no hay forma de entrar a la base a mano el dia que haga falta. Queda en un archivo que
    # solo pueden leer los Administradores.
    Set-Content -Path $rootFile -Value $rootPassword -Encoding UTF8
    icacls $rootFile /inheritance:r /grant:r "*S-1-5-32-544:(R,W)" /grant:r "*S-1-5-18:(R,W)" | Out-Null
    Escribir "Contrasena de root guardada en $rootFile"
} else {
    Escribir 'La carpeta de datos ya existe: no se reinicializa.'
    if (Test-Path $rootFile) { $rootPassword = (Get-Content $rootFile -Raw).Trim() }
}

# El my.ini va siempre: es donde viven el puerto y el bind-address, y una version nueva
# puede necesitar cambiarlos. No toca los datos.
$ini = Get-Content $plantillaIni -Raw
$ini = $ini.Replace('@@PUERTO_BASE@@', "$PuertoBase")
$ini = $ini.Replace('@@DATADIR@@', $datos.Replace('\', '/'))
$ini = $ini.Replace('@@BASEDIR@@', $mariadb.Replace('\', '/'))
Set-Content -Path (Join-Path $datos 'my.ini') -Value $ini -Encoding UTF8

if (-not (ServicioExiste $NOMBRE_SERVICIO_BASE)) {
    Escribir 'Registrando el servicio de la base...'
    & (Join-Path $mariadb 'bin\mysqld.exe') --install $NOMBRE_SERVICIO_BASE --defaults-file="$datos\my.ini"
    if ($LASTEXITCODE -ne 0) { Fallar "No se pudo registrar el servicio $NOMBRE_SERVICIO_BASE" }
}

Set-Service -Name $NOMBRE_SERVICIO_BASE -StartupType Automatic
if ((Get-Service -Name $NOMBRE_SERVICIO_BASE).Status -ne 'Running') {
    Escribir 'Arrancando la base...'
    Start-Service -Name $NOMBRE_SERVICIO_BASE
}
(Get-Service -Name $NOMBRE_SERVICIO_BASE).WaitForStatus('Running', '00:02:00')

# ---------------------------------------------------------------------------
#  2. Secretos de esta instalacion
# ---------------------------------------------------------------------------
Titulo '2. Secretos'

# Se leen los del servicio anterior ANTES de escribir el nuevo. Si esto se rehiciera en cada
# actualizacion, los modelos faciales ya guardados quedarian ilegibles.
$dbPassword      = ValorAnterior 'VISUM_DB_PASSWORD'
$biometriaClave  = ValorAnterior 'BIOMETRIA_CLAVE'
$biometriaSalt   = ValorAnterior 'BIOMETRIA_SALT'

if ([string]::IsNullOrWhiteSpace($dbPassword))     { $dbPassword = ClaveAlAzar 28 ; $dbEsNueva = $true }
if ([string]::IsNullOrWhiteSpace($biometriaClave)) { $biometriaClave = ClaveAlAzar 44 }
if ([string]::IsNullOrWhiteSpace($biometriaSalt))  { $biometriaSalt = HexAlAzar 16 }

if ($biometriaClave.Length -lt 32) {
    Fallar 'La clave de cifrado biometrico quedo demasiado corta. Abortado antes de tocar nada.'
}
Escribir 'Listos (los de una instalacion anterior se conservan).'

# ---------------------------------------------------------------------------
#  3. La base del sistema, la del historial y el usuario de la aplicacion
# ---------------------------------------------------------------------------
Titulo '3. Esquemas y usuario'

if ($primeraVez -or $dbEsNueva) {
    if ([string]::IsNullOrWhiteSpace($rootPassword)) {
        Fallar "Falta la contrasena de root ($rootFile) y hay que crear el usuario de la aplicacion."
    }
    $mysql = Join-Path $mariadb 'bin\mysql.exe'

    # Dos bases: la del sistema y la del historial de Flyway, que vive aparte para no
    # aparecer como una tabla mas entre las del modelo. Las dos hacen falta.
    $sql = @"
CREATE DATABASE IF NOT EXISTS asistenciautomatica CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS asistenciautomatica_meta CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '$UsuarioBase'@'127.0.0.1' IDENTIFIED BY '$dbPassword';
ALTER USER '$UsuarioBase'@'127.0.0.1' IDENTIFIED BY '$dbPassword';
GRANT ALL PRIVILEGES ON asistenciautomatica.* TO '$UsuarioBase'@'127.0.0.1';
GRANT ALL PRIVILEGES ON asistenciautomatica_meta.* TO '$UsuarioBase'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
    $env:MYSQL_PWD = $rootPassword
    try {
        $sql | & $mysql --user=root --host=127.0.0.1 --port=$PuertoBase
        if ($LASTEXITCODE -ne 0) { Fallar "No se pudieron crear las bases (codigo $LASTEXITCODE)" }
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
    Escribir 'Bases y usuario creados.'
} else {
    Escribir 'Ya existian: no se tocan.'
}

# ---------------------------------------------------------------------------
#  4. El servicio de la aplicacion
# ---------------------------------------------------------------------------
Titulo '4. Servicio de Visum'

$xml = Get-Content $plantillaXml -Raw
$xml = $xml.Replace('@@EJECUTABLE@@',      $ejecutable)
$xml = $xml.Replace('@@SERVICIO_BASE@@',   $NOMBRE_SERVICIO_BASE)
$xml = $xml.Replace('@@LOGS@@',            $logs)
$xml = $xml.Replace('@@LOG_APP@@',         (Join-Path $logs 'visum.log'))
$xml = $xml.Replace('@@PUERTO@@',          "$Puerto")
$xml = $xml.Replace('@@PUERTO_BASE@@',     "$PuertoBase")
$xml = $xml.Replace('@@DB_USUARIO@@',      $UsuarioBase)
$xml = $xml.Replace('@@DB_PASSWORD@@',     $dbPassword)
$xml = $xml.Replace('@@BIOMETRIA_CLAVE@@', $biometriaClave)
$xml = $xml.Replace('@@BIOMETRIA_SALT@@',  $biometriaSalt)
$xml = $xml.Replace('@@MYSQLDUMP@@',       (Join-Path $mariadb 'bin\mysqldump.exe'))
Set-Content -Path $servicioXml -Value $xml -Encoding UTF8

# Este archivo tiene la clave de cifrado biometrico adentro: sin herencia y solo para
# Administradores y para el sistema. Cualquiera que lo lea puede descifrar los rostros.
icacls $servicioXml /inheritance:r /grant:r "*S-1-5-32-544:(R,W)" /grant:r "*S-1-5-18:(R,W)" | Out-Null

# WinSW toma el XML que se llama igual que el ejecutable, asi que se lo copia con ese nombre.
Copy-Item -Path $winsw -Destination $servicioExe -Force

if (ServicioExiste $NOMBRE_SERVICIO_APP) {
    Escribir 'Actualizando el servicio existente...'
    & $servicioExe refresh
} else {
    Escribir 'Registrando el servicio...'
    & $servicioExe install
}
if ($LASTEXITCODE -ne 0) { Fallar "WinSW fallo con codigo $LASTEXITCODE" }

& $servicioExe start | Out-Null
Escribir 'Servicio arrancado.'

# ---------------------------------------------------------------------------
#  5. El acceso directo
# ---------------------------------------------------------------------------
Titulo '5. Acceso directo'

# Un .url y no un acceso directo al ejecutable: la aplicacion corre como servicio y ya esta
# andando. Lo que la persona necesita es abrir la pantalla, no arrancar un programa.
$contenidoUrl = "[InternetShortcut]`r`nURL=http://localhost:$Puerto`r`n"
$escritorio = Join-Path $env:PUBLIC 'Desktop\Visum.url'
$menu = Join-Path $env:ProgramData 'Microsoft\Windows\Start Menu\Programs\Visum.url'
Set-Content -Path $escritorio -Value $contenidoUrl -Encoding ASCII
Set-Content -Path $menu -Value $contenidoUrl -Encoding ASCII
Escribir 'Creado en el escritorio y en el menu inicio.'

# ---------------------------------------------------------------------------
#  Cierre
# ---------------------------------------------------------------------------
Titulo 'Listo'
Write-Host ""
Write-Host "  Visum atiende en   http://localhost:$Puerto" -ForegroundColor Green
Write-Host "  Base de datos      127.0.0.1:$PuertoBase (servicio $NOMBRE_SERVICIO_BASE)"
Write-Host "  Logs               $logs"
Write-Host ""
if ($primeraVez) {
    Write-Host "  Al abrirlo por primera vez va a pedir los datos de la institucion." -ForegroundColor Yellow
    Write-Host "  Ahi se crea la cuenta y se entrega la CLAVE DE RECUPERACION." -ForegroundColor Yellow
    Write-Host "  Esa clave se muestra una sola vez: guardala fuera de esta computadora." -ForegroundColor Yellow
    Write-Host ""
}
