/* =============================================================================
 *  table-scroll.js
 *  Dos cosas sobre los contenedores .table-wrap:
 *
 *   1. Shift + rueda del raton desplaza la tabla de costado.
 *   2. Se avisa con un degradado en el borde cuando la tabla sigue para ese lado.
 *
 *  Lo segundo existe porque el scroll horizontal no se anunciaba solo: en una
 *  ventana angosta la columna de acciones queda partida al medio y se lee como un
 *  error de maquetado. La barra de scroll no alcanza como aviso --es fina y en
 *  Windows aparece recien cuando uno ya la esta usando--, asi que el borde
 *  degradado es la unica senal que esta antes de que la persona intente nada.
 * ========================================================================== */

(function (window, document) {
    'use strict';

    // Margen de 1px: scrollWidth y clientWidth pueden diferir por redondeo de
    // subpixel aunque no haya nada que desplazar, y sin esto el degradado
    // quedaria encendido para siempre en tablas que entran justo.
    var MARGEN = 1;

    function marcarBordes(wrap) {
        var haciaIzq = wrap.scrollLeft > MARGEN;
        var haciaDer = wrap.scrollLeft + wrap.clientWidth < wrap.scrollWidth - MARGEN;
        wrap.classList.toggle('table-wrap--mas-izq', haciaIzq);
        wrap.classList.toggle('table-wrap--mas-der', haciaDer);
    }

    function attach(wrap) {
        wrap.addEventListener('wheel', function (e) {
            if (!e.shiftKey) return;
            var canScrollHoriz = wrap.scrollWidth > wrap.clientWidth;
            if (!canScrollHoriz) return;
            e.preventDefault();
            wrap.scrollLeft += e.deltaY;
        }, { passive: false });

        // passive: el handler solo lee y toggle clases, nunca llama a
        // preventDefault, asi que no tiene por que bloquear el scroll.
        wrap.addEventListener('scroll', function () { marcarBordes(wrap); }, { passive: true });

        // El ancho disponible cambia sin que haya resize de ventana: el filtro de
        // listados oculta filas, el drawer entra y sale. ResizeObserver lo agarra;
        // si no existe, queda el resize de window como respaldo.
        if (typeof ResizeObserver === 'function') {
            try {
                new ResizeObserver(function () { marcarBordes(wrap); }).observe(wrap);
            } catch (e) { /* navegador viejo */ }
        }

        marcarBordes(wrap);
    }

    document.addEventListener('DOMContentLoaded', function () {
        var wraps = [].slice.call(document.querySelectorAll('.table-wrap'));
        wraps.forEach(attach);

        window.addEventListener('resize', function () {
            wraps.forEach(marcarBordes);
        });
    });

})(window, document);
