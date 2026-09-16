/* =============================================================================
 *  pase-estado.js
 *
 *  El estado del pase, compartido entre las ventanas del mismo navegador.
 *
 *  Por que. El pase necesita la camara, y la camara se suelta apenas la pagina se
 *  recarga: ir a cualquier otra pantalla lo apagaba. Ahora el pase puede vivir en
 *  una ventana propia (/asistencia/pase/ventana) y el resto del sistema se usa
 *  normal en la ventana de siempre. Para que esa ventana no quede invisible,
 *  publica aca lo que va pasando y las demas lo muestran: el punto del menu y los
 *  avisos de cada reconocimiento (comun/pase-aviso.js).
 *
 *  Dos canales, a proposito:
 *    - BroadcastChannel lleva lo del momento (el avance y el resultado). No guarda
 *      nada: una pantalla que se abre despues no se entera de lo que ya paso.
 *    - localStorage guarda el ULTIMO estado, que es lo que necesita una pantalla
 *      recien abierta para saber si hay un pase andando.
 *
 *  El estado guardado se refresca cada pocos segundos. Si una ventana se cierra de
 *  golpe --se cuelga el navegador, se corta la luz-- lo ultimo que dejo escrito
 *  envejece y a los FRESCO_MS deja de contar: es preferible no mostrar nada antes
 *  que mostrar para siempre un pase que ya no existe.
 * ========================================================================== */
(function (window) {
    'use strict';

    var CLAVE = 'pase-estado';
    var CANAL = 'visum-pase';

    // Cuanto vale el ultimo estado guardado sin volver a confirmarse.
    var FRESCO_MS = 6000;
    var LATIDO_MS = 2000;

    // Cada ventana se nombra sola: es lo que permite ignorar los mensajes propios.
    var ID = Math.random().toString(36).slice(2) + Date.now().toString(36);

    var canal = null;
    try {
        canal = window.BroadcastChannel ? new window.BroadcastChannel(CANAL) : null;
    } catch (e) {
        canal = null;                       // navegador sin el canal: queda localStorage
    }

    var ultimoEstado = null;
    var latidoId = null;

    function guardar(mensaje) {
        try { localStorage.setItem(CLAVE, JSON.stringify(mensaje)); } catch (e) { /* modo privado */ }
    }

    function emitir(mensaje) {
        mensaje.id = ID;
        mensaje.ts = Date.now();
        if (canal) {
            try { canal.postMessage(mensaje); } catch (e) { /* la ventana se esta cerrando */ }
        }
        return mensaje;
    }

    /**
     * Publica en que anda esta ventana:
     *   'andando'  toma asistencia
     *   'quieto'   la camara esta prendida pero el pase esta detenido
     *   'apagado'  la ventana sigue abierta y la camara no
     *   'cerrado'  la ventana se va
     */
    function publicar(estado) {
        ultimoEstado = estado;
        guardar(emitir({ tipo: 'estado', estado: estado }));
        if (estado === 'cerrado') detenerLatido(); else arrancarLatido();
    }

    function arrancarLatido() {
        if (latidoId) return;
        latidoId = setInterval(function () {
            if (!ultimoEstado || ultimoEstado === 'cerrado') return;
            guardar({ tipo: 'estado', estado: ultimoEstado, id: ID, ts: Date.now() });
        }, LATIDO_MS);
    }

    function detenerLatido() {
        if (latidoId) { clearInterval(latidoId); latidoId = null; }
    }

    // Cuanto lleva sostenida la identidad mientras el servidor la confirma, entre 0 y 1.
    function progreso(fraccion) {
        emitir({ tipo: 'progreso', fraccion: fraccion });
    }

    // Como termino el reconocimiento: marco la entrada, marco la salida, no se lo
    // reconocio, no tenia clase. La clase es la del aviso: success, info, warning o error.
    function resultado(clase, mensaje, detalle) {
        emitir({ tipo: 'resultado', clase: clase, mensaje: mensaje, detalle: detalle || null });
    }

    // El ultimo estado conocido, de cualquier ventana; null si no hay ninguno o si envejecio.
    function leer() {
        var crudo;
        try { crudo = localStorage.getItem(CLAVE); } catch (e) { return null; }
        if (!crudo) return null;

        var dato;
        try { dato = JSON.parse(crudo); } catch (e) { return null; }
        if (!dato || !dato.estado || dato.estado === 'cerrado') return null;
        if (Date.now() - (dato.ts || 0) > FRESCO_MS) return null;
        return dato;
    }

    // Avisa de lo que publican las OTRAS ventanas. Los mensajes propios no se entregan:
    // la pantalla del pase ya muestra su estado con sus propios elementos.
    function escuchar(fn) {
        if (canal) {
            canal.addEventListener('message', function (ev) {
                if (ev.data && ev.data.id !== ID) fn(ev.data);
            });
        }
        // Sin BroadcastChannel el evento de localStorage alcanza para el estado, que es lo
        // que sostiene el punto del menu. El avance y el resultado se pierden, y es el orden
        // correcto de prioridades si hubiera que quedarse con una sola de las dos cosas.
        window.addEventListener('storage', function (ev) {
            if (ev.key !== CLAVE || !ev.newValue) return;
            try {
                var dato = JSON.parse(ev.newValue);
                if (dato && dato.id !== ID) fn(dato);
            } catch (e) { /* valor roto: se ignora */ }
        });
    }

    window.PaseEstado = {
        ID: ID,
        FRESCO_MS: FRESCO_MS,
        publicar: publicar,
        progreso: progreso,
        resultado: resultado,
        leer: leer,
        escuchar: escuchar
    };
})(window);
