/* =============================================================================
 *  fila-detalle.js — Desplegar la letra chica de una fila de tabla
 *
 *  Una tabla ancha no puede mostrar todo lo que tiene de cada fila sin volverse
 *  ilegible, pero lo que deja afuera no deja de existir: en el reporte, el
 *  motivo por el que una ausencia está justificada vivía sólo en el CSV, así que
 *  la pantalla valía menos que su propia descarga.
 *
 *  El botón despliega una SEGUNDA FILA debajo, que el servidor ya mandó armada y
 *  oculta. No es un cuadro flotante: el detalle pertenece a esa fila y se tiene
 *  que poder leer al lado de ella, no encima, y una tabla con el detalle abierto
 *  se imprime y se copia como se ve.
 *
 *  Uso:
 *      <button data-fila-detalle aria-controls="detalle-3" aria-expanded="false"
 *              data-abrir="Ver más" data-cerrar="Ver menos">Ver más</button>
 *      <tr id="detalle-3" hidden> ... </tr>
 *
 *  aria-expanded y no una clase: es un botón de dos estados, y el lector de
 *  pantalla y el CSS leen del mismo atributo. Los textos van en data-* y no acá
 *  porque son de la pantalla, no del mecanismo.
 *
 *  El listener va en el documento: estas filas salen de un th:each y pueden
 *  volver a dibujarse enteras sin que este script se entere.
 * ========================================================================== */
(function (document) {
    'use strict';

    document.addEventListener('click', function (ev) {
        var boton = ev.target.closest && ev.target.closest('[data-fila-detalle]');
        if (!boton) return;

        var detalle = document.getElementById(boton.getAttribute('aria-controls'));
        if (!detalle) return;

        var seAbre = detalle.hidden;
        detalle.hidden = !seAbre;
        boton.setAttribute('aria-expanded', seAbre ? 'true' : 'false');

        var texto = seAbre ? boton.dataset.cerrar : boton.dataset.abrir;
        if (texto) boton.textContent = texto;
    });
})(document);
