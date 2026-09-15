/* =============================================================================
 *  ciclo-periodos.js
 *
 *  Las fechas de un ciclo lectivo y la division del anio en periodos.
 *
 *  1. Las fechas del ciclo caen dentro de su anio, que es lo que exige el servidor.
 *     Los campos de fecha toman ese limite como min y max: el calendario del
 *     navegador no ofrece dias de otro anio, y form-validacion.js lo avisa antes
 *     de enviar. Sirve en el alta y en "Datos del ciclo" (form[data-fechas-del-anio]).
 *
 *  2. En el alta, "Como se divide el anio": anual, dos cuatrimestres, anual y dos
 *     cuatrimestres, o tres trimestres arman las filas de periodos con sus nombres y
 *     reparten las fechas del ciclo en partes iguales; el Anual, si lo hay, lo cubre
 *     entero. Despues se ajustan a mano. En cuanto se toca una
 *     fila, o se agrega otra, la division pasa a "Personalizada" y ya no se
 *     recalcula sola: si no, el proximo cambio de fechas del ciclo pisaria el ajuste.
 *
 *  Antes habia que cargar a mano cada periodo, y el unico que venia sugerido
 *  ("Anual") llegaba sin fechas: habia que repetir las del ciclo.
 * ========================================================================== */
(function () {
    'use strict';

    var DIA = 24 * 60 * 60 * 1000;
    // Cada division: los periodos que reparten el ciclo en partes iguales y, si lleva, un
    // Anual que lo cubre entero. Con "Anual y dos cuatrimestres" los periodos se superponen a
    // proposito: una materia anual y una cuatrimestral conviven en el mismo ciclo.
    var ESTRUCTURAS = {
        '1': { partes: ['Anual'] },
        '2': { partes: ['1er cuatrimestre', '2do cuatrimestre'] },
        'anual+2': { anual: true, partes: ['1er cuatrimestre', '2do cuatrimestre'] },
        '3': { partes: ['1er trimestre', '2do trimestre', '3er trimestre'] }
    };

    // ---- 1. Fechas dentro del anio ------------------------------------------------

    Array.prototype.forEach.call(document.querySelectorAll('form[data-fechas-del-anio]'), function (form) {
        var anio = form.querySelector('[name="anio"]');
        var fechas = form.querySelectorAll('[name="fechaInicio"], [name="fechaFin"]');
        if (!anio) return;

        function acotar() {
            var a = parseInt(anio.value, 10);
            Array.prototype.forEach.call(fechas, function (f) {
                if (a >= 2000 && a <= 2200) {
                    f.min = a + '-01-01';
                    f.max = a + '-12-31';
                } else {
                    f.removeAttribute('min');
                    f.removeAttribute('max');
                }
            });
        }
        anio.addEventListener('input', acotar);
        acotar();
    });

    // ---- 2. Division del anio (solo en el alta) -----------------------------------

    var form = document.getElementById('form-ciclo');
    var estructura = document.getElementById('estructura');
    var contenedor = document.getElementById('periodos');
    var agregar = document.getElementById('agregar-periodo');
    if (!form || !estructura || !contenedor) return;

    var inicio = form.querySelector('[name="fechaInicio"]');
    var fin = form.querySelector('[name="fechaFin"]');
    // La primera fila es el molde: si cambian las clases o los names en la plantilla,
    // cambian en un solo lugar.
    var molde = contenedor.querySelector('.periodo-fila').cloneNode(true);

    function aDia(iso) {
        var p = iso.split('-');
        return Date.UTC(+p[0], +p[1] - 1, +p[2]);
    }
    function aIso(ms) {
        return new Date(ms).toISOString().slice(0, 10);
    }

    // Reparte [desde, hasta] en n tramos seguidos y sin huecos. Se trabaja en UTC para que
    // un cambio de horario no corra un dia; los dias que no se dividen justo van a los
    // ultimos tramos.
    function repartir(desde, hasta, n) {
        var base = aDia(desde);
        var dias = Math.round((aDia(hasta) - base) / DIA) + 1;
        var tramos = [];
        for (var i = 0; i < n; i++) {
            var primero = Math.floor(i * dias / n);
            var ultimo = Math.floor((i + 1) * dias / n) - 1;
            tramos.push([aIso(base + primero * DIA), aIso(base + ultimo * DIA)]);
        }
        return tramos;
    }

    // Una fila nueva a partir del molde, vacia y con sus id renumerados: con el id
    // repetido, tocar la etiqueta "Desde" de la tercera fila enfocaba el campo de la primera.
    function fila(i) {
        var f = molde.cloneNode(true);
        f.querySelectorAll('[id]').forEach(function (e) { e.id = e.id.replace(/-\d+$/, '-' + i); });
        f.querySelectorAll('label[for]').forEach(function (l) { l.htmlFor = l.htmlFor.replace(/-\d+$/, '-' + i); });
        f.querySelectorAll('input').forEach(function (e) { e.value = ''; });
        return f;
    }

    // Las fechas de cada periodo, dentro de las del ciclo.
    function acotarFilas() {
        contenedor.querySelectorAll('input[type="date"]').forEach(function (f) {
            if (inicio.value) f.min = inicio.value; else f.removeAttribute('min');
            if (fin.value) f.max = fin.value; else f.removeAttribute('max');
        });
    }

    function armar() {
        var e = ESTRUCTURAS[estructura.value];
        if (!e) return;                             // personalizada: se deja como esta
        var nombres = (e.anual ? ['Anual'] : []).concat(e.partes);
        var conFechas = inicio.value && fin.value && inicio.value <= fin.value;
        var tramos = !conFechas ? []
            : (e.anual ? [[inicio.value, fin.value]] : [])
                .concat(repartir(inicio.value, fin.value, e.partes.length));
        contenedor.innerHTML = '';
        nombres.forEach(function (nombre, i) {
            var f = fila(i);
            f.querySelector('[name="periodoNombre"]').value = nombre;
            if (conFechas) {
                f.querySelector('[name="periodoInicio"]').value = tramos[i][0];
                f.querySelector('[name="periodoFin"]').value = tramos[i][1];
            }
            contenedor.appendChild(f);
        });
        acotarFilas();
    }

    function aPersonalizada() {
        if (estructura.value === 'personalizada') return;
        estructura.value = 'personalizada';
        // select-menu.js escucha el change del select para repintar el desplegable.
        estructura.dispatchEvent(new Event('change', { bubbles: true }));
    }

    estructura.addEventListener('change', armar);
    [inicio, fin].forEach(function (campo) {
        campo.addEventListener('change', function () {
            acotarFilas();
            armar();
        });
    });
    contenedor.addEventListener('input', aPersonalizada);
    if (agregar) {
        agregar.addEventListener('click', function () {
            aPersonalizada();
            contenedor.appendChild(fila(contenedor.querySelectorAll('.periodo-fila').length));
            acotarFilas();
        });
    }

    armar();
})();
