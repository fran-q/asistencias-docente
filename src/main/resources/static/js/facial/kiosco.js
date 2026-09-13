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
    const progresoEl    = document.getElementById('k-progreso');
    const progresoBarra = progresoEl.querySelector('.progreso__barra');

    // Un cuadro por segundo, igual que el pase. Es lo que el servidor espera y contra lo
    // que está dimensionado el tope de peticiones.
    const INTERVALO_MS = 1000;
    // Tras registrar, la pantalla se queda mostrando el resultado sin mandar cuadros. Más
    // largo que en el pase: acá nadie está para leerlo enseguida. Eran 5 s y no alcanzaban
    // para leer el apellido y la hora de pie, a un metro de la pantalla.
    const PAUSA_TRAS_MARCAR_MS = 6000;
    // Cuánto se sostiene un resultado que hay que leer --un rechazo, un "no tenés clase
    // ahora"-- antes de que lo pise el "Acercate a la cámara" del cuadro siguiente. Antes
    // duraba un cuadro, un segundo: alcanzaba con correrse un paso para no enterarse del
    // motivo.
    const LECTURA_MINIMA_MS = 4000;
    // Si la cámara se corta, cada cuánto se intenta volver a abrirla. Acá no hay nadie para
    // apretar un botón: si no se reintenta sola, el kiosco deja de tomar asistencia hasta que
    // pase alguien.
    const REINTENTO_CAMARA_MS = 10000;
    // Si el servidor frena por exceso de pedidos, se espera de más antes de reintentar.
    const ESPERA_TRAS_FRENO_MS = 10000;

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    let stream = null;
    let loopId = null;
    let pausaId = null;
    let enVuelo = false;
    let dejarDeVigilar = null;
    let camaraSinImagen = false;
    let fijoHasta = 0;
    let reintentoId = null;

    // ------------------------------------------------------------------ arranque

    /**
     * Enciende la cámara.
     *
     * Un kiosco no debería necesitar que nadie apriete nada, así que se intenta solo. Pero
     * la primera vez el permiso lo tiene que dar una persona, y algunos navegadores exigen
     * un gesto: si falla, se muestra el botón en vez de quedar con la pantalla muerta y sin
     * explicación.
     */
    async function encender(esReintento) {
        if (reintentoId) { clearTimeout(reintentoId); reintentoId = null; }
        try {
            // La camara elegida para este puesto (V026), o la predeterminada.
            stream = await CamaraDelPuesto.abrir(video, { facingMode: 'user' });
            video.srcObject = stream;
            camaraSinImagen = false;
            dejarDeVigilar = CamaraDelPuesto.vigilar(stream, {
                perdida: camaraPerdida,
                sinImagen: camaraSinImagenAviso,
                volvio: camaraVolvio
            });
            botonEl.hidden = true;
            mostrar('Acercate a la cámara', 'info');
            arrancarLoop();
        } catch (e) {
            // Tras un corte, la cámara puede seguir desenchufada: se sigue intentando sola.
            // Salvo que falte el permiso, que solo lo puede dar una persona.
            if (esReintento && !(e && e.name === 'NotAllowedError')) {
                programarReintento();
                return;
            }
            botonEl.hidden = false;
            mostrar('Encendé la cámara para empezar', 'warn');
        }
    }

    botonEl.addEventListener('click', function () { encender(false); });
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
            ocultarProgreso();
            mostrar('Acercate a la cámara', 'info');
            if (stream) arrancarLoop();
        }, ms);
    }

    // ------------------------------------------------------------------ envío

    async function enviarCuadro() {
        if (!stream || enVuelo || camaraSinImagen) return;
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

    // ------------------------------------------------------------------ progreso

    // La barra muestra el dato del servidor --cuánto lleva sostenida la identidad y cuánto
    // hace falta--, no una animación de relleno: si la persona se corre, la barra baja o se
    // va. Al registrar se llena de golpe y queda verde durante la pausa.
    function mostrarProgreso(fraccion, listo) {
        const pct = Math.round(Math.max(0, Math.min(1, fraccion || 0)) * 100);
        progresoEl.hidden = false;
        progresoEl.classList.toggle('progreso--ok', !!listo);
        progresoBarra.style.width = pct + '%';
        progresoEl.setAttribute('aria-valuenow', String(pct));
    }

    function ocultarProgreso() {
        progresoEl.hidden = true;
        progresoEl.classList.remove('progreso--ok');
        progresoBarra.style.width = '0%';
        progresoEl.setAttribute('aria-valuenow', '0');
    }

    // ------------------------------------------------------------------ la cámara se corta

    // Se desenchufó o se la llevó otra aplicación. Se dice y se reintenta sola: en un kiosco
    // no hay nadie para apretar "Encender".
    function camaraPerdida() {
        dejarDeVigilar = null;
        detenerLoop();
        if (pausaId) { clearTimeout(pausaId); pausaId = null; }
        if (stream) {
            stream.getTracks().forEach(function (t) { t.stop(); });
            stream = null;
        }
        video.srcObject = null;
        camaraSinImagen = false;
        limpiarRecuadro();
        limpiarResultado();
        ocultarProgreso();
        mostrar('Se perdió la cámara. Reintentando…', 'error');
        programarReintento();
    }

    function programarReintento() {
        if (reintentoId) clearTimeout(reintentoId);
        reintentoId = setTimeout(function () {
            reintentoId = null;
            encender(true);
        }, REINTENTO_CAMARA_MS);
    }

    // Pausa: la cámara sigue abierta pero no manda imagen. No se envían cuadros --llegarían
    // negros-- y se avisa; si vuelve, se sigue solo.
    function camaraSinImagenAviso() {
        camaraSinImagen = true;
        limpiarRecuadro();
        limpiarResultado();
        ocultarProgreso();
        mostrar('La cámara dejó de mandar imagen. Esperando que vuelva…', 'warn');
    }

    function camaraVolvio() {
        camaraSinImagen = false;
        mostrar('Acercate a la cámara', 'info');
    }

    // ------------------------------------------------------------------ pintado

    function pintar(data) {
        if (!data.rostroDetectado) {
            limpiarRecuadro();
            ocultarProgreso();
            // Es el mensaje de rutina: no pisa un resultado que todavía se está leyendo.
            if (Date.now() < fijoHasta) return;
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
            mostrarProgreso(1, true);
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
            mostrarProgreso(data.progresoMs / data.objetivoMs, false);
            mostrar(data.mensaje, 'info');
            return;
        }

        // Reconocido pero sin registrar —no hay clase, falta esperar—, o no reconocido.
        recuadro(data, color1('--warning'), null);
        apellidoEl.textContent = data.apellido || '';
        detalleEl.textContent = '';
        ocultarProgreso();
        mostrar(data.mensaje, 'warn');
        fijoHasta = Date.now() + LECTURA_MINIMA_MS;
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
        // Cualquier mensaje puesto a propósito reemplaza al que se estaba sosteniendo.
        fijoHasta = 0;
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
