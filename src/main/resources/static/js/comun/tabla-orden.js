/* =============================================================================
 *  tabla-orden.js — Orden por columna en los listados
 *
 *  Ordena en el navegador, igual que filtro-tabla.js, y por el mismo motivo: en
 *  los catálogos (docentes, materias, comisiones, horarios) el listado completo
 *  ya viene en la página, así que ir al servidor por un orden distinto sería un
 *  viaje de ida y vuelta para reacomodar filas que ya están acá.
 *
 *  Los listados que SÍ paginan o filtran por servidor —Asistencias y Reportes—
 *  no deben usar este script: ordenar sólo la página visible da un orden que
 *  parece global y no lo es, que es peor que no ordenar.
 *
 *  Uso:
 *      <table class="table" data-ordenable>
 *        <thead><tr>
 *          <th data-orden="texto">DNI</th>
 *          <th data-orden="texto">Apellido, Nombre</th>
 *          <th data-orden="numero">Horas</th>
 *          <th data-orden="fecha">Alta</th>
 *          <th>Correo</th>                    <-- sin data-orden: no se ordena
 *        </tr></thead>
 *
 *  Tipos: texto (por defecto), numero, fecha (dd/MM/yyyy).
 *
 *  El valor que se compara sale del texto de la celda, salvo que la celda traiga
 *  data-valor: eso es lo que permite ordenar una columna de estados por su
 *  insignia ("Activo" / "Inactivo") o una de fechas por su ISO real sin cambiar
 *  lo que se muestra.
 * ========================================================================== */
(function () {
    'use strict';

    var tablas = document.querySelectorAll('table[data-ordenable]');
    if (!tablas.length) return;

    /* Interpreta el valor de una celda según el tipo declarado en el <th>. */
    function valor(celda, tipo) {
        var crudo = celda.getAttribute('data-valor');
        if (crudo === null) crudo = (celda.textContent || '').trim();

        if (tipo === 'numero') {
            /* Se queda con dígitos, signo y separador decimal: una celda que dice
               "94 / 120 min" ordena por 94, que es el dato de la columna. */
            var n = parseFloat(crudo.replace(/[^\d,.\-]/g, '').replace(',', '.'));
            return isNaN(n) ? -Infinity : n;
        }

        if (tipo === 'fecha') {
            /* dd/MM/yyyy — el formato en el que la app muestra todas sus fechas.
               Se convierte a yyyyMMdd, que ordena bien como número. */
            var m = crudo.match(/(\d{2})\/(\d{2})\/(\d{4})/);
            if (m) return parseInt(m[3] + m[2] + m[1], 10);
            var iso = Date.parse(crudo);
            return isNaN(iso) ? -Infinity : iso;
        }

        return crudo.toLowerCase();
    }

    function comparar(a, b, tipo) {
        if (tipo === 'numero' || tipo === 'fecha') return a - b;
        /* localeCompare con 'es' para que la ñ y los acentos caigan donde
           corresponde: con la comparación por defecto, Núñez quedaba después de
           Nuñoa y Ávila al final del listado. */
        return String(a).localeCompare(String(b), 'es', { numeric: true, sensitivity: 'base' });
    }

    Array.prototype.forEach.call(tablas, function (tabla) {
        var cuerpo = tabla.querySelector('tbody');
        if (!cuerpo) return;

        var encabezados = tabla.querySelectorAll('thead th[data-orden]');

        Array.prototype.forEach.call(encabezados, function (th, i) {
            /* El encabezado ordenable es interactivo, así que tiene que poder
               recibir foco y responder a Enter y a Espacio. No se convierte en
               <button> para no romper el layout de la tabla ni el sticky del
               thead: alcanza con darle el rol. */
            th.setAttribute('role', 'columnheader');
            th.setAttribute('tabindex', '0');
            th.setAttribute('aria-sort', 'none');

            function ordenar() {
                var tipo = th.getAttribute('data-orden') || 'texto';
                var asc  = th.getAttribute('aria-sort') !== 'ascending';
                var col  = Array.prototype.indexOf.call(th.parentNode.children, th);

                /* El estado de las otras columnas se limpia: dos columnas
                   marcadas a la vez prometen un orden compuesto que no existe. */
                Array.prototype.forEach.call(encabezados, function (otro) {
                    if (otro !== th) otro.setAttribute('aria-sort', 'none');
                });
                th.setAttribute('aria-sort', asc ? 'ascending' : 'descending');

                /* Las filas que no son datos —el estado vacío, el mensaje de "sin
                   resultados"— quedan afuera del orden y se reinsertan al final.
                   Sin esto, la fila de "ningún docente coincide" terminaba
                   ordenada entre los docentes. */
                var todas  = Array.prototype.slice.call(cuerpo.rows);
                var datos  = todas.filter(function (f) { return !f.querySelector('.table__empty'); });
                var otras  = todas.filter(function (f) { return  f.querySelector('.table__empty'); });

                datos.sort(function (f1, f2) {
                    var c1 = f1.cells[col], c2 = f2.cells[col];
                    if (!c1 || !c2) return 0;
                    var r = comparar(valor(c1, tipo), valor(c2, tipo), tipo);
                    return asc ? r : -r;
                });

                /* Un solo reflow: se arma el fragmento completo y se inserta de
                   una vez. Reinsertando fila por fila, una tabla de doscientas
                   filas parpadea. */
                var frag = document.createDocumentFragment();
                datos.forEach(function (f) { frag.appendChild(f); });
                otras.forEach(function (f) { frag.appendChild(f); });
                cuerpo.appendChild(frag);
            }

            th.addEventListener('click', ordenar);
            th.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); ordenar(); }
            });
        });
    });
})();
