# Guía de instalación — Visum

Cómo dejar Visum andando en la computadora de la institución. No hace falta internet, ni
instalar Java, ni instalar una base de datos: viene todo en la carpeta.

> **Esta guía todavía no se probó en una máquina real.** Está escrita a partir de los scripts,
> que sí están verificados: las migraciones aplican sobre la base que viene en el paquete y la
> aplicación arranca con su configuración. La primera instalación va a ser, además, la prueba de
> esta guía. Si algo no coincide, anotalo: corregir la guía es parte del trabajo.

---

## 1. Qué hace falta

| | |
|---|---|
| **Sistema** | Windows 10 u 11, de 64 bits |
| **Permisos** | Administrador. Se registran dos servicios de Windows |
| **Espacio** | 1 GB libre para la instalación, más lo que crezca la base |
| **Cámara** | Una webcam USB, en la computadora donde se va a tomar asistencia |
| **Internet** | **No hace falta.** Ni para instalar ni para usar |

La computadora donde se instala es la que tiene la cámara: es donde los docentes van a pasar.
Desde otras computadoras de la institución se puede entrar a consultar y cargar, pero la
captura del rostro ocurre solo en los equipos autorizados.

**Antes de empezar, dos cosas que conviene mirar:**

- Que el **puerto 8080** esté libre. Si en esa computadora hay otro sistema web andando, puede
  estar ocupado. Más abajo está cómo cambiarlo.
- Si la máquina ya tiene **XAMPP o MySQL**, no hay problema: Visum trae su propia base y la
  hace escuchar en el puerto 3307, así que no se pisan.

---

## 2. Instalación

Son dos pasos: el instalador copia los archivos, y un segundo doble clic deja el sistema
andando. **Hacen falta los dos.**

### Paso 1 — Ejecutar `Visum-0.0.1.msi`

Doble clic. Windows va a pedir confirmación para instalar.

Cuando pregunte dónde instalarlo, la recomendación es:

```
C:\Visum
```

No lo dejes bajo *Archivos de programa*: ahí adentro va a vivir la base de datos con el
registro de asistencia, y esa carpeta tiene permisos que complican los respaldos.

Cuando termina, los archivos están en la máquina **pero el sistema todavía no funciona**: falta
crear la base y registrar los servicios. Eso es el paso 2.

### Paso 2 — Ejecutar `Instalar-Visum.bat`

Entrá a la carpeta donde lo instalaste, abrí la subcarpeta `instalador` y hacé **doble clic en
`Instalar-Visum.bat`**.

Windows va a pedir permiso de administrador: hay que dárselo, porque registra dos servicios.

**Qué vas a ver**, en este orden: inicializando la base, secretos, esquemas y usuario, servicio
de Visum, acceso directo. Al final, un resumen en verde con la dirección donde atiende.

La primera vez tarda un poco: tiene que crear la base desde cero.

### Si el puerto 8080 está ocupado

Abrí PowerShell **como administrador** y corré el script con otro puerto:

```powershell
cd C:\Visum\instalador
powershell -ExecutionPolicy Bypass -File .\instalar.ps1 -Puerto 8090
```

Y entonces la dirección pasa a ser `http://localhost:8090`.

### Sin el `.msi`

Si en vez del instalador tenés la carpeta `Visum` suelta, sirve igual: copiala a `C:\Visum` y
hacé el paso 2. Es exactamente lo mismo; el `.msi` solo se ocupa de la copia.

## 3. Primer arranque

Abrí el acceso directo **Visum** del escritorio. Se abre el navegador en `http://localhost:8080`.

### La configuración inicial

La primera vez —y solo la primera— aparece una pantalla que pide los datos de la institución y
de la cuenta con la que vas a entrar. No pide ningún código por correo: esta instalación no
manda correos.

- **Nombre de la institución**: el nombre completo y oficial. Es el que va a figurar en los
  reportes y en las constancias de datos biométricos.
- **Nombre corto o siglas**: es con lo que se inicia sesión.
- **Contraseña**: entre 6 y 20 caracteres, con minúscula, mayúscula y número.

### La clave de recuperación

Al terminar, la pantalla muestra una **clave de recuperación** de cuatro grupos de cinco
caracteres.

> **Se muestra una sola vez y no se puede volver a ver.** Es lo único que permite recuperar el
> acceso si se olvida la contraseña, porque esta instalación no envía correos.

Anotala o imprimila, y **guardala fuera de esta computadora**. Un papel en el mismo escritorio
donde está la máquina se pierde junto con la máquina.

Si se pierde, se puede generar una nueva desde **Mi institución**, siempre que todavía se pueda
entrar. Al generar una nueva, la anterior deja de servir.

---

## 4. Dejar lista la cámara

1. Entrá con la cuenta que creaste.
2. Andá a **Puestos de captura** y designá esta computadora como puesto. Ponele un nombre que
   se entienda: "Secretaría PC-1", "Entrada principal".
3. Elegí la cámara que va a usar. La lista de cámaras aparece recién después de darle permiso
   al navegador la primera vez.

Dos cosas para saber:

- **El puesto queda guardado en el navegador de esa computadora.** Si se borran los datos del
  sitio, hay que designarlo de nuevo.
- **Registrá los rostros con la misma cámara con la que después se va a reconocer.** Cambiarla
  puede empeorar el reconocimiento.

Si la institución tiene más de una entrada, se puede designar una computadora por entrada.

---

## 5. El día a día

- Los dos servicios **arrancan solos con Windows**. Nadie tiene que abrir nada para que el
  sistema tome asistencia.
- El acceso directo **Visum** solo abre la pantalla; no arranca la aplicación.
- Si la computadora se reinicia, el sistema vuelve solo.

---

## 6. Respaldo

Todo vive en esta computadora. Si se rompe o se la roban, se pierde el registro de asistencia
de la institución entera. **Es lo único que no se puede recuperar de ninguna manera.**

En **Mi institución → Respaldo**, el botón *Descargar una copia* baja un archivo con toda la
base.

- Hacelo seguido. Una vez por semana es razonable; antes de actualizar, siempre.
- **Guardá la copia en otro lado**: un pendrive, otra computadora, un disco externo.
- El archivo incluye los datos biométricos de los docentes. Están cifrados, pero son datos
  sensibles bajo la Ley 25.326: se guarda con el mismo cuidado que el resto del legajo.

---

## 7. Actualizar a una versión nueva

1. **Sacá un respaldo** (punto 6).
2. Copiá la carpeta `Visum` nueva encima de la instalada, **sin borrar** las carpetas `datos` ni
   `instalador`.
3. Volvé a correr el instalador, igual que la primera vez.

El instalador reconoce que ya hay una instalación: no toca la base y **no vuelve a generar los
secretos**. Eso último es importante — si la clave de cifrado cambiara, los rostros registrados
dejarían de poder leerse y habría que volver a tomárselos a todos.

La pantalla de configuración inicial tampoco vuelve a aparecer: ya hay una institución cargada.

---

## 8. Si algo no anda

| Qué pasa | Qué mirar |
|---|---|
| El acceso directo abre y dice que no se puede conectar | Mirá si los servicios están corriendo: `Get-Service VisumApp, VisumDB` |
| Un servicio está detenido | `Start-Service VisumDB` y después `Start-Service VisumApp`. Siempre en ese orden |
| Sigue sin levantar | `C:\Visum\logs\visum.log` dice qué pasó. El final del archivo es lo último que intentó |
| La cámara no aparece | El navegador tiene que tener permiso de cámara para `localhost`. Y la cámara tiene que estar enchufada antes de abrir la pantalla |
| Dice que esta computadora no es un puesto autorizado | Hay que designarla (punto 4). Si se borraron los datos del navegador, se perdió la designación |
| Alguien olvidó su contraseña | Si es la cuenta de la institución, se recupera con la clave del punto 3. Si es otra cuenta, ver *Límites conocidos* |

Para ver los servicios y los logs:

```powershell
Get-Service VisumApp, VisumDB
Get-Content C:\Visum\logs\visum.log -Tail 50
```

---

## 9. Desinstalar

**El orden importa.** Primero los servicios, después los archivos.

**1.** Abrí PowerShell como administrador y corré:

```powershell
cd C:\Visum\instalador
powershell -ExecutionPolicy Bypass -File .\desinstalar.ps1
```

**2.** Recién entonces, desinstalá *Visum* desde **Configuración → Aplicaciones**.

Al revés no: si borrás los archivos primero, los dos servicios quedan registrados apuntando a un
ejecutable que ya no existe, y hay que sacarlos a mano con `sc.exe delete`.

`desinstalar.ps1` **no borra la base ni los secretos**, a propósito: ahí está el registro de
asistencia. Si de verdad hay que borrarlos, sacá un respaldo primero y después borrá la carpeta
a mano.

---

## 10. Límites conocidos

Cosas que conviene saber antes de usarlo, no después:

- **Si una cuenta que no es la de la institución olvida su contraseña, no hay forma de
  recuperarla.** La clave de recuperación destraba únicamente la cuenta institucional. Para el
  resto, la salida es que la institución cree una cuenta nueva y dé de baja la anterior. Está
  anotado como TD-010.
- **La base que trae el paquete es MariaDB 10.4, que ya no recibe actualizaciones.** Es la única
  versión sobre la que el esquema del sistema corre hoy. El riesgo está acotado —la base solo
  escucha desde esta misma computadora, que no está expuesta a internet— pero está anotado como
  TD-011 y hay que resolverlo antes de mover el sistema a un servidor.
- **Una instalación atiende a una institución.** No se pueden cargar dos.
- **Todo depende de esta computadora.** Por eso el punto 6 no es opcional.
