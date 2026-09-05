/* =============================================================================
 *  reloj.js — Hora del sistema en la barra superior
 *
 *  Es el dato que más se mira en una app de asistencia: si la clase empieza 8:00
 *  y el reloj no está a la vista, no hay forma de saber si una marca entró tarde
 *  sin salir de la pantalla.
 *
 *  Se actualiza al filo del minuto y no cada segundo: un setInterval de 1000 ms
 *  mantiene despierto el hilo principal todo el día para cambiar un dígito cada
 *  sesenta vueltas. El primer timeout se calcula contra los segundos que faltan,
 *  así el cambio de minuto coincide con el del reloj del sistema en vez de ir
 *  corrido unos segundos.
 * ========================================================================== */
(function () {
    'use strict';

    var caja = document.getElementById('reloj');
    if (!caja) return;

    function dosDigitos(n) { return (n < 10 ? '0' : '') + n; }

    function pintar() {
        var d = new Date();
        caja.textContent = dosDigitos(d.getHours()) + ':' + dosDigitos(d.getMinutes());
        /* datetime para que un lector de pantalla y cualquier script lean la hora
           en formato máquina, no el string ya formateado. */
        caja.setAttribute('datetime', d.toISOString());
    }

    function programar() {
        var d = new Date();
        var faltan = (60 - d.getSeconds()) * 1000 - d.getMilliseconds();
        setTimeout(function () { pintar(); programar(); }, faltan);
    }

    pintar();
    programar();

    /* Al volver de una pestaña en segundo plano el reloj puede estar viejo: los
       navegadores frenan los timers de las pestañas ocultas. */
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden) pintar();
    });
})();
