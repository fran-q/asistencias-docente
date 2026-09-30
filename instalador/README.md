# Instalador de Windows

Lo que convierte la aplicación empaquetada en algo que se instala y anda solo en la máquina de
la institución: sin internet, sin terminal y sin instalar Java ni MariaDB aparte (ADR-0022).

## Qué hay acá

| Archivo | Qué es |
|---|---|
| `instalar.ps1` | Inicializa la base, genera los secretos, registra los dos servicios y deja el acceso directo |
| `desinstalar.ps1` | Saca los servicios y los accesos directos. **No borra datos ni secretos**, a propósito |
| `visum-servicio.xml.plantilla` | Definición del servicio para WinSW. `instalar.ps1` la completa |
| `my.ini.plantilla` | Configuración de la MariaDB propia de Visum. `instalar.ps1` la completa |

## Lo que hay que conseguir aparte

Dos binarios de terceros que **no están en el repositorio**, por tamaño y porque incluirlos es
una decisión de distribución, no una dependencia de compilación. Van en `asistencias/vendor/`,
que está ignorado por git:

```
asistencias/vendor/
├── mariadb/        <- la MariaDB portable, descomprimida (tiene que quedar vendor/mariadb/bin/mysqld.exe)
└── WinSW.exe       <- el ejecutable de WinSW para .NET 4, renombrado asi
```

- **MariaDB**, versión ZIP para Windows x86_64, de mariadb.org. Es **GPLv2**: distribuirla junto
  a la aplicación es legítimo, y corresponde dejar constancia en el apartado de licencias, igual
  que se hizo con la elección de OpenPDF (RNF-18).
- **WinSW**, licencia MIT, de su página de releases en GitHub. Alcanza el ejecutable suelto.

Con eso puesto:

```
./gradlew armarInstalable
```

deja en `build/empaquetado/salida/Visum` la carpeta completa. Si falta alguno de los dos, la
tarea lo dice y arma igual lo que puede, pero el paquete **no se instala**.

## Cómo queda la máquina

```
Visum/
├── Visum.exe            la aplicación, con Java adentro
├── app/ runtime/        lo que arma jpackage
├── mariadb/             el motor, solo para Visum
├── datos/               la base. Acá vive el registro de asistencia
├── logs/                el servicio y la aplicación
└── instalador/          estos scripts, más los secretos generados
```

Dos servicios: `VisumDB` (la base) y `VisumApp` (la aplicación, que depende de la primera).
Arrancan con Windows. El acceso directo no lanza nada: abre `http://localhost:8080`.

## Lo que hay que saber antes de correrlo

**Se puede volver a correr, y hay que poder.** Es lo que pasa al instalar una versión nueva
encima de una que ya tiene datos. Por eso:

- La carpeta `datos/` no se toca. Las migraciones nuevas las aplica Flyway al arrancar.
- **Los secretos no se regeneran.** Se leen del servicio anterior y se reusan tal cual. Si la
  clave de cifrado cambiara, los modelos faciales ya registrados quedarían ilegibles y habría
  que volver a tomarle el rostro a cada docente.

**El archivo `visum-servicio.xml` que genera el script tiene secretos adentro**: la contraseña
de la base y la clave con la que se cifran los rostros. El script le deja permisos solo para
Administradores. No se copia a otra máquina, no se versiona, y no sirve de nada sin la base que
le corresponde.

**`base-root.txt`** guarda la contraseña de administración de la base. La aplicación no la usa
—se conecta con la suya—, pero sin ella no hay forma de entrar a la base a mano.

## Lo que todavía no está probado

Nada de esto se corrió en una máquina de verdad. Lo verificado hasta acá es que los scripts
parsean, que la aplicación empaquetada arranca con el perfil de instalación, y que falla donde
tiene que fallar cuando la base no está. La instalación completa —con su webcam— es la prueba
que falta.
