/*
 * registro-facial.js
 *
 * Pantalla de registro del modelo facial:
 *  - botón único Encender/Apagar cámara (toggle, cambia de texto),
 *  - mientras la cámara está encendida, un loop manda un frame al servidor
 *    cada ~700 ms y dibuja un recuadro amarillo SOBRE el rostro detectado
 *    (mismo Haar Cascade que se usa para entrenar — feedback fiel),
 *  - botón único Iniciar/Cancelar grabación (toggle): graba N segundos,
 *    captura un frame cada intervalo configurado, y al terminar manda todo
 *    al servidor, que descarta frames sin rostro válido y entrena el modelo.
 *
 * Ningún video ni foto se persiste.
 */
(function () {
    'use strict';

    const seccion = document.querySelector('.registro-facial');
    if (!seccion) return;

    const video        = document.getElementById('rf-video');
    const canvas       = document.getElementById('rf-canvas');
    const overlay      = document.getElementById('rf-overlay');
    const btnCamara    = document.getElementById('rf-btn-camara');
    const btnGrabar    = document.getElementById('rf-btn-grabar');
    const estadoEl     = document.getElementById('rf-estado');
    const contadorEl   = document.getElementById('rf-contador');
    const recIndicator = document.getElementById('rf-rec-indicator');
    const resultado    = document.getElementById('rf-resultado');
    const mensajeEl    = document.getElementById('rf-resultado-mensaje');

    if (!video || !btnCamara) return;

    const docenteId     = seccion.dataset.docenteId;
    const duracionSeg   = parseInt(seccion.dataset.duracionSeg, 10);
    const intervaloMs   = parseInt(seccion.dataset.intervaloMs, 10);
    const minimoValidas = parseInt(seccion.dataset.minimoValidas, 10);

    /** Cada cuánto pedirle al server que detecte la cara para dibujar el overlay. */
    const INTERVALO_DETECCION_MS = 700;

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    let stream = null;
    let grabando = false;
    let capturas = [];
    let tickInterval = null;
    let countdownInterval = null;
    let deteccionInterval = null;
    let deteccionEnVuelo = false;
    let segundosRestantes = 0;

    // ---- Cámara: toggle ---------------------------------------------------

    async function toggleCamara() {
        if (stream) {
            apagarCamara();
        } else {
            await encenderCamara();
        }
    }

    async function encenderCamara() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            mostrarMensaje('Tu navegador no soporta el acceso a la cámara.', 'error');
            return;
        }
        try {
            stream = await navigator.mediaDevices.getUserMedia({
                video: { width: { ideal: 640 }, height: { ideal: 480 } },
                audio: false
            });
            video.srcObject = stream;
            await video.play().catch(function () {});
            ajustarOverlay();
            iniciarLoopDeteccion();

            btnCamara.textContent = 'Apagar cámara';
            btnGrabar.disabled    = false;
            estadoEl.textContent  = 'Cámara encendida — listo para grabar';
            ocultarMensaje();
        } catch (err) {
            mostrarMensaje('No se pudo acceder a la cámara: ' + traducirError(err), 'error');
        }
    }

    function apagarCamara() {
        if (grabando) cancelarGrabacion();
        detenerLoopDeteccion();
        limpiarOverlay();
        if (stream) {
            stream.getTracks().forEach(function (t) { t.stop(); });
            stream = null;
        }
        video.srcObject = null;
        btnCamara.textContent   = 'Encender cámara';
        btnGrabar.textContent   = 'Iniciar grabación';
        btnGrabar.disabled      = true;
        estadoEl.textContent    = 'Cámara apagada';
        contadorEl.textContent  = '';
    }

    // ---- Detección en vivo: recuadro amarillo sobre el rostro -------------

    function ajustarOverlay() {
        if (video.videoWidth > 0 && video.videoHeight > 0) {
            overlay.width  = video.videoWidth;
            overlay.height = video.videoHeight;
        }
    }

    function iniciarLoopDeteccion() {
        if (deteccionInterval) return;
        deteccionInterval = setInterval(detectarYDibujar, INTERVALO_DETECCION_MS);
    }

    function detenerLoopDeteccion() {
        if (deteccionInterval) {
            clearInterval(deteccionInterval);
            deteccionInterval = null;
        }
    }

    async function detectarYDibujar() {
        if (!stream || deteccionEnVuelo) return;
        ajustarOverlay();
        canvas.width  = video.videoWidth;
        canvas.height = video.videoHeight;
        canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
        const dataUrl = canvas.toDataURL('image/jpeg', 0.6);

        deteccionEnVuelo = true;
        try {
            const headers = { 'Content-Type': 'application/json' };
            if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
            const resp = await fetch('/reconocimiento/detectar', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ imagen: dataUrl })
            });
            if (!resp.ok) return;
            const data = await resp.json();
            if (data.rostroDetectado && data.cantidadRostros === 1) {
                dibujarRecuadro(data.x, data.y, data.ancho, data.alto);
            } else {
                limpiarOverlay();
            }
        } catch (err) {
            // Errores de red transitorios: no dibujamos, no molestamos.
        } finally {
            deteccionEnVuelo = false;
        }
    }

    function dibujarRecuadro(x, y, ancho, alto) {
        const ctx = overlay.getContext('2d');
        ctx.clearRect(0, 0, overlay.width, overlay.height);
        ctx.strokeStyle = '#ffc107';
        ctx.lineWidth   = Math.max(3, Math.round(overlay.width / 160));
        ctx.lineJoin    = 'round';
        ctx.strokeRect(x, y, ancho, alto);
    }

    function limpiarOverlay() {
        if (overlay.getContext) {
            overlay.getContext('2d').clearRect(0, 0, overlay.width, overlay.height);
        }
    }

    // ---- Grabación: toggle ------------------------------------------------

    function toggleGrabacion() {
        if (grabando) {
            cancelarGrabacion();
        } else {
            iniciarGrabacion();
        }
    }

    function iniciarGrabacion() {
        if (!stream || grabando) return;
        capturas = [];
        grabando = true;
        segundosRestantes = duracionSeg;

        recIndicator.style.display = 'inline-flex';
        btnGrabar.textContent = 'Cancelar grabación';
        btnCamara.disabled    = true; // no se puede apagar mientras graba
        estadoEl.textContent  = 'Grabando…';
        ocultarMensaje();
        actualizarContador();

        capturarFrame();
        tickInterval = setInterval(capturarFrame, intervaloMs);
        countdownInterval = setInterval(tickSegundo, 1000);
    }

    function capturarFrame() {
        if (!stream) return;
        canvas.width  = video.videoWidth;
        canvas.height = video.videoHeight;
        canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
        capturas.push(canvas.toDataURL('image/jpeg', 0.85));
    }

    function tickSegundo() {
        segundosRestantes--;
        if (segundosRestantes <= 0) {
            finalizarGrabacion();
        } else {
            actualizarContador();
        }
    }

    function actualizarContador() {
        contadorEl.textContent = 'Quedan ' + segundosRestantes + ' s — '
            + capturas.length + ' frames capturados';
    }

    function cancelarGrabacion() {
        pararIntervalosDeGrabacion();
        grabando = false;
        capturas = [];
        recIndicator.style.display = 'none';
        btnGrabar.textContent = 'Iniciar grabación';
        btnCamara.disabled    = false;
        estadoEl.textContent  = 'Grabación cancelada';
        contadorEl.textContent = '';
    }

    function pararIntervalosDeGrabacion() {
        if (tickInterval)      { clearInterval(tickInterval);      tickInterval = null; }
        if (countdownInterval) { clearInterval(countdownInterval); countdownInterval = null; }
    }

    async function finalizarGrabacion() {
        pararIntervalosDeGrabacion();
        grabando = false;
        recIndicator.style.display = 'none';
        btnGrabar.disabled    = true;
        btnGrabar.textContent = 'Iniciar grabación';
        estadoEl.textContent  = 'Procesando ' + capturas.length + ' frames…';
        mostrarMensaje('Entrenando el modelo facial… esto puede tardar unos segundos.', 'info');

        try {
            const headers = { 'Content-Type': 'application/json' };
            if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
            const resp = await fetch('/docentes/' + docenteId + '/rostro/registrar', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ capturas: capturas })
            });
            if (!resp.ok) throw new Error('El servidor respondió ' + resp.status);
            const data = await resp.json();
            if (data.exito) {
                apagarCamara();
                mostrarMensaje(data.mensaje + ' Redirigiendo…', 'success');
                setTimeout(function () {
                    window.location.href = '/docentes/' + docenteId + '/editar';
                }, 1200);
            } else {
                mostrarMensaje(data.mensaje, 'error');
                btnGrabar.disabled = false;
                btnCamara.disabled = false;
                estadoEl.textContent = 'Listo para volver a grabar';
            }
        } catch (err) {
            mostrarMensaje('No se pudo registrar: ' + err.message, 'error');
            btnGrabar.disabled = false;
            btnCamara.disabled = false;
        }
    }

    // ---- UI helpers -------------------------------------------------------

    function mostrarMensaje(texto, tipo) {
        mensajeEl.textContent = texto;
        resultado.className = 'alert alert--' + tipo;
        resultado.hidden = false;
    }

    function ocultarMensaje() {
        resultado.hidden = true;
    }

    function traducirError(err) {
        switch (err && err.name) {
            case 'NotAllowedError':
            case 'SecurityError':
                return 'denegaste el permiso de cámara.';
            case 'NotFoundError':
            case 'DevicesNotFoundError':
                return 'no se encontró ninguna cámara.';
            case 'NotReadableError':
            case 'TrackStartError':
                return 'la cámara está siendo usada por otra aplicación.';
            default:
                return (err && err.message) ? err.message : 'error desconocido.';
        }
    }

    // ---- Eventos ----------------------------------------------------------

    btnCamara.addEventListener('click', toggleCamara);
    btnGrabar.addEventListener('click', toggleGrabacion);
    window.addEventListener('pagehide', apagarCamara);
})();
