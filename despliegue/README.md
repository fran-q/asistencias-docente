# Desplegar Visum en un servidor

Un solo servidor con Docker: la aplicación, MariaDB 11.4 y Caddy, que da el HTTPS. El porqué de cada
decisión está en ADR-0025 y ADR-0026.

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

## Qué hace falta

- **Un servidor Linux** con Ubuntu 24.04 y 8 GB de RAM, para 400 docentes. Con pocos docentes
  alcanza con 4 GB. x86-64: la imagen no está probada sobre ARM.
- **Un dominio**, con un registro A apuntando a la IP del servidor.
- **Un servicio de correo** con SMTP, y SPF y DKIM configurados en el dominio. Sin eso los códigos
  de verificación terminan en spam.

## Puesta en marcha

**1. Preparar el servidor.** Entrar por SSH con llave y cerrar la contraseña:

```bash
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo systemctl restart ssh
sudo ufw allow OpenSSH && sudo ufw allow 80,443/tcp && sudo ufw allow 443/udp && sudo ufw enable
sudo apt update && sudo apt install -y unattended-upgrades && sudo dpkg-reconfigure -plow unattended-upgrades
```

⚠ Docker abre sus puertos por fuera de `ufw`. Por eso `docker-compose.yml` publica solamente los de
Caddy, y no hay que agregarle `ports` a la base ni a la aplicación.

**2. Instalar Docker**, con [el método oficial para Ubuntu](https://docs.docker.com/engine/install/ubuntu/).

**3. Bajar el código y configurarlo.**

```bash
git clone https://github.com/fran-q/asistencias-docente.git visum
cd visum/despliegue
./generar-secretos.sh
nano .env
```

Completar lo que dice `COMPLETAR`. **Guardar `BIOMETRIA_CLAVE` y `BIOMETRIA_SALT` fuera del servidor,
en un gestor de contraseñas.** Si se pierden o se cambian, los modelos faciales quedan ilegibles y
hay que registrar todos los rostros de nuevo.

**4. Levantar.**

```bash
docker compose up -d --build --wait
docker compose ps
```

La primera vez tarda varios minutos: compila el sistema y crea la base. Después, entrar a
`https://el-dominio/actuator/health` tiene que decir `{"status":"UP"}`.

**5. Crear la primera institución.** El alta pública viene cerrada. Se abre un momento:

```bash
sed -i 's/^ALTA_PUBLICA=.*/ALTA_PUBLICA=true/' .env && docker compose up -d --wait app
```

Se da de alta en `https://el-dominio/alta-institucion`, confirmando con el código que llega por
correo. Después se cierra de la misma forma, con `false`.

**6. Programar los respaldos.**

```bash
sudo mkdir -p /root/.visum
sudo sh -c 'openssl rand -hex 32 > /root/.visum/clave-respaldo && chmod 600 /root/.visum/clave-respaldo'
sudo crontab -e
```

Y agregar una línea, con la ruta real:

```
0 4 * * * cd /ruta/a/visum/despliegue && REMOTO_RCLONE=remoto:visum ./respaldar.sh >> /var/log/visum-respaldo.log 2>&1
```

**Guardar una copia de `clave-respaldo` fuera del servidor**, y separada de la clave biométrica. Y
sacar las copias del servidor con [`rclone`](https://rclone.org): una que vive en la misma máquina
no sobrevive a perderla.

**7. Probar una restauración.** Un respaldo que nunca se restauró es una suposición:

```bash
sudo ./restaurar.sh /var/backups/visum/visum-FECHA.sql.gz.enc --confirmar
```

**8. Vigilarlo.** Apuntar un monitor externo a `https://el-dominio/actuator/health`.

## Antes de cargar un rostro real

Hasta acá se puede trabajar **solo con datos ficticios**. Antes de registrar el rostro de una persona
real tiene que estar resuelto lo que dice `contexto/07-pendientes.md`, rumbo 1: consentimiento nuevo,
política de privacidad, procedimiento ARCO y la transferencia internacional si corresponde.

## Actualizar

Se hace **fuera del horario de clases**: reiniciar cierra las sesiones y corta el pase.

```bash
git pull
./respaldar.sh
docker compose up -d --build --wait
```

Las migraciones nuevas las aplica Flyway al arrancar. Mirar el resultado con
`docker compose logs --tail=100 app`.

## Lo que no hay que hacer

- **Regenerar `.env` o cambiar `BIOMETRIA_CLAVE`** en un sistema con datos.
- **`docker compose down -v`**: la `-v` borra el volumen de la base. Sin ella, `down` no borra datos.
- **Publicar el puerto de la base o de la aplicación.** Sin el proxy adelante, la aplicación no sabe
  que llegó por HTTPS, y la base no tiene por qué verse desde afuera.
- **Levantar más de una instancia de la aplicación.** Las sesiones, los frenos y los modelos viven en
  memoria: el sistema escala hacia arriba, no hacia los costados.
