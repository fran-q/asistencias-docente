# Instalador de Windows

Lo que convierte la aplicación empaquetada en algo que se instala y anda solo en la máquina de
la institución: sin internet, sin terminal y sin instalar Java ni MariaDB aparte (ADR-0022).

## Qué hay acá

Este README es para **quien arma el paquete**. Para **quien lo instala en la institución** está
[GUIA-DE-INSTALACION.md](GUIA-DE-INSTALACION.md), que viaja adentro del paquete y se lee sin
saber nada de Gradle.

| Archivo | Qué es |
|---|---|
| `GUIA-DE-INSTALACION.md` | La guía paso a paso para la máquina de la institución |
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

- **MariaDB 10.4.34**, ZIP para Windows x86_64, de `archive.mariadb.org`. Es **GPLv2**:
  distribuirla junto a la aplicación es legítimo, y corresponde dejar constancia en el apartado
  de licencias, igual que se hizo con la elección de OpenPDF (RNF-18).

  **La versión no es libre de elegir, y conviene saber por qué.** El esquema no aplica sobre
  10.6 ni sobre 10.11: esas versiones rechazan que una columna esté a la vez en un `CHECK` y en
  una foránea con `ON DELETE SET NULL`, que es el caso de `asistencias.modelo_facial_id`. Está
  medido y documentado en **TD-011**. La 10.4 **no tiene soporte desde junio de 2024**: se usa
  porque es la única sobre la que el esquema corre, y mover el motor es trabajo de TD-011, no
  de este README.

- **WinSW 2.12.0**, licencia MIT, archivo `WinSW.NET4.exe` de su página de releases en GitHub,
  renombrado a `WinSW.exe`. Alcanza el ejecutable suelto (0,8 MB).

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

## Qué está verificado y qué no

**Verificado:**

- Las **18 migraciones aplican de cero** sobre la MariaDB que lleva el paquete (10.4.34), y el
  esquema que producen es el que esperan las entidades. Es `MigracionesIT` corriendo contra una
  instancia levantada desde `vendor/mariadb`, no contra el XAMPP de desarrollo.
- La aplicación empaquetada **arranca con el perfil de instalación** y falla donde corresponde
  cuando la base todavía no está.
- Los dos scripts **parsean** sin errores.

**Sin verificar:**

- La instalación de verdad: registrar los servicios necesita permisos de administrador y una
  máquina donde hacerlo.
- El reconocimiento facial con una webcam.

El paquete completo pesa unos **500 MB**: 223 de MariaDB, 149 del runtime de Java y 129 de la
aplicación.
