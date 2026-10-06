@echo off
rem ============================================================================
rem  Instalar-Visum.bat - El doble clic que deja Visum andando (ADR-0022)
rem
rem  Existe porque instalar.ps1 necesita dos cosas que nadie tiene por que saber:
rem  permisos de administrador y el permiso de Windows para correr scripts. Sin
rem  esto, el doble clic sobre el .ps1 abre el Bloc de notas, y correrlo a mano
rem  falla con un mensaje sobre politicas de ejecucion que no explica nada.
rem
rem  Que hace: si no se esta corriendo como administrador, se vuelve a lanzar a
rem  si mismo pidiendo permiso --el cartel de Windows-- y despues llama al script.
rem ============================================================================

setlocal
cd /d "%~dp0"

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo   Visum necesita permisos de administrador para registrar sus servicios.
    echo   Windows va a pedir confirmacion.
    echo.
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1"

echo.
echo   Presiona una tecla para cerrar esta ventana.
pause >nul
