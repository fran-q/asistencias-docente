/*
 * reconocimiento.js — Sprint 4 Fase D
 *
 * Pantalla de reconocimiento facial en vivo:
 *  - botón único Encender/Apagar cámara (toggle, cambia de texto),
 *  - botón único Reconocer/Detener (toggle): cuando está activo, manda
 *    frames al servidor cada ~700 ms y muestra quién es el docente
 *    identificado en el cuadro.
 *
 * El recuadro sobre el rostro se pinta:
 *   - verde si reconoció a un docente,
 *   - amarillo si detectó un rostro pero no lo reconoce,
 *   - nada si no hay rostro.
 *
 * Las imágenes no se persisten en ningún lado.
 */
(function () {
    'use strict';

    const video        = document.getElementById('rec-video');
    const canvas       = document.getElementById('rec-canvas');
    const overlay      = document.getElementById('rec-overlay');
    const btnCamara    = document.getElementById('rec-btn-camara');
    const btnReconocer = document.getElementById('rec-btn-reconocer');
    const mensajeEl    = document.getElementById('rec-estado-mensaje');
    const distanciaEl  = document.getElementById('rec-distancia');

    if (!video || !btnCamara) return;

    const INTERVALO_MS = 700;

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    let stream = null;
    let loopId = null;
    let enVuelo = false;

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
            btnCamara.textContent  = 'Apagar cámara';
            btnReconocer.disabled  = false;
            mostrarMensaje('Cámara encendida. Apretá "Reconocer rostros" para empezar.', 'info');
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
        btnCamara.textContent     = 'Encender cámara';
        btnReconocer.textContent  = 'Reconocer rostros';
        btnReconocer.disabled     = true;
        mostrarMensaje('Cámara apagada', 'info');
        distanciaEl.textContent = '';
    }

    // ---- Reconocimiento continuo: toggle ----------------------------------

    function toggleReconocimiento() {
        if (loopId) {
            detenerLoop();
            mostrarMensaje('Reconocimiento detenido.', 'info');
            distanciaEl.textContent = '';
            limpiarOverlay();
        } else {
            arrancarLoop();
        }
    }

    function arrancarLoop() {
        if (!stream || loopId) return;
        btnReconocer.textContent = 'Detener reconocimiento';
        mostrarMensaje('Buscando rostros…', 'info');
        // Una llamada inmediata + loop
        identificarFrame();
        loopId = setInterval(identificarFrame, INTERVALO_MS);
    }

    function detenerLoop() {
        if (loopId) {
            clearInterval(loopId);
            loopId = null;
        }
        btnReconocer.textContent = 'Reconocer rostros';
    }

    async function identificarFrame() {
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
            const resp = await fetch('/reconocimiento/identificar', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ imagen: dataUrl })
            });
            if (!resp.ok) return;
            const data = await resp.json();
            renderizar(data);
        } catch (err) {
            // Sin internet o error transitorio: no dibujamos, no molestamos.
        } finally {
            enVuelo = false;
        }
    }

    function renderizar(data) {
        if (!data.rostroDetectado) {
            limpiarOverlay();
            mostrarMensaje('No se detecta ningún rostro.', 'info');
            distanciaEl.textContent = '';
            return;
        }
        if (data.reconocido) {
            dibujarRecuadro(data.x, data.y, data.ancho, data.alto, '#2e7d32', data.docenteNombre);
            mostrarMensaje('Rostro presente: ' + data.docenteNombre, 'success');
            distanciaEl.textContent = 'Distancia: ' + data.distancia.toFixed(1)
                + ' (menor = más parecido)';
        } else {
            dibujarRecuadro(data.x, data.y, data.ancho, data.alto, '#ffc107', null);
            mostrarMensaje(data.mensaje, 'error');
            distanciaEl.textContent = data.distancia
                ? 'Distancia más cercana: ' + data.distancia.toFixed(1)
                : '';
        }
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
            // Etiqueta arriba del recuadro con el nombre
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
            tipo === 'info'    ? '' :
                                 ''
        );
        mensajeEl.style.fontWeight = (tipo === 'success') ? '600' : '400';
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
    btnReconocer.addEventListener('click', toggleReconocimiento);
    window.addEventListener('pagehide', apagarCamara);
})();
