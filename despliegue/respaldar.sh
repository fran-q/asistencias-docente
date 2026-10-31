#!/usr/bin/env bash
# Deja una copia cifrada de la base, y borra las que pasaron el plazo. Va en un cron diario.
#
# Variables, con su valor por defecto:
#   CLAVE_RESPALDO     archivo con la clave de estas copias      /root/.visum/clave-respaldo
#   DESTINO_RESPALDOS  carpeta donde quedan                      /var/backups/visum
#   DIAS_RESPALDO      cuantos dias se conservan                 30
#   REMOTO_RCLONE      si esta, tambien la sube (rclone remoto:carpeta)
#
# EL PLAZO NO ES SOLO ESPACIO. Una copia conserva los modelos de un docente que ejercio la supresion
# ARCO: el dato se termina de borrar recien cuando vence la ultima copia que lo tiene.
#
# La copia lleva los modelos faciales tal como estan en la base, cifrados con BIOMETRIA_CLAVE. Esa
# clave no va en la copia: guardarlas juntas alcanzaria para leerlo todo.
set -euo pipefail
cd "$(dirname "$0")"

CLAVE="${CLAVE_RESPALDO:-/root/.visum/clave-respaldo}"
DESTINO="${DESTINO_RESPALDOS:-/var/backups/visum}"
DIAS="${DIAS_RESPALDO:-30}"

if [ ! -r "$CLAVE" ]; then
    echo "No se puede leer la clave de los respaldos: $CLAVE" >&2
    echo "Se crea una vez, y se guarda una copia fuera de este servidor:" >&2
    echo "  mkdir -p $(dirname "$CLAVE") && openssl rand -hex 32 > $CLAVE && chmod 600 $CLAVE" >&2
    exit 1
fi

mkdir -p "$DESTINO"
chmod 700 "$DESTINO"
archivo="$DESTINO/visum-$(date +%Y%m%d-%H%M%S).sql.gz.enc"
temporal="$archivo.parcial"
trap 'rm -f "$temporal"' EXIT

# pipefail: si el volcado se corta a la mitad, el script falla y no queda una copia que parece buena.
docker compose exec -T db sh -c \
    'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb-dump -uroot --single-transaction --add-drop-database --default-character-set=utf8mb4 --databases asistenciautomatica asistenciautomatica_meta' \
    | gzip \
    | openssl enc -aes-256-cbc -pbkdf2 -salt -pass "file:$CLAVE" -out "$temporal"
mv "$temporal" "$archivo"
chmod 600 "$archivo"
echo "Respaldo: $archivo ($(du -h "$archivo" | cut -f1))"

find "$DESTINO" -name 'visum-*.sql.gz.enc' -mtime +"$DIAS" -delete

# Una copia que vive en el mismo servidor no sobrevive a perder el servidor.
if [ -n "${REMOTO_RCLONE:-}" ]; then
    rclone copy "$archivo" "$REMOTO_RCLONE"
    rclone delete --min-age "${DIAS}d" "$REMOTO_RCLONE"
fi
