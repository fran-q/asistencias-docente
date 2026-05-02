/* =============================================================================
 *  table-scroll.js
 *  Convierte Shift + rueda del raton en scroll horizontal sobre los
 *  contenedores .table-wrap (cuando la tabla excede el ancho disponible).
 * ========================================================================== */

(function (window, document) {
    'use strict';

    function attach(wrap) {
        wrap.addEventListener('wheel', function (e) {
            if (!e.shiftKey) return;
            var canScrollHoriz = wrap.scrollWidth > wrap.clientWidth;
            if (!canScrollHoriz) return;
            e.preventDefault();
            wrap.scrollLeft += e.deltaY;
        }, { passive: false });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.table-wrap').forEach(attach);
    });

})(window, document);
