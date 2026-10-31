#!/usr/bin/env bash
# Levanta el despliegue completo con valores de prueba y comprueba lo que promete. Lo corre el CI;
# en cualquier maquina con Docker hace lo mismo. Al terminar borra contenedores y volumenes.
set -uo pipefail
cd "$(dirname "$0")"

fallas=0
base=https://localhost

ok() { echo "  ok: $1"; }
# ::error:: queda como anotacion de la corrida, que se lee sin iniciar sesion en GitHub.
mal() { echo "::error title=Imagen::$1"; fallas=$((fallas + 1)); }
limpiar() { docker compose down -v --remove-orphans >/dev/null 2>&1; rm -f .env; rm -rf "$respaldos"; }

if [ -e .env ]; then
    echo "Ya hay un .env: esta prueba lo borraria. Correla en una copia." >&2
    exit 1
fi

respaldos="$(mktemp -d)"
trap limpiar EXIT

./generar-secretos.sh >/dev/null
sed -i -e 's/^DOMINIO=.*/DOMINIO=localhost/' -e 's/^CORREO_ACME=.*/CORREO_ACME=prueba@ejemplo.test/' \
       -e 's/^MAIL_HOST=.*/MAIL_HOST=localhost/' -e 's/^MAIL_USER=.*/MAIL_USER=prueba/' \
       -e 's/^MAIL_PASS=.*/MAIL_PASS=prueba/' -e 's/^MAIL_FROM=.*/MAIL_FROM=prueba@ejemplo.test/' .env

echo "== Construyendo y levantando"
if ! docker compose up -d --build --wait --wait-timeout 300; then
    mal "El despliegue no levanto. Ultimas lineas de la aplicacion: $(docker compose logs --tail=25 app 2>&1 | grep -aE 'ERROR|Exception|Caused|no arranca|UnsatisfiedLink' | tail -5 | cut -c1-400 | tr '\n' ' ')"
    docker compose ps -a
    docker compose logs --tail=60
    exit 1
fi
docker compose ps
docker images --format '{{.Repository}}:{{.Tag}} {{.Size}}' | grep -E 'despliegue|visum' || true

echo "== Por HTTPS, a traves de Caddy"
for i in $(seq 1 30); do
    codigo=$(curl -ks -o /dev/null -w '%{http_code}' "$base/login")
    [ "$codigo" = "200" ] && break
    sleep 2
done
[ "$codigo" = "200" ] && ok "login responde" || mal "/login respondio $codigo"

encabezados=$(curl -ks -D - -o /dev/null "$base/login" | tr -d '\r')
for buscado in 'strict-transport-security:' 'content-security-policy:' 'permissions-policy: camera=(self)'; do
    echo "$encabezados" | grep -qi "$buscado" && ok "$buscado" || mal "Falta el encabezado $buscado"
done
echo "$encabezados" | grep -i '^set-cookie: XSRF-TOKEN' | grep -q 'Secure' \
    && ok "cookie Secure: la aplicacion sabe que llego por HTTPS" \
    || mal "La cookie del token CSRF salio sin Secure: la aplicacion no ve el HTTPS del proxy"

salud=$(curl -ks "$base/actuator/health")
[ "$salud" = '{"status":"UP"}' ] && ok "salud UP" || mal "/actuator/health dio: $salud"

codigo=$(curl -ks -o /dev/null -w '%{http_code}' "$base/actuator/env")
[ "$codigo" = "404" ] && ok "actuator cerrado afuera" || mal "/actuator/env respondio $codigo"

codigo=$(curl -ks -o /dev/null -w '%{http_code}' "$base/alta-institucion")
[ "$codigo" != "200" ] && ok "alta publica cerrada ($codigo)" || mal "El alta publica esta abierta"

codigo=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost/login")
[ "$codigo" = "308" ] && ok "HTTP redirige a HTTPS" || mal "http://localhost/login respondio $codigo"

# 26 MB de un solo envio, contra un tope de 25.
codigo=$(head -c 27262976 /dev/zero | curl -ks -o /dev/null -w '%{http_code}' -X POST --data-binary @- "$base/login")
[ "$codigo" = "413" ] && ok "el proxy corta los envios gigantes" || mal "Un envio de 26 MB respondio $codigo"

echo "== Lo que no tiene que verse desde afuera"
for puerto in 3306 8080; do
    if (exec 3<>"/dev/tcp/127.0.0.1/$puerto") 2>/dev/null; then
        mal "El puerto $puerto esta abierto en el servidor"
    else
        ok "puerto $puerto cerrado"
    fi
done

echo "== Adentro del contenedor"
usuario=$(docker compose exec -T app id -u | tr -d '\r')
[ "$usuario" = "10001" ] && ok "la aplicacion no corre como root" || mal "La aplicacion corre con uid $usuario"

docker compose exec -T app sh -c 'grep -q " /tmp tmpfs " /proc/mounts' \
    && ok "/tmp en memoria" || mal "/tmp no esta en memoria: el modelo descifrado tocaria el disco"

hora=$(docker compose exec -T db date +%z | tr -d '\r')
[ "$hora" = "-0300" ] && ok "la base en hora de Ushuaia" || mal "La base esta en $hora"

docker compose logs app 2>&1 | grep -q 'Biblioteca de reconocimiento lista' \
    && ok "OpenCV y LBPH cargados" || mal "No se ve que el reconocedor haya cargado"

consulta() {
    docker compose exec -T db sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb -uroot -N -e "$1"' sh "$1" | tr -d '\r'
}

version=$(consulta "select max(version) from asistenciautomatica_meta.flyway_schema_history where success=1")
[ -n "$version" ] && ok "Flyway en la version $version" || mal "No hay historial de Flyway"

echo "== Respaldo y restauracion"
export CLAVE_RESPALDO="$respaldos/clave" DESTINO_RESPALDOS="$respaldos/copias"
openssl rand -hex 32 > "$CLAVE_RESPALDO"
consulta "insert into asistenciautomatica.instituciones (nombre, activo) values ('Testigo del respaldo', 1)"

if ./respaldar.sh; then
    copia=$(ls "$DESTINO_RESPALDOS"/*.enc | head -1)
    ok "respaldo generado"
    if zcat "$copia" >/dev/null 2>&1 || grep -qa 'Testigo del respaldo' "$copia"; then
        mal "La copia se puede leer sin la clave"
    else
        ok "la copia esta cifrada"
    fi
    consulta "delete from asistenciautomatica.instituciones where nombre = 'Testigo del respaldo'"

    if ./restaurar.sh "$copia" --confirmar; then
        vuelta=$(consulta "select count(*) from asistenciautomatica.instituciones where nombre = 'Testigo del respaldo'")
        [ "$vuelta" = "1" ] && ok "la restauracion trae los datos de vuelta" || mal "Despues de restaurar hay $vuelta filas testigo"
        docker compose up -d --wait --wait-timeout 180 \
            && ok "la aplicacion vuelve a levantar con la base restaurada" \
            || mal "La aplicacion no levanto despues de restaurar"
        salud=$(curl -ks "$base/actuator/health")
        [ "$salud" = '{"status":"UP"}' ] && ok "salud UP despues de restaurar" || mal "Salud despues de restaurar: $salud"
    else
        mal "restaurar.sh fallo"
    fi

    if ./restaurar.sh "$copia" >/dev/null 2>&1; then
        mal "restaurar.sh corrio sin --confirmar"
    else
        ok "restaurar pide confirmacion"
    fi
else
    mal "respaldar.sh fallo"
fi

echo "== Sin la clave biometrica no arranca"
if BIOMETRIA_CLAVE= docker compose config >/dev/null 2>&1; then
    mal "compose acepto una clave biometrica vacia"
else
    ok "compose exige la clave"
fi

if [ "$fallas" -gt 0 ]; then
    echo "Fallaron $fallas comprobaciones."
    docker compose logs --tail=40 app
    exit 1
fi
echo "Todo en orden."
