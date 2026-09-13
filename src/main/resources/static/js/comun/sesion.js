/* =============================================================================
 *  sesion.js
 *
 *  Que la sesión no se cierre sin aviso ni se lleve puesto lo que se estaba
 *  escribiendo.
 *
 *  La sesión vence tras un rato sin pedidos al servidor (server.servlet.session.
 *  timeout, 30 minutos). Antes eso pasaba en silencio: se completaba un
 *  formulario largo, se apretaba Guardar pasado ese tiempo, y la respuesta era
 *  el login, sin una palabra y con todo lo escrito perdido. Escribir no hace
 *  viajar nada, así que para el servidor era inactividad.
 *
 *  Tres cosas:
 *
 *   1. Mientras la persona trabaja en la pantalla --escribe, hace clic, se
 *      desplaza-- la sesión se renueva sola, a lo sumo una vez cada cinco
 *      minutos. Los pedidos que ya hace la pantalla (el pase manda un cuadro
 *      por segundo) también cuentan.
 *   2. Si de verdad no hay actividad, poco antes del cierre aparece un aviso con
 *      la cuenta regresiva y un botón para seguir conectado.
 *   3. Si igual se cierra, se dice, y los formularios dejan de enviarse:
 *      mandarlos llevaría al login y lo escrito se perdería sin aviso. En la
 *      pantalla sigue estando, y se puede copiar.
 *
 *  Las pestañas se enteran entre sí por localStorage: la sesión es una sola, así
 *  que la actividad en una la mantiene viva para todas.
 *
 *  El reloj de acá es una estimación: el servidor cuenta desde el último pedido
 *  que recibió, y esto cuenta desde el último que vio volver. La diferencia es de
 *  milisegundos, y para no anunciar un cierre que todavía no ocurrió, la sesión
 *  se da por cerrada unos segundos después de la hora calculada.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    var meta = document.querySelector('meta[name="sesion-segundos"]');
    var segundos = meta ? parseInt(meta.getAttribute('content'), 10) : 0;
    if (!(segundos > 0) || !window.fetch) return;

    var DURACION     = segundos * 1000;
    // Cuánto antes del cierre se avisa: dos minutos, o un cuarto de la sesión si fuera
    // más corta que ocho (solo pasa en pruebas).
    var AVISO_ANTES  = Math.min(2 * 60 * 1000, DURACION / 4);
    // Cada cuánto, como mucho, la actividad en pantalla renueva la sesión.
    var RENOVAR_CADA = Math.min(5 * 60 * 1000, DURACION / 3);
    var MARGEN       = 5000;
    var CLAVE        = 'sesion-actividad';

    var fetchOriginal = window.fetch;
    var ultima = 0;          // última vez que el servidor vio pasar un pedido de esta sesión
    var renovando = false;
    var cerrada = false;
    var aviso = null;

    registrar(Date.now());

    // ---- Lo que el servidor ya vio ----------------------------------------------

    function registrar(momento) {
        if (momento <= ultima) return;
        ultima = momento;
        try { localStorage.setItem(CLAVE, String(momento)); } catch (e) { /* modo privado */ }
    }

    function leerLasOtrasPestanas() {
        try {
            var momento = parseInt(localStorage.getItem(CLAVE), 10);
            if (momento > ultima) ultima = momento;
        } catch (e) { /* modo privado */ }
    }

    // Actividad en otra pestaña: la sesión es la misma, así que acá también cuenta.
    window.addEventListener('storage', function (ev) {
        if (ev.key !== CLAVE || !ev.newValue) return;
        var momento = parseInt(ev.newValue, 10);
        if (momento > ultima) { ultima = momento; revisar(); }
    });

    // Todo pedido que vuelve bien renueva la sesión. Sin esto, el aviso aparecería en
    // pleno pase de asistencia, que manda un cuadro por segundo.
    window.fetch = function () {
        return fetchOriginal.apply(this, arguments).then(function (resp) {
            if (resp.ok && !llevaAlLogin(resp) && esDeAca(resp.url)) registrar(Date.now());
            return resp;
        });
    };

    function esDeAca(url) {
        return !url || url.indexOf(window.location.origin + '/') === 0;
    }

    function llevaAlLogin(resp) {
        return resp.redirected && /\/login([?#]|$)/.test(resp.url);
    }

    // ---- Renovar ------------------------------------------------------------------

    function renovar() {
        if (renovando || cerrada) return;
        renovando = true;
        fetchOriginal.call(window, '/sesion/mantener', {
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { 'Accept': 'application/json' }
        }).then(function (resp) {
            if (resp.status === 401) { cerrar(); return; }
            if (resp.ok) { registrar(Date.now()); revisar(); }
        }).catch(function () {
            /* Sin red: lo reintenta la próxima actividad, o el botón del aviso. */
        }).then(function () {
            renovando = false;
        });
    }

    // Escribir un formulario es trabajar, aunque no viaje nada. Se renueva a lo sumo cada
    // RENOVAR_CADA: no puede costar un pedido por tecla.
    function actividad() {
        if (!cerrada && Date.now() - ultima >= RENOVAR_CADA) renovar();
    }
    ['keydown', 'pointerdown', 'input'].forEach(function (tipo) {
        document.addEventListener(tipo, actividad, { capture: true, passive: true });
    });
    window.addEventListener('scroll', actividad, { passive: true });

    // ---- Reloj --------------------------------------------------------------------

    function revisar() {
        if (cerrada) return;
        var restante = ultima + DURACION - Date.now();
        if (restante <= -MARGEN) { cerrar(); return; }
        if (restante <= AVISO_ANTES) mostrarAviso(Math.max(0, restante));
        else quitarAviso();
    }
    setInterval(revisar, 1000);

    // Una pestaña en segundo plano recibe el reloj con demora, y una que vuelve con Atrás
    // puede venir congelada de antes: en los dos casos se recalcula al volver a verla.
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden) { leerLasOtrasPestanas(); revisar(); }
    });
    window.addEventListener('pageshow', function () {
        leerLasOtrasPestanas();
        revisar();
    });

    // ---- Avisos -------------------------------------------------------------------

    function mostrarAviso(restante) {
        if (!aviso) {
            aviso = crearAviso('alert--warning',
                'Tu sesión se va a cerrar por inactividad.', 'Seguir conectado', renovar);
        }
        aviso.querySelector('.sesion-aviso__reloj').textContent = reloj(restante);
    }

    function quitarAviso() {
        if (aviso) { aviso.remove(); aviso = null; }
    }

    function cerrar() {
        if (cerrada) return;
        cerrada = true;
        quitarAviso();
        aviso = crearAviso('alert--error',
            'Se cerró tu sesión. Lo que tengas escrito sigue acá, pero ya no se puede guardar: '
            + 'copialo si lo necesitás y volvé a entrar.',
            'Volver a entrar', function () { window.location.href = '/login'; });
    }

    // Con la sesión cerrada, enviar lleva al login y lo escrito se pierde sin aviso: se frena
    // antes. En fase de captura y cortando el resto, para adelantarse al modal de
    // confirmación (que después envía con form.submit(), sin evento que atajar) y a
    // envio-form.js, que dejaría el botón bloqueado diciendo "Guardando...".
    document.addEventListener('submit', function (ev) {
        if (!cerrada) return;
        ev.preventDefault();
        ev.stopImmediatePropagation();
        if (aviso) aviso.querySelector('button').focus();
    }, true);

    // Se crea cada vez que aparece y se quita cuando no hace falta: un role="alert" recién
    // agregado es lo que el lector de pantalla anuncia. La cuenta regresiva va aparte y
    // oculta para él; si no, la leería segundo a segundo.
    function crearAviso(tipo, texto, accion, alHacer) {
        var caja = document.createElement('div');
        caja.className = 'sesion-aviso alert ' + tipo;
        caja.setAttribute('role', 'alert');

        var p = document.createElement('p');
        p.className = 'sesion-aviso__texto';
        p.textContent = texto;

        var cuenta = document.createElement('span');
        cuenta.className = 'sesion-aviso__reloj';
        cuenta.setAttribute('aria-hidden', 'true');

        var boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'btn btn--primary btn--sm';
        boton.textContent = accion;
        boton.addEventListener('click', alHacer);

        caja.appendChild(p);
        caja.appendChild(cuenta);
        caja.appendChild(boton);
        document.body.appendChild(caja);
        return caja;
    }

    function reloj(ms) {
        var s = Math.ceil(ms / 1000);
        return Math.floor(s / 60) + ':' + ('0' + (s % 60)).slice(-2);
    }
})(window, document);
