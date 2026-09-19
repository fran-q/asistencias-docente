/* =============================================================================
 *  grilla-ahora.js
 *
 *  La marca de la hora actual en la grilla semanal: un guion punteado a lo ancho
 *  de los dias, a la altura de la hora que es.
 *
 *  Por que. La grilla es una plantilla de la semana --las mismas franjas todas
 *  las semanas-- y no dice en que momento de esa plantilla estamos parados. Con
 *  la marca se ve de un vistazo que esta corriendo ahora y que falta, sin leer
 *  una sola etiqueta de hora.
 *
 *  No dice nada a proposito: no lleva texto ni hora. Es una referencia visual, y
 *  la hora exacta ya esta en la barra de arriba. Por eso va con aria-hidden: a
 *  un lector de pantalla no le aporta nada que no pueda leer en otro lado.
 *
 *  Se ubica midiendo las etiquetas de hora que ya estan dibujadas, en vez de
 *  repetir aca las medidas de las filas del CSS: si manana la grilla cambia el
 *  alto de la fila o el intervalo, la marca lo sigue sin tocar este archivo.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    var grilla = document.querySelector('.grilla');
    if (!grilla) return;                          // sin horarios no se dibuja la grilla

    var linea = document.createElement('div');
    linea.className = 'grilla__ahora';
    linea.setAttribute('aria-hidden', 'true');
    linea.hidden = true;
    grilla.appendChild(linea);

    var etiquetas = grilla.querySelectorAll('.grilla__hour-label');
    var dias = grilla.querySelectorAll('.grilla__day-header');

    // La etiqueta de esa hora en punto, y la siguiente: entre las dos esta el alto
    // de una hora, gap incluido. La ultima no sirve como inicio --abajo no hay fila--,
    // asi que la hora del borde queda sin marca, igual que cualquier hora de afuera.
    function tramoDe(hora) {
        var buscada = (hora < 10 ? '0' : '') + hora + ':00';
        for (var i = 0; i < etiquetas.length - 1; i++) {
            if (etiquetas[i].textContent.trim() === buscada) {
                return { arriba: etiquetas[i].offsetTop,
                         alto: etiquetas[i + 1].offsetTop - etiquetas[i].offsetTop };
            }
        }
        return null;                              // fuera del rango que muestra la grilla
    }

    function ubicar() {
        var ahora = new Date();
        var tramo = tramoDe(ahora.getHours());
        if (!tramo || tramo.alto <= 0 || !dias.length) {
            linea.hidden = true;
            return;
        }
        var primero = dias[0];
        var ultimo = dias[dias.length - 1];
        linea.style.top = (tramo.arriba + (ahora.getMinutes() / 60) * tramo.alto) + 'px';
        linea.style.left = primero.offsetLeft + 'px';
        linea.style.width = (ultimo.offsetLeft + ultimo.offsetWidth - primero.offsetLeft) + 'px';
        linea.hidden = false;
    }

    ubicar();
    // Cada minuto, que es lo que puede moverse la marca. Al volver a la pestaña se
    // recalcula: con la pestaña oculta el navegador espacia los timers y la marca
    // volveria atrasada.
    window.setInterval(ubicar, 60000);
    window.addEventListener('resize', ubicar);
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden) ubicar();
    });

})(window, document);
