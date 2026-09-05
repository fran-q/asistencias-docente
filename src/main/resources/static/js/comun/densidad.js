/* =============================================================================
 *  densidad.js — Alto de fila elegido por la persona
 *
 *  Tres niveles: compacta, equilibrada y amplia. Cambian --row-h, --cell-y,
 *  --card-pad y --gap, o sea el alto de las filas y también el aire de las
 *  tarjetas: si sólo cambiara el alto de fila, una tabla compacta dentro de una
 *  tarjeta amplia se veía desbalanceada.
 *
 *  Por qué es una preferencia y no una decisión de diseño. Las mismas pantallas
 *  se usan para dos cosas distintas: revisar treinta docentes de corrido —donde
 *  lo que importa es cuántas filas entran— y cargar o corregir uno —donde lo que
 *  importa es no equivocarse de fila. Un solo alto sirve bien para una de las
 *  dos.
 *
 *  Se guarda en localStorage y lo repone el script inline del layout, antes del
 *  primer paint: si se aplicara acá, la tabla se dibujaría con el alto por
 *  defecto y saltaría al elegido.
 *
 *  Uso:
 *      <div class="segmentado" role="group" aria-label="Densidad de la tabla">
 *        <button type="button" class="segmentado__opcion" data-densidad="compacta">...</button>
 *        <button type="button" class="segmentado__opcion" data-densidad="equilibrada">...</button>
 *        <button type="button" class="segmentado__opcion" data-densidad="amplia">...</button>
 *      </div>
 * ========================================================================== */
(function () {
    'use strict';

    var raiz     = document.documentElement;
    /* El selector va acotado a <button> a propósito. Con '[data-densidad]' pelado
       el primer resultado es el <html>, que lleva ese mismo atributo puesto por el
       script inline del layout: quedaba con un aria-pressed que no le corresponde a
       la raíz del documento, y con un listener que escribía en localStorage en cada
       clic de la aplicación. */
    var opciones = document.querySelectorAll('button[data-densidad]');
    if (!opciones.length) return;

    function marcar() {
        var actual = raiz.getAttribute('data-densidad') || 'equilibrada';
        Array.prototype.forEach.call(opciones, function (b) {
            /* aria-pressed y no una clase --activo: es un botón de dos estados,
               y el CSS engancha del mismo atributo que anuncia el lector de
               pantalla. Una sola fuente de verdad para las dos cosas. */
            b.setAttribute('aria-pressed', b.getAttribute('data-densidad') === actual ? 'true' : 'false');
        });
    }

    Array.prototype.forEach.call(opciones, function (b) {
        b.addEventListener('click', function () {
            var valor = b.getAttribute('data-densidad');
            raiz.setAttribute('data-densidad', valor);
            try { localStorage.setItem('densidad', valor); } catch (e) { /* modo privado */ }
            marcar();
        });
    });

    marcar();
})();
