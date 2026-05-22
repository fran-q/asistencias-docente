/*
 * reconocimiento.js — Sprint 4 Fase B
 *
 * Maneja la pantalla de prueba de detección de rostro:
 *  - enciende / apaga la cámara del navegador (getUserMedia),
 *  - captura un frame y lo envía al servidor (/reconocimiento/detectar),
 *  - muestra si se detectó un rostro.
 *
 * La imagen no se guarda en ningún lado: se captura, se manda y se descarta.
 */
(function () {
    'use strict';

    const video        = document.getElementById('rec-video');
    const canvas       = document.getElementById('rec-canvas');
    const btnEncender  = document.getElementById('rec-btn-encender');
    const btnDetectar  = document.getElementById('rec-btn-detectar');
    const btnApagar    = document.getElementById('rec-btn-apagar');
    const resultado    = document.getElementById('rec-resultado');
    const mensajeEl    = document.getElementById('rec-resultado-mensaje');
    const detalleEl    = document.getElementById('rec-resultado-detalle');
    const cantidadEl   = document.getElementById('rec-cantidad');
    const posicionEl   = document.getElementById('rec-posicion');
    const tamanoEl     = document.getElementById('rec-tamano');

    // Si faltan elementos, no estamos en la pantalla de reconocimiento.
    if (!video || !btnEncender || !btnDetectar || !btnApagar) {
        return;
    }

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    let stream = null;

    // ---- Cámara -----------------------------------------------------------

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
            btnEncender.disabled = true;
            btnDetectar.disabled = false;
            btnApagar.disabled   = false;
            ocultarResultado();
        } catch (err) {
            mostrarMensaje('No se pudo acceder a la cámara: ' + traducirError(err), 'error');
        }
    }

    function apagarCamara() {
        if (stream) {
            stream.getTracks().forEach(function (t) { t.stop(); });
            stream = null;
        }
        video.srcObject = null;
        btnEncender.disabled = false;
        btnDetectar.disabled = true;
        btnApagar.disabled   = true;
    }

    // ---- Detección --------------------------------------------------------

    async function detectarRostro() {
        if (!stream) {
            return;
        }
        // Capturar el frame actual del video en el canvas.
        canvas.width  = video.videoWidth;
        canvas.height = video.videoHeight;
        canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
        const dataUrl = canvas.toDataURL('image/jpeg', 0.9);

        btnDetectar.disabled = true;
        mostrarMensaje('Procesando…', 'info');

        try {
            const headers = { 'Content-Type': 'application/json' };
            if (csrfToken && csrfHeader) {
                headers[csrfHeader] = csrfToken;
            }
            const resp = await fetch('/reconocimiento/detectar', {
                method: 'POST',
                headers: headers,
                body: JSON.stringify({ imagen: dataUrl })
            });
            if (!resp.ok) {
                throw new Error('El servidor respondió ' + resp.status);
            }
            mostrarResultado(await resp.json());
        } catch (err) {
            mostrarMensaje('No se pudo procesar la imagen: ' + err.message, 'error');
        } finally {
            btnDetectar.disabled = (stream === null);
        }
    }

    // ---- Render del resultado --------------------------------------------

    function mostrarResultado(data) {
        if (data.rostroDetectado) {
            mostrarMensaje(data.mensaje, data.cantidadRostros === 1 ? 'success' : 'error');
            cantidadEl.textContent = data.cantidadRostros;
            posicionEl.textContent = data.x + ', ' + data.y;
            tamanoEl.textContent   = data.ancho + ' × ' + data.alto + ' px';
            detalleEl.hidden = false;
        } else {
            mostrarMensaje(data.mensaje, 'error');
            detalleEl.hidden = true;
        }
    }

    function mostrarMensaje(texto, tipo) {
        mensajeEl.textContent = texto;
        resultado.className = 'reconocimiento__resultado alert alert--' + tipo;
        resultado.hidden = false;
    }

    function ocultarResultado() {
        resultado.hidden = true;
        detalleEl.hidden = true;
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

    btnEncender.addEventListener('click', encenderCamara);
    btnDetectar.addEventListener('click', detectarRostro);
    btnApagar.addEventListener('click', apagarCamara);

    // Liberar la cámara si el usuario navega a otra página.
    window.addEventListener('pagehide', apagarCamara);
})();
