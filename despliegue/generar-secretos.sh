#!/usr/bin/env bash
# Crea .env desde la plantilla con secretos al azar. Se corre una sola vez: si ya existe no lo
# toca, porque regenerar la clave biometrica dejaria ilegibles todos los modelos faciales.
set -euo pipefail
cd "$(dirname "$0")"

if [ -e .env ]; then
    echo "Ya existe .env y no se toca: cambiar BIOMETRIA_CLAVE deja ilegibles los modelos." >&2
    exit 1
fi

# Hexadecimal: no hay comillas ni simbolos que compose o el shell puedan interpretar.
sed -e "s/^DB_ROOT_PASSWORD=.*/DB_ROOT_PASSWORD=$(openssl rand -hex 24)/" \
    -e "s/^DB_PASSWORD=.*/DB_PASSWORD=$(openssl rand -hex 24)/" \
    -e "s/^BIOMETRIA_CLAVE=.*/BIOMETRIA_CLAVE=$(openssl rand -hex 32)/" \
    -e "s/^BIOMETRIA_SALT=.*/BIOMETRIA_SALT=$(openssl rand -hex 16)/" \
    .env.ejemplo > .env
chmod 600 .env

echo "Listo: .env creado. Falta completar a mano lo que dice COMPLETAR."
echo "Guarda BIOMETRIA_CLAVE y BIOMETRIA_SALT fuera de este servidor, y lejos de los respaldos."
