#!/usr/bin/env bash
# Reemplaza la base por una copia de respaldar.sh. Borra lo que haya: pide confirmacion escrita.
#   ./restaurar.sh /var/backups/visum/visum-20270301-030000.sql.gz.enc --confirmar
#
# Si la copia es anterior a una supresion ARCO, el dato suprimido vuelve a estar: hay que repetir
# esas supresiones despues de restaurar. Por eso importa llevar la constancia de cada una.
set -euo pipefail
cd "$(dirname "$0")"

archivo="${1:-}"
CLAVE="${CLAVE_RESPALDO:-/root/.visum/clave-respaldo}"

if [ -z "$archivo" ] || [ ! -r "$archivo" ] || [ "${2:-}" != "--confirmar" ]; then
    echo "Uso: $0 <archivo.sql.gz.enc> --confirmar" >&2
    echo "Reemplaza la base entera por esa copia." >&2
    exit 1
fi

# Se comprueba antes de borrar nada: una clave equivocada o una copia rota no pueden costar la base.
openssl enc -d -aes-256-cbc -pbkdf2 -pass "file:$CLAVE" -in "$archivo" | gzip -t

# Con la aplicacion parada nadie escribe a mitad de la restauracion.
docker compose stop app
openssl enc -d -aes-256-cbc -pbkdf2 -pass "file:$CLAVE" -in "$archivo" \
    | gzip -d \
    | docker compose exec -T db sh -c \
        'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb -uroot --default-character-set=utf8mb4'

# La copia borra y recrea las dos bases. Los permisos del usuario quedan: viven en mysql.*, que la
# copia no toca.
docker compose start app
echo "Restaurado desde $archivo. La aplicacion esta arrancando."
