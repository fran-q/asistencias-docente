/*
 * pase-asistencia.js
 *
 * Pantalla de pase de asistencia:
 *  - botón único Encender/Apagar cámara (toggle),
 *  - botón único Iniciar/Detener pase (toggle): cuando está activo, manda
 *    un frame cada ~1 s al endpoint /asistencia/pase/marcar y muestra el
 *    resultado (marcado / ya estaba / sin clase / no reconocido).
 *
 * El recuadro sobre la cara va en VERDE cuando el rostro se reconoce y
 * además se marcó (o ya estaba marcada) la asistencia, AMARILLO si
 * reconoce pero no hay clase ahora, y ROJO si detecta cara sin reconocer.
 *
 * Las imágenes nunca se persisten.
 */
(function () {
    'use strict';

    const video      = document.getElementById('pa-video');
    const canvas     = document.getElementById('pa-canvas');
    const overlay    = document.getElementById('pa-overlay');
    const btnCamara  = document.getElementById('pa-btn-camara');
    const btnPase    = document.getElementById('pa-btn-pase');
    const mensajeEl  = document.getElementById('pa-estado-mensaje');
    const claseEl    = document.getElementById('pa-clase');
    const distanciaEl = document.getElementById('pa-distancia');

    if (!video || !btnCamara) return;

    /** Tiempo entre frames enviados al servidor. */
    const INTERVALO_MS = 1000;
    /** Pausa tras marcar (nueva o ya estaba) — evita ruido continuo. */
    const PAUSA_TRAS_MARCAR_MS = 5000;

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    let stream = null;
    let loopId = null;
    let enVuelo = false;
    let pausaTimeoutId = null;
    let cuentaRegresivaId = null;

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
            btnCamara.textContent = 'Apagar cámara';
            btnPase.disabled      = false;
            mostrarMensaje('Cámara encendida. Apretá "Iniciar pase" para comenzar.', 'info');
            claseEl.textContent = '';
            distanciaEl.textContent = '';
        } catch (err) {
            mostrarMensaje('No se pudo acceder a la cámara: ' + traducirError(err), 'error');
        }
    }

    function apagarCamara() {
        detenerLoop();
        limpiarOverlay();
        if (stream) {
            stream.getTracks().forEach(function (t) { t.stop(); });
            stream = null;
        }
        video.srcObject = null;
        btnCamara.textContent = 'Encender cámara';
        btnPase.textContent   = 'Iniciar pase';
        btnPase.disabled      = true;
        mostrarMensaje('Cámara apagada', 'info');
        claseEl.textContent = '';
        distanciaEl.textContent = '';
    }

    // ---- Pase: toggle -----------------------------------------------------

    function togglePase() {
        if (loopId) {
            detenerLoop();
            mostrarMensaje('Pase detenido.', 'info');
            claseEl.textContent = '';
            distanciaEl.textContent = '';
            limpiarOverlay();
        } else {
            arrancarLoop();
        }
    }

    function arrancarLoop() {
        if (!stream || loopId) return;
        btnPase.textContent = 'Detener pase';
        mostrarMensaje('Buscando rostros…', 'info');
        marcarFrame();
        loopId = setInterval(marcarFrame, INTERVALO_MS);
    }

    function detenerLoop() {
        if (loopId) {
            clearInterval(loopId);
            loopId = null;
        }
        cancelarPausa();
        btnPase.textContent = 'Iniciar pase';
    }

    /**
     * Pausa el envío de frames por unos segundos tras una marca exitosa.
     * Durante la pausa, el botón "Detener pase" sigue activo (el loop NO
     * se detiene del todo), pero no se mandan más frames al servidor.
     */
    function pausarLoopTrasMarcar(claseLabel) {
        cancelarPausa();
        if (!loopId) return;             // ya estaba detenido manualmente
        clearInterval(loopId);           // freno el envío
        loopId = null;

        let restante = Math.round(PAUSA_TRAS_MARCAR_MS / 1000);
        actualizarCuentaRegresiva(restante, claseLabel);
        cuentaRegresivaId = setInterval(function () {
            restante--;
            if (restante > 0) {
                actualizarCuentaRegresiva(restante, claseLabel);
            }
        }, 1000);

        pausaTimeoutId = setTimeout(function () {
            cancelarPausa();
            // Reanudar el loop si la cámara sigue prendida y el botón sigue activo
            if (stream && btnPase.textContent === 'Detener pase') {
                marcarFrame();
                loopId = setInterval(marcarFrame, INTERVALO_MS);
            }
        }, PAUSA_TRAS_MARCAR_MS);
    }

    function cancelarPausa() {
        if (pausaTimeoutId)    { clearTimeout(pausaTimeoutId);   pausaTimeoutId = null; }
        if (cuentaRegresivaId) { clearInterval(cuentaRegresivaId); cuentaRegresivaId = null; }
    }

    function actualizarCuentaRegresiva(segundos, claseLabel) {
        const baseMsg = claseLabel
            ? 'Asistencia ya registrada para ' + claseLabel + '.'
            : 'Asistencia ya registrada.';
        mostrarMensaje(baseMsg + ' Próximo escaneo en ' + segundos + ' s…', 'info');
    }

    async function marcarFrame() {
        if (!stream || enVuelo) return;
        ajustarOverlay();
        canvas.width  = video.videoWidth;
        canvas.height = video.videoHeight;
        canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
        const dataUrl = canvas.toDataURL('image/jpeg', 0.7);

        enVuelo = true;
        try {
            const headers = { 'Content-Type': 'application/json' };
            if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
            const resp = await fetch('/asistencia/pase/marcar', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ imagen: dataUrl })
            });
            if (!resp.ok) return;
            renderizar(await resp.json());
        } catch (err) {
            // Error de red transitorio: no molestamos.
        } finally {
            enVuelo = false;
        }
    }

    function renderizar(data) {
        if (!data.rostroDetectado) {
            limpiarOverlay();
            mostrarMensaje('No se detecta ningún rostro.', 'info');
            claseEl.textContent = '';
            distanciaEl.textContent = '';
            return;
        }

        // 1) rostro detectado pero no se reconoce ningún docente registrado
        if (!data.reconocido) {
            dibujarRecuadro(data.x, data.y, data.ancho, data.alto, '#e57373', null);
            mostrarMensaje(data.mensaje, 'error');
            claseEl.textContent = '';
            distanciaEl.textContent = data.distancia
                ? 'Distancia más cercana: ' + data.distancia.toFixed(1)
                : '';
            return;
        }

        // 2) reconocido + marcado (o ya estaba) → VERDE
        if (data.asistenciaMarcada) {
            const color = data.yaEstaba ? '#1976d2' : '#2e7d32';
            dibujarRecuadro(data.x, data.y, data.ancho, data.alto, color, data.docenteNombre);
            mostrarMensaje(data.mensaje, data.yaEstaba ? 'info' : 'success');
            claseEl.textContent = data.claseLabel || '';
            distanciaEl.textContent = data.distancia
                ? 'Distancia: ' + data.distancia.toFixed(1) + ' (menor = más parecido)'
                : '';
            // Pausa breve para no bombardear el server con frames del mismo
            // docente que ya está marcado. Backend igual es idempotente.
            pausarLoopTrasMarcar(data.claseLabel);
            return;
        }

        // 3) reconocido pero NO hay clase ahora → AMARILLO
        dibujarRecuadro(data.x, data.y, data.ancho, data.alto, '#ffc107', data.docenteNombre);
        mostrarMensaje(data.mensaje, 'warn');
        claseEl.textContent = '';
        distanciaEl.textContent = data.distancia
            ? 'Distancia: ' + data.distancia.toFixed(1)
            : '';
    }

    // ---- Overlay ----------------------------------------------------------

    function ajustarOverlay() {
        if (video.videoWidth > 0 && video.videoHeight > 0) {
            overlay.width  = video.videoWidth;
            overlay.height = video.videoHeight;
        }
    }

    function dibujarRecuadro(x, y, ancho, alto, color, label) {
        const ctx = overlay.getContext('2d');
        ctx.clearRect(0, 0, overlay.width, overlay.height);
        ctx.strokeStyle = color;
        ctx.lineWidth   = Math.max(3, Math.round(overlay.width / 160));
        ctx.strokeRect(x, y, ancho, alto);
        if (label) {
            const fontSize = Math.max(14, Math.round(overlay.width / 35));
            ctx.font = '600 ' + fontSize + 'px sans-serif';
            const padX = 6, padY = 4;
            const textW = ctx.measureText(label).width;
            const labelY = Math.max(fontSize + padY * 2, y);
            ctx.fillStyle = color;
            ctx.fillRect(x, labelY - fontSize - padY * 2, textW + padX * 2, fontSize + padY * 2);
            ctx.fillStyle = '#fff';
            ctx.fillText(label, x + padX, labelY - padY);
        }
    }

    function limpiarOverlay() {
        if (overlay.getContext) {
            overlay.getContext('2d').clearRect(0, 0, overlay.width, overlay.height);
        }
    }

    // ---- UI helpers -------------------------------------------------------

    function mostrarMensaje(texto, tipo) {
        mensajeEl.textContent = texto;
        mensajeEl.style.color = (
            tipo === 'success' ? '#4caf50' :
            tipo === 'error'   ? '#e57373' :
            tipo === 'warn'    ? '#ffc107' :
                                 ''
        );
        mensajeEl.style.fontWeight = (tipo === 'success' || tipo === 'warn') ? '600' : '400';
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
    btnPase.addEventListener('click', togglePase);
    window.addEventListener('pagehide', apagarCamara);
})();
