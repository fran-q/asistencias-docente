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
 *      <select class="filtro__campo" data-filtro="carrera">
 *          <option value="">Todas las carreras</option>
 *      </select>
 *      <span class="filtro__resultado"></span>
 *  </div>
 *
 *  Las filas filtrables se marcan con data-fila, y las que tengan estado con
 *  data-estado="activo|inactivo". El data-fila hace falta para no esconder la
 *  fila de "todavía no cargaste nada", que no es un resultado de la búsqueda.
 *
 *  ----------------------------------------------------------------------------
 *  Filtros por columna. Un <select data-filtro="carrera"> compara su valor
 *  contra el data-carrera de cada fila, y se combina con la búsqueda y con los
 *  demás: la fila se muestra si cumple todos.
 *
 *  Sus opciones NO se escriben en la plantilla: las arma este script con los
 *  valores que traen las filas. Así el desplegable ofrece exactamente lo que
 *  hay en la tabla --sin carreras que no aparecen en ninguna fila, que solo
 *  llevan a una lista vacía--, y sumar el filtro a una pantalla no obliga a
 *  pasarle el catálogo entero desde el controlador. Si ningún valor aparece,
 *  el select se esconde: un filtro con una sola opción no filtra nada.
 *
 *  El orden de las opciones es alfabético salvo que las filas traigan
 *  data-<campo>-orden, que es lo que pone los días en el orden de la semana en
 *  vez de "Jueves, Lunes, Martes".
 * ========================================================================== */

(function (document) {
    'use strict';

    // Saca acentos y pasa a minusculas: quien busca "garcia" tiene que encontrar
    // a "García" sin tener que acordarse de la tilde. NFD separa la letra de su
    // tilde, y \p{Diacritic} borra la tilde suelta sin escribirla en el archivo,
    // que es lo que evita que el regex dependa del encoding con que se guardo.
    var DIACRITICOS = /\p{Diacritic}/gu;

    // Pasa a minusculas y saca las tildes, para que el texto tipeado y el de la tabla comparen igual.
    function normalizar(texto) {
        return texto
            .toLowerCase()
            .normalize('NFD')
            .replace(DIACRITICOS, '');
    }

    // Los selects que acotan por una columna. El de estado es el mismo mecanismo con el
    // nombre viejo: sus opciones son siempre las mismas dos y las escribe la plantilla.
    function camposDe(caja) {
        var campos = [];
        var estado = caja.querySelector('.filtro__estado');
        if (estado) campos.push({ select: estado, campo: 'estado', desdeLasFilas: false });
        [].forEach.call(caja.querySelectorAll('select[data-filtro]'), function (select) {
            campos.push({ select: select, campo: select.dataset.filtro, desdeLasFilas: true });
        });
        return campos;
    }

    // Ancho maximo del desplegable de un filtro, en pixeles. Los valores salen de las filas
    // --el nombre de una carrera, el de un docente-- y sin tope el control mide lo que el mas
    // largo y se lleva la barra entera.
    var ANCHO_MAXIMO = 240;

    // Llena el desplegable con los valores distintos que traen las filas.
    function llenarDesdeLasFilas(campo, filas) {
        var orden = Object.create(null);
        filas.forEach(function (fila) {
            var valor = fila.dataset[campo.campo];
            if (!valor || valor in orden) return;
            var clave = fila.dataset[campo.campo + 'Orden'];
            orden[valor] = clave === undefined ? null : Number(clave);
        });

        var valores = Object.keys(orden).sort(function (a, b) {
            if (orden[a] !== null && orden[b] !== null && orden[a] !== orden[b]) {
                return orden[a] - orden[b];
            }
            // numeric: "10° año" va despues de "2° año" y no antes.
            return a.localeCompare(b, 'es', { numeric: true });
        });

        campo.select.dataset.menuAnchoMax = String(ANCHO_MAXIMO);

        valores.forEach(function (valor) {
            var opcion = document.createElement('option');
            opcion.value = valor;
            opcion.textContent = valor;
            campo.select.appendChild(opcion);
        });

        if (!valores.length) {
            // Sin valores no hay nada que elegir. Se le saca el data-menu antes de que
            // select-menu.js lo lea: si no, dibujaria su desplegable propio al lado de un
            // select escondido. Este script corre primero, que es lo que lo hace posible.
            campo.select.hidden = true;
            campo.select.removeAttribute('data-menu');
        }
    }

    // Engancha una caja de filtro con su tabla y deja todo listo para filtrar.
    function conectar(caja) {
        var tabla = document.querySelector(caja.dataset.filtroTabla);
        if (!tabla) return;

        var texto  = caja.querySelector('.filtro__texto');
        var salida = caja.querySelector('.filtro__resultado');
        var filas  = [].slice.call(tabla.querySelectorAll('tbody tr[data-fila]'));
        var vacia  = tabla.querySelector('tbody tr.table__empty-fila');
        var campos = camposDe(caja);

        campos.forEach(function (campo) {
            if (campo.desdeLasFilas) llenarDesdeLasFilas(campo, filas);
        });

        // El texto de cada fila se calcula una sola vez: no cambia mientras se filtra.
        var textoDeFila = filas.map(function (f) { return normalizar(f.textContent); });

        // Esconde las filas que no coinciden y actualiza el contador de resultados.
        function aplicar() {
            var buscado = texto ? normalizar(texto.value.trim()) : '';
            var visibles = 0;

            filas.forEach(function (fila, i) {
                var coincideTexto = !buscado || textoDeFila[i].indexOf(buscado) !== -1;
                var coincidenCampos = campos.every(function (campo) {
                    return !campo.select.value || fila.dataset[campo.campo] === campo.select.value;
                });
                var mostrar = coincideTexto && coincidenCampos;
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

        if (texto) texto.addEventListener('input', aplicar);
        campos.forEach(function (campo) { campo.select.addEventListener('change', aplicar); });
        aplicar();
    }

    document.addEventListener('DOMContentLoaded', function () {
        [].forEach.call(document.querySelectorAll('[data-filtro-tabla]'), conectar);
    });

})(document);
