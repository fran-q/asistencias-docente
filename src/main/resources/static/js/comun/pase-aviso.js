/* =============================================================================
 *  pase-aviso.js
 *
 *  Lo que se ve del pase mientras anda en otra ventana: el punto del menu y el
 *  aviso de cada reconocimiento.
 *
 *  Por que. Con el pase en su propia ventana, quien administra sigue trabajando en
 *  el resto del sistema; el riesgo es el contrario al de antes: que nadie se entere
 *  de que hay una camara tomando asistencia, o de que dejo de tomarla. Entonces:
 *
 *    · un punto al lado de "Pase de asistencia" en el menu --verde mientras marca,
 *      rojo si la ventana esta abierta pero la camara no--;
 *    · mientras un docente sostiene la posicion, una barra con el avance, con el
 *      mismo estilo que los avisos del sistema;
 *    · al terminar, el resultado por aviso comun (toast.js).
 *
 *  Este archivo solo escucha. Lo que publica el pase esta en facial/pase-estado.js,
 *  que ademas ignora los mensajes de la propia ventana: la pantalla del pase ya
 *  muestra su estado con sus propios elementos.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    if (!window.PaseEstado) return;

    var punto  = document.querySelector('[data-punto-pase]');
    var enlace = punto ? punto.closest('a') : null;

    // Cuanto se sostiene la barra sin noticias antes de darla por terminada. El
    // reconocimiento manda un cuadro por segundo; si se corta sin resultado es porque el
    // docente se corrio de la camara, y la barra no puede quedar colgada.
    var SIN_NOTICIAS_MS = 2500;
    // Cada cuanto se revisa el estado guardado, para notar una ventana que se cerro de golpe.
    var REVISION_MS = 2000;

    var barra = null;
    var barraTimeout = null;

    // ---- El punto del menu -------------------------------------------------

    function pintarPunto(estado) {
        if (!punto) return;
        var hay     = !!estado && estado !== 'cerrado';
        var andando = estado === 'andando';

        punto.hidden = !hay;
        punto.classList.toggle('lateral__punto--andando', andando);
        punto.classList.toggle('lateral__punto--quieto', hay && !andando);
        // El punto solo no dice que significa: el titulo del enlace lo explica al pasar el
        // mouse, y el texto oculto lo deja disponible para un lector de pantalla.
        punto.textContent = !hay ? ''
            : andando ? 'Tomando asistencia en otra ventana'
                      : 'Abierto en otra ventana, sin tomar asistencia';
        if (enlace) enlace.title = punto.textContent;
    }

    function revisarEstado() {
        var dato = window.PaseEstado.leer();
        pintarPunto(dato ? dato.estado : null);
        if (!dato || dato.estado !== 'andando') quitarBarra();
    }

    // ---- La barra de reconocimiento ----------------------------------------

    // El mismo contenedor de toast.js: los avisos del pase y los del sistema se apilan
    // juntos, en vez de competir por la misma esquina desde dos lugares distintos.
    function contenedor() {
        var caja = document.getElementById('toast-container');
        if (!caja) {
            caja = document.createElement('div');
            caja.id = 'toast-container';
            caja.setAttribute('role', 'status');
            caja.setAttribute('aria-live', 'polite');
            document.body.appendChild(caja);
        }
        return caja;
    }

    function mostrarBarra(fraccion) {
        if (!barra) {
            barra = document.createElement('div');
            barra.className = 'toast toast--info toast--pase toast--in';
            barra.innerHTML =
                '<span class="toast__icon">●</span>'
                + '<span class="toast__msg">Reconociendo a alguien en el pase…'
                + '<span class="progreso"><span class="progreso__barra"></span></span></span>';
            contenedor().appendChild(barra);
        }
        var pct = Math.round(Math.max(0, Math.min(1, fraccion || 0)) * 100);
        barra.querySelector('.progreso__barra').style.width = pct + '%';

        clearTimeout(barraTimeout);
        barraTimeout = setTimeout(quitarBarra, SIN_NOTICIAS_MS);
    }

    function quitarBarra() {
        clearTimeout(barraTimeout);
        if (barra && barra.parentNode) barra.parentNode.removeChild(barra);
        barra = null;
    }

    // ---- Lo que llega de la ventana del pase -------------------------------

    window.PaseEstado.escuchar(function (msg) {
        if (msg.tipo === 'estado') {
            pintarPunto(msg.estado);
            if (msg.estado !== 'andando') quitarBarra();
            return;
        }
        if (msg.tipo === 'progreso') {
            mostrarBarra(msg.fraccion);
            return;
        }
        if (msg.tipo === 'resultado') {
            quitarBarra();
            if (window.Toast) {
                var texto = msg.detalle ? msg.mensaje + ' ' + msg.detalle : msg.mensaje;
                window.Toast.show(texto, msg.clase, 6000);
            }
        }
    });

    revisarEstado();
    setInterval(revisarEstado, REVISION_MS);
})(window, document);
