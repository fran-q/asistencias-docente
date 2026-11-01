# Subir Visum a internet, paso a paso

Un servidor de DonWeb con Docker: la aplicación, MariaDB 11.4 y Caddy, que da el HTTPS. El dominio es
un `.com.ar`. El porqué de cada decisión está en ADR-0025 y ADR-0026.

> **Nunca pegues una contraseña, una clave ni el contenido de `.env` en un chat, un correo o el
> repositorio.** Guardalas en un gestor de contraseñas, como Bitwarden, que es gratis.

| Fase | Qué se hace | Cuánto tarda |
|---|---|---|
| [1](#1-sacar-el-dominio) | Sacar el dominio en NIC Argentina | 1 hora, más lo que tarde la clave fiscal |
| [2](#2-contratar-el-servidor) | Contratar el servidor en DonWeb | Hasta 48 h, según el medio de pago |
| [3](#3-apuntar-el-dominio) | Apuntar el dominio con Cloudflare | 15 minutos, más la propagación |
| [4](#4-configurar-el-correo) | Configurar el correo con Brevo | 30 minutos |
| [5](#5-preparar-el-servidor) | Preparar el servidor | 20 minutos |
| [6](#6-levantar-el-sistema) | Levantar el sistema | 15 minutos |
| [7](#7-crear-la-primera-institución) | Crear la primera institución | 10 minutos |
| [8](#8-probar-que-anda) | Probar que anda | 30 minutos |
| [9](#9-respaldos-y-monitoreo) | Respaldos y monitoreo | 30 minutos |

Las pantallas de DonWeb, Cloudflare y Brevo pueden cambiar de nombre. Si algo no coincide, buscá lo más
parecido.

---

## 1. Sacar el dominio

Necesitás CUIT o CUIL, y **clave fiscal**. Las fuentes dicen nivel 2 o nivel 3: si podés, tené el 3.

1. Buscá si el nombre está libre en [nic.ar](https://nic.ar).
2. Entrá con clave fiscal a [Trámites a Distancia](https://tramitesadistancia.gob.ar). Si te lo pide,
   adherí el servicio desde el sitio de ARCA.
3. Iniciá el trámite **"Operaciones sobre dominios"** y registrá el `.com.ar`.
4. Pagá con tarjeta o Rapipago. El arancel figura en [nic.ar](https://nic.ar/es/dominios/aranceles).
5. **Agendá el vencimiento:** dura un año. Si se vence, el sistema deja de responder.

Todavía no hace falta delegarlo: eso va en la fase 3.

## 2. Contratar el servidor

**Antes de pagar**, preguntale al soporte de DonWeb, por escrito, estas dos cosas, y guardá la respuesta:

- **¿El Cloud Server queda alojado en Argentina?** Su página dice que los nodos están acá, pero que la
  IP puede figurar en Estados Unidos.
- **¿Cuánto cuesta renovarlo** después del período promocional?

En el [configurador](https://donweb.com/es-ar/cloud-server-vps) elegí:

| Opción | Valor |
|---|---|
| vCPU | **2** |
| Memoria RAM | **4 GB**, con `MEMORIA_APP=2500m` y `MEMORIA_DB=700m` en `.env`. Se puede cambiar desde el panel cuando haga falta |
| Almacenamiento | **20 GB** |
| Copia de seguridad | Standard, la que viene incluida |
| Transferencia | 1 TB |
| Licencias | **Ninguna**: ni Windows, ni Ferozo, ni cPanel |
| Certificado SSL | Gratuito, o ninguno: lo saca Caddy |

Pagá con el medio que prefieras: Mercado Pago, Rapipago, Pago Fácil, transferencia o tarjeta. Al activarlo,
elegí **Ubuntu** en la versión más nueva que ofrezca, sin paneles y sin stack.

Anotá la **IP pública**, la **contraseña de root** y el **puerto SSH** que te da DonWeb. La IP la vas a usar a
cada rato; en esta guía figura como `IP_DEL_SERVIDOR`. Si el panel de DonWeb tiene un firewall propio, abrí ahí
los puertos 80 y 443, y el de SSH.

⚠ **Si el puerto SSH no es el 22,** agregá `-p PUERTO` a cada `ssh` de la guía, y en la fase 5 cambiá
`sudo ufw allow OpenSSH` por `sudo ufw allow PUERTO/tcp`. Con el puerto equivocado, el firewall te deja afuera.

## 3. Apuntar el dominio

NIC Argentina no administra registros DNS, así que se usa Cloudflare, gratis.

1. Creá una cuenta en [cloudflare.com](https://dash.cloudflare.com/sign-up), agregá tu dominio y elegí el
   plan **Free**.
2. En **DNS**, creá este registro:

   | Tipo | Nombre | Contenido | Proxy |
   |---|---|---|---|
   | A | `@` | `IP_DEL_SERVIDOR` | **DNS only, con la nube gris** |

   ⚠ **La nube tiene que quedar gris.** Con el proxy activo, las imágenes de los rostros pasarían por
   Cloudflare, y es un tercero más viendo datos biométricos. Si Cloudflare cargó otros registros solo,
   borralos.

3. Cloudflare te muestra dos servidores de nombres, del tipo `algo.ns.cloudflare.com`.
4. En Trámites a Distancia, buscá el dominio, elegí **Delegar** y cargá esos dos nombres.

Puede tardar de minutos a un día. Para saber si ya está, desde PowerShell, en tu PC:

```powershell
nslookup tudominio.com.ar 1.1.1.1
```

Tiene que responder con `IP_DEL_SERVIDOR`.

## 4. Configurar el correo

Sin correo no se puede crear una institución ni recuperar una contraseña.

1. Creá una cuenta gratis en [brevo.com](https://www.brevo.com).
2. En **Senders & Domains > Domains**, agregá tu dominio. Te da unos registros para copiar.
3. Cargalos en Cloudflare, en **DNS**, siempre con la nube gris. Son del tipo TXT o CNAME: DKIM,
   verificación y DMARC. Volvé a Brevo y apretá **Authenticate**.
4. En **SMTP & API > SMTP**, generá una **clave SMTP**. Anotá el servidor, el puerto y el login que muestra.
5. Elegí la dirección que manda los correos, como `no-responder@tudominio.com.ar`. No hace falta una casilla
   real.

Si Brevo pide activar los correos transaccionales, hacelo desde su soporte: en una cuenta nueva a veces no
vienen habilitados.

## 5. Preparar el servidor

**Conectate desde PowerShell, en tu PC.** Primero creá una llave, si no tenés una. Apretá Enter a todo, o
poné una frase para protegerla:

```powershell
ssh-keygen -t ed25519
```

Entrá al servidor con la contraseña de root:

```powershell
ssh root@IP_DEL_SERVIDOR
```

**Ya en el servidor**, actualizá y creá tu usuario:

```bash
apt update && apt upgrade -y
adduser visum
usermod -aG sudo visum
mkdir -p /home/visum/.ssh && chmod 700 /home/visum/.ssh
```

`adduser` te pide una contraseña: guardala, la vas a usar con `sudo`. Lo demás que pregunta se deja vacío.

**Copiá tu llave.** En otra ventana de PowerShell, en tu PC:

```powershell
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh root@IP_DEL_SERVIDOR "cat >> /home/visum/.ssh/authorized_keys && chown -R visum:visum /home/visum/.ssh && chmod 600 /home/visum/.ssh/authorized_keys"
```

**Probá que entrás con tu usuario, sin cerrar la otra ventana:**

```powershell
ssh visum@IP_DEL_SERVIDOR
```

⚠ **No sigas si esto no funciona:** el paso que viene cierra el acceso con contraseña y podrías quedar afuera.

**Si entró, cerrá el acceso con contraseña y por root, y prendé el firewall:**

```bash
sudo sed -i -E 's/^#?PasswordAuthentication.*/PasswordAuthentication no/; s/^#?PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo grep -rlE '^PasswordAuthentication yes' /etc/ssh/sshd_config.d/ | xargs -r sudo sed -i 's/^PasswordAuthentication yes/PasswordAuthentication no/'
sudo systemctl restart ssh
sudo ufw allow OpenSSH
sudo ufw allow 80,443/tcp
sudo ufw allow 443/udp
sudo ufw --force enable
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -f noninteractive unattended-upgrades
```

**Instalá Docker**, con el repositorio oficial:

```bash
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker visum
```

Cerrá la conexión y volvé a entrar, para que tome el permiso de Docker. Comprobalo:

```bash
docker compose version
```

## 6. Levantar el sistema

```bash
git clone https://github.com/fran-q/asistencias-docente.git visum
cd visum/despliegue
./generar-secretos.sh
nano .env
```

Completá lo que dice `COMPLETAR`:

| Variable | Qué va |
|---|---|
| `DOMINIO` | `tudominio.com.ar`, sin `https://` |
| `CORREO_ACME` | Tu correo: Let's Encrypt avisa ahí si hay un problema |
| `MAIL_HOST` | El servidor SMTP que muestra Brevo |
| `MAIL_PORT` | `587` |
| `MAIL_USER` | El login SMTP que muestra Brevo |
| `MAIL_PASS` | La clave SMTP de Brevo |
| `MAIL_FROM` | `no-responder@tudominio.com.ar` |

Con 4 GB, cambiá `MEMORIA_APP=2500m` y `MEMORIA_DB=700m`; los valores que trae son para 8 GB. Guardá con
`Ctrl+O`, Enter, y salí con `Ctrl+X`.

**Guardá `BIOMETRIA_CLAVE` y `BIOMETRIA_SALT` en tu gestor de contraseñas.** Para verlas:

```bash
grep BIOMETRIA .env
```

⚠ Si se pierden o se cambian, los modelos faciales quedan ilegibles y hay que registrar todos los rostros de
nuevo.

**Levantá todo.** La primera vez tarda varios minutos, porque compila el sistema:

```bash
docker compose up -d --build --wait
docker compose ps
```

Los tres servicios tienen que figurar `running`, con `app` y `db` en `healthy`. En el navegador, abrí
`https://tudominio.com.ar/actuator/health`: tiene que decir `{"status":"UP"}`, con el candado de HTTPS.

## 7. Crear la primera institución

El alta pública viene cerrada, así que se abre solo para esto:

```bash
sed -i 's/^ALTA_PUBLICA=.*/ALTA_PUBLICA=true/' .env && docker compose up -d --wait app
```

Entrá a `https://tudominio.com.ar/alta-institucion`, completá el formulario y confirmá con el código que
llega por correo. **Si no llega,** revisá spam y mirá el error:

```bash
docker compose logs --tail=50 app
```

Cuando puedas entrar, cerrala de nuevo:

```bash
sed -i 's/^ALTA_PUBLICA=.*/ALTA_PUBLICA=false/' .env && docker compose up -d --wait app
```

## 8. Probar que anda

Con datos de prueba:

- [ ] **Desde tu PC:** entrar, autorizar el equipo y abrir el pase. El navegador tiene que pedir la cámara.
- [ ] **Registrar un rostro:** un docente de prueba, con su consentimiento cargado.
- [ ] **Pase de asistencia:** que lo reconozca y registre la entrada.
- [ ] **Desde el celular, con datos móviles:** entrar y ver el listado del día, los reportes y los docentes.
      El pase tiene que estar bloqueado: la captura es solo para equipos autorizados.
- [ ] **Recuperar una contraseña:** que llegue el código.
- [ ] **Seis intentos con la contraseña mal:** que el último muestre el aviso de esperar. Se libera en 15 minutos.
- [ ] **`http://` sin la `s`:** que pase solo a `https://`.

Mientras no estén resueltos el consentimiento nuevo y lo que dice `contexto/07-pendientes.md`, rumbo 1, usá
**datos ficticios**. Un rostro de una persona real necesita siempre su consentimiento cargado, y el sistema no
deja registrarlo sin eso.

## 9. Respaldos y monitoreo

**Respaldo diario**, cifrado con una clave propia:

```bash
sudo mkdir -p /root/.visum
sudo sh -c 'openssl rand -hex 32 > /root/.visum/clave-respaldo && chmod 600 /root/.visum/clave-respaldo'
sudo cat /root/.visum/clave-respaldo
```

Guardá esa clave en tu gestor de contraseñas: sin ella, las copias no se pueden abrir. Después programalo:

```bash
sudo crontab -e
```

Agregá esta línea al final, y guardá:

```
0 4 * * * cd /home/visum/visum/despliegue && ./respaldar.sh >> /var/log/visum-respaldo.log 2>&1
```

Probalo una vez a mano, y comprobá que la copia se pueda restaurar:

```bash
sudo ./respaldar.sh
sudo ls -l /var/backups/visum
sudo ./restaurar.sh /var/backups/visum/ARCHIVO_QUE_APARECIO.sql.gz.enc --confirmar
```

**Copia afuera del servidor.** Una copia que vive en la misma máquina no sobrevive a perderla. Con una cuenta
gratis en Backblaze B2 o Cloudflare R2, instalá [rclone](https://rclone.org/install/), configuralo con
`sudo rclone config` y agregá `REMOTO_RCLONE=remoto:visum` antes de `./respaldar.sh` en la línea del cron.

**Monitoreo.** Con una cuenta gratis en un servicio como [UptimeRobot](https://uptimerobot.com), apuntá un
monitor a `https://tudominio.com.ar/actuator/health`. Te avisa por correo si se cae.

---

## Si algo falla

| Qué pasa | Qué mirar |
|---|---|
| El navegador no conecta | `nslookup tudominio.com.ar 1.1.1.1`: tiene que dar tu IP. Después, `docker compose ps` |
| Advertencia de certificado | Caddy no pudo sacarlo. `docker compose logs --tail=50 caddy`. Suele ser el DNS, o la nube naranja en Cloudflare |
| `app` no llega a `healthy` | `docker compose logs --tail=80 app`. Si dice "El sistema no arranca", ahí mismo indica qué variable falta |
| Falta memoria al compilar | `free -h`. Reintentá con `docker compose build app` |
| No llegan los correos | `docker compose logs --tail=50 app`. Revisá `MAIL_*` en `.env` y que el dominio figure autenticado en Brevo |
| "Hubo demasiados intentos" | Esperá 15 minutos, o reiniciá la aplicación: `docker compose restart app` |
| El navegador no pide la cámara | Tiene que ser `https://`, y el equipo tiene que estar autorizado |

## Actualizar

Se hace **fuera del horario de clases**: reiniciar cierra las sesiones y corta el pase.

```bash
cd ~/visum/despliegue
git pull
sudo ./respaldar.sh
docker compose up -d --build --wait
```

Las migraciones nuevas las aplica Flyway al arrancar. Los resultados se ven con
`docker compose logs --tail=100 app`.

## Lo que no hay que hacer

- **Regenerar `.env` o cambiar `BIOMETRIA_CLAVE`** en un sistema con datos.
- **`docker compose down -v`:** la `-v` borra el volumen de la base. Sin ella, `down` no borra datos.
- **Publicar el puerto de la base o de la aplicación.** Docker abre sus puertos por fuera de `ufw`. Sin el proxy
  adelante, la aplicación no sabe que llegó por HTTPS, y la base no tiene por qué verse desde afuera.
- **Levantar más de una instancia de la aplicación.** Las sesiones, los frenos y los modelos viven en memoria.

## Los archivos de esta carpeta

| Archivo | Qué es |
|---|---|
| `Dockerfile` | La imagen de la aplicación |
| `docker-compose.yml` | Los tres servicios y sus redes |
| `Caddyfile` | HTTPS y lo único que da a internet |
| `base-inicial.sql` | El esquema con el que nace la base. No se edita |
| `permisos.sql` | Los permisos del usuario de la aplicación |
| `.env.ejemplo` | Plantilla de la configuración |
| `generar-secretos.sh` | Crea `.env` con secretos al azar |
| `respaldar.sh` / `restaurar.sh` | Copia cifrada de la base, y volver a ella |
| `probar-imagen.sh` | Levanta todo y comprueba lo que promete. Lo corre el CI |
