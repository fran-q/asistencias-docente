/* =============================================================================
 *  filtro-tabla.js
 *  Filtro instantáneo para los listados de catálogo (docentes, usuarios,
 *  carreras, materias, comisiones, horarios).
 *
 *  ----------------------------------------------------------------------------
 *  Por qué acá y no en el servidor. Estos listados son catálogos de la
 *  institución: decenas de filas, no miles, y ya vienen enteras en la página.
 *  Filtrar en el navegador responde mientras se tipea y no necesita tocar
 *  seis controladores. Los filtros de Asistencias y Reportes SÍ van por
 *  servidor, porque ahí la tabla crece sin techo con el tiempo y no se puede
 *  traer completa.
 *
 *  ----------------------------------------------------------------------------
 *  Uso declarativo:
 *
 *  <div class="filtro" data-filtro-tabla="#tabla-docentes">
 *      <input type="search" class="filtro__texto" placeholder="Buscar...">
 *      <select class="filtro__estado">
 *          <option value="">Todos</option>
 *          <option value="activo">Activos</option>
 *          <option value="inactivo">Inactivos</option>
 *      </select>
 *      <span class="filtro__resultado"></span>
 *  </div>
 *
 *  Las filas filtrables se marcan con data-fila, y las que tengan estado con
 *  data-estado="activo|inactivo". El data-fila hace falta para no esconder la
 *  fila de "todavía no cargaste nada", que no es un resultado de la búsqueda.
 * ========================================================================== */

(function (document) {
    'use strict';

    // Saca acentos y pasa a minusculas: quien busca "garcia" tiene que encontrar
    // a "García" sin tener que acordarse de la tilde. NFD separa la letra de su
    // tilde, y \p{Diacritic} borra la tilde suelta sin escribirla en el archivo,
    // que es lo que evita que el regex dependa del encoding con que se guardo.
    var DIACRITICOS = /\p{Diacritic}/gu;

    function normalizar(texto) {
        return texto
            .toLowerCase()
            .normalize('NFD')
            .replace(DIACRITICOS, '');
    }

    function conectar(caja) {
        var tabla = document.querySelector(caja.dataset.filtroTabla);
        if (!tabla) return;

        var texto  = caja.querySelector('.filtro__texto');
        var estado = caja.querySelector('.filtro__estado');
        var salida = caja.querySelector('.filtro__resultado');
        var filas  = [].slice.call(tabla.querySelectorAll('tbody tr[data-fila]'));
        var vacia  = tabla.querySelector('tbody tr.table__empty-fila');

        // El texto de cada fila se calcula una sola vez: no cambia mientras se filtra.
        var textoDeFila = filas.map(function (f) { return normalizar(f.textContent); });

        function aplicar() {
            var buscado = texto ? normalizar(texto.value.trim()) : '';
            var estadoBuscado = estado ? estado.value : '';
            var visibles = 0;

            filas.forEach(function (fila, i) {
                var coincideTexto = !buscado || textoDeFila[i].indexOf(buscado) !== -1;
                var coincideEstado = !estadoBuscado || fila.dataset.estado === estadoBuscado;
                var mostrar = coincideTexto && coincideEstado;
                fila.hidden = !mostrar;
                if (mostrar) visibles++;
            });

            if (salida) {
                salida.textContent = visibles === filas.length
                    ? filas.length + (filas.length === 1 ? ' registro' : ' registros')
                    : visibles + ' de ' + filas.length;
            }

            // Aviso de que la busqueda no dio nada, distinto de "no hay nada cargado".
            if (vacia) vacia.hidden = visibles > 0 || filas.length === 0;
        }

        if (texto)  texto.addEventListener('input', aplicar);
        if (estado) estado.addEventListener('change', aplicar);
        aplicar();
    }

    document.addEventListener('DOMContentLoaded', function () {
        [].forEach.call(document.querySelectorAll('[data-filtro-tabla]'), conectar);
    });

})(document);
