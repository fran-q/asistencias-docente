/* =============================================================================
 *  volver-al-kiosco.js
 *
 *  El login, cuando se llego desde el kiosco, vuelve solo a el si nadie lo usa.
 *
 *  El kiosco queda encendido en un mostrador y su marca lleva al login: es la puerta
 *  para quien tiene que operar el sistema en esa maquina. Pero la marca es lo mas
 *  grande de la barra y se toca sin querer, y un kiosco que quedo en el login deja de
 *  tomar asistencia hasta que alguien lo note, que puede ser al dia siguiente. Por
 *  eso, si pasa un minuto sin que nadie escriba ni toque nada, la pantalla vuelve al
 *  kiosco. Cualquier tecla o toque reinicia la cuenta: quien vino a ingresar no pierde
 *  lo que estaba escribiendo.
 *
 *  El enlace "Volver al kiosco" hace lo mismo en el momento. La plantilla trae el
 *  cuadro y este script solo si se llego con ?desde=kiosco (auth/login.html).
 * ========================================================================== */
(function (window, document) {
    'use strict';

    var cuadro = document.querySelector('[data-volver-al-kiosco]');
    if (!cuadro) return;

    var destino = cuadro.getAttribute('data-volver-al-kiosco');
    var TOTAL = parseInt(cuadro.getAttribute('data-segundos'), 10) || 60;
    var cuenta = cuadro.querySelector('[data-cuenta]');
    var quedan = TOTAL;
    var reloj = null;

    function pintar() {
        if (cuenta) cuenta.textContent = quedan;
    }

    function tic() {
        quedan -= 1;
        if (quedan <= 0) {
            window.clearInterval(reloj);
            // replace y no assign: el login no queda en el historial, y "atras" desde el
            // kiosco no vuelve a esta pantalla.
            window.location.replace(destino);
            return;
        }
        pintar();
    }

    // La cuenta vuelve a empezar junto con el reloj: si solo se repusieran los segundos, el
    // primero despues de una tecla podia durar un instante.
    function arrancar() {
        quedan = TOTAL;
        pintar();
        window.clearInterval(reloj);
        reloj = window.setInterval(tic, 1000);
    }

    // Cualquier señal de que hay alguien adelante reinicia la cuenta.
    ['keydown', 'input', 'pointerdown', 'focusin'].forEach(function (evento) {
        document.addEventListener(evento, arrancar, true);
    });

    arrancar();
})(window, document);
