/**
 * Pantalla desatendida: cámara siempre encendida, sin controles y sin salida (ADR-0019).
 *
 * Es un archivo aparte de pase-asistencia.js y no una variante suya. Las dos pantallas
 * hacen lo mismo por dentro, pero lo que las separa es qué muestran: acá no hay nombre
 * completo, no hay clase, no hay botón para detener y no hay a dónde navegar. Compartir un
 * script obligaría a condicionar cada una de esas diferencias, y el día que alguien agregue
 * algo al pase se lo lleva también el kiosco sin querer.
 *
 * El recorte del nombre NO se hace acá: el servidor manda apellido y nada más (RF-87).
 */
(function () {
    'use strict';

    const video     = document.getElementById('k-video');
    const overlay   = document.getElementById('k-overlay');
    const canvas    = document.getElementById('k-canvas');
    const mensajeEl = document.getElementById('k-mensaje');
    const apellidoEl = document.getElementById('k-apellido');
    const detalleEl = document.getElementById('k-detalle');
    const botonEl   = document.getElementById('k-iniciar');

    // Un cuadro por segundo, igual que el pase. Es lo que el servidor espera y contra lo
    // que está dimensionado el tope de peticiones.
    const INTERVALO_MS = 1000;
    // Tras registrar, la pantalla se queda mostrando el resultado sin mandar cuadros. Más
    // largo que en el pase: acá nadie está para leerlo enseguida.
    const PAUSA_TRAS_MARCAR_MS = 5000;
    // Si el servidor frena por exceso de pedidos, se espera de más antes de reintentar.
    const ESPERA_TRAS_FRENO_MS = 10000;

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    let stream = null;
    let loopId = null;
    let pausaId = null;
    let enVuelo = false;

    // ------------------------------------------------------------------ arranque

    /**
     * Enciende la cámara.
     *
     * Un kiosco no debería necesitar que nadie apriete nada, así que se intenta solo. Pero
     * la primera vez el permiso lo tiene que dar una persona, y algunos navegadores exigen
     * un gesto: si falla, se muestra el botón en vez de quedar con la pantalla muerta y sin
     * explicación.
     */
    async function encender() {
        try {
            // La camara elegida para este puesto (V026), o la predeterminada.
            stream = await CamaraDelPuesto.abrir(video, { facingMode: 'user' });
            video.srcObject = stream;
            botonEl.hidden = true;
            mostrar('Acercate a la cámara', 'info');
            arrancarLoop();
        } catch (e) {
            botonEl.hidden = false;
            mostrar('Encendé la cámara para empezar', 'warn');
        }
    }

    botonEl.addEventListener('click', encender);
    encender();

    function arrancarLoop() {
        detenerLoop();
        loopId = setInterval(enviarCuadro, INTERVALO_MS);
    }

    function detenerLoop() {
        if (loopId) { clearInterval(loopId); loopId = null; }
    }

    /**
     * Frena el envío por un rato y lo reanuda solo.
     *
     * Nunca apaga la cámara: si se apagara, alguien tendría que volver a encenderla, y en
     * una pantalla desatendida eso significa que el sistema deja de tomar asistencia hasta
     * que pase un administrador.
     */
    function pausar(ms) {
        detenerLoop();
        if (pausaId) clearTimeout(pausaId);
        pausaId = setTimeout(function () {
            pausaId = null;
            limpiarResultado();
            mostrar('Acercate a la cámara', 'info');
            if (stream) arrancarLoop();
        }, ms);
    }

    // ------------------------------------------------------------------ envío

    async function enviarCuadro() {
        if (!stream || enVuelo) return;
        ajustarOverlay();
        canvas.width = video.videoWidth;
        canvas.height = video.videoHeight;
        canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);

        enVuelo = true;
        try {
            const headers = { 'Content-Type': 'application/json' };
            if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

            const res = await fetch('/kiosco/marcar', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ imagen: canvas.toDataURL('image/jpeg', 0.7) })
            });

            if (res.status === 429) {
                // El equipo está autorizado: pidió de más. Se espera y se sigue.
                mostrar('Esperá un momento…', 'warn');
                pausar(ESPERA_TRAS_FRENO_MS);
                return;
            }
            if (res.status === 403) {
                // La credencial del equipo dejó de servir: revocada o vencida. No hay nada
                // que el docente pueda hacer, así que se dice y se deja de insistir.
                detenerLoop();
                limpiarResultado();
                mostrar('Este equipo ya no está autorizado. Avisá en secretaría.', 'error');
                return;
            }
            if (!res.ok) {
                mostrar('No se pudo registrar. Reintentando…', 'warn');
                return;
            }
            pintar(await res.json());

        } catch (e) {
            mostrar('Sin conexión con el servidor. Reintentando…', 'warn');
        } finally {
            enVuelo = false;
        }
    }

    // ------------------------------------------------------------------ pintado

    function pintar(data) {
        if (!data.rostroDetectado) {
            limpiarRecuadro();
            limpiarResultado();
            mostrar(data.mensaje, 'info');
            return;
        }

        if (data.registrada) {
            const esSalida = data.tipoDeMarca === 'SALIDA';
            const color = esSalida ? color1('--primary') : color1('--success');
            recuadro(data, color, esSalida ? 'SALE' : 'ENTRA');
            apellidoEl.textContent = data.apellido || '';
            detalleEl.textContent = data.detalle || '';
            mostrar(data.mensaje, esSalida ? 'info' : 'success');
            // La pantalla se queda mostrando el resultado: es la única confirmación que el
            // docente va a recibir, porque no hay nadie a quien preguntarle.
            pausar(PAUSA_TRAS_MARCAR_MS);
            return;
        }

        if (data.confirmando) {
            // Sin apellido a propósito: mostrarlo antes de confirmar es lo que hace que
            // alguien vea el apellido equivocado durante un parpadeo.
            recuadro(data, color1('--warning'), null);
            limpiarResultado();
            mostrar(data.mensaje, 'info');
            return;
        }

        // Reconocido pero sin registrar —no hay clase, falta esperar—, o no reconocido.
        recuadro(data, color1('--warning'), null);
        apellidoEl.textContent = data.apellido || '';
        detalleEl.textContent = '';
        mostrar(data.mensaje, 'warn');
    }

    function recuadro(data, color, etiqueta) {
        if (data.x == null) { limpiarRecuadro(); return; }
        const ctx = overlay.getContext('2d');
        ctx.clearRect(0, 0, overlay.width, overlay.height);
        ctx.strokeStyle = color;
        ctx.lineWidth = 4;
        ctx.strokeRect(data.x, data.y, data.ancho, data.alto);
        if (etiqueta) {
            ctx.fillStyle = color;
            ctx.font = 'bold 22px system-ui, sans-serif';
            ctx.fillText(etiqueta, data.x, Math.max(24, data.y - 10));
        }
    }

    function limpiarRecuadro() {
        overlay.getContext('2d').clearRect(0, 0, overlay.width, overlay.height);
    }

    function limpiarResultado() {
        apellidoEl.textContent = '';
        detalleEl.textContent = '';
    }

    // El canvas del recuadro tiene que medir lo mismo que el video, o el rectangulo cae
    // corrido respecto de la cara.
    function ajustarOverlay() {
        if (overlay.width !== video.videoWidth || overlay.height !== video.videoHeight) {
            overlay.width = video.videoWidth;
            overlay.height = video.videoHeight;
        }
    }

    function mostrar(texto, tipo) {
        mensajeEl.textContent = texto || '';
        mensajeEl.className = 'kiosco__mensaje' + (tipo ? ' kiosco__mensaje--' + tipo : '');
    }

    // Los colores salen de los tokens de :root, no escritos a mano: un hex acá se ve bien
    // en oscuro y se rompe en claro. Al canvas no llegan las variables CSS, así que hay que
    // resolverlas antes.
    function color1(nombre) {
        return getComputedStyle(document.documentElement).getPropertyValue(nombre).trim()
            || '#888';
    }
})();
