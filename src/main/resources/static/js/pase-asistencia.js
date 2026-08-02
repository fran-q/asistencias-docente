/*
 * pase-asistencia.js
 *
 * Pantalla de pase de asistencia:
 *  - botón principal Iniciar/Detener pase: al iniciar enciende la cámara y
 *    arranca el loop en un solo paso, y cuando está activo manda un frame
 *    cada ~1 s al endpoint /asistencia/pase/marcar mostrando el resultado
 *    (marcado / ya estaba / sin clase / no reconocido);
 *  - botón secundario Apagar cámara, para soltar el dispositivo.
 *
 * Son dos controles y no uno porque apagan cosas distintas: detener el pase
 * corta el envío de frames al servidor pero deja la vista previa, que es lo
 * que hace falta para pausar el marcado sin perder el encuadre. Pero NO son
 * dos pasos de arranque: encender la cámara sin llegar a iniciar el pase no
 * le sirve a nadie, y pedirlo en dos clicks todos los días era ruido.
 *
 * El recuadro sobre la cara va en VERDE cuando se reconoce a la persona y
 * ROJO cuando no. Las imágenes nunca se persisten.
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

    if (!video || !btnCamara) return;

    /** Tiempo entre frames enviados al servidor. */
    const INTERVALO_MS = 1000;
    /** Pausa tras marcar (nueva o ya estaba) — evita ruido continuo. */
    const PAUSA_TRAS_MARCAR_MS = 3000;

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    let stream = null;
    let loopId = null;
    let enVuelo = false;
    let pausaTimeoutId = null;
    let cuentaRegresivaId = null;

    // Si el pase esta activo o no. Es un estado propio y no se deduce de loopId ni del texto
    // del boton: durante la pausa posterior a una marca loopId queda en null aunque el pase
    // sigue activo, y con esa confusion el boton "Detener" terminaba arrancando otro loop.
    let paseActivo = false;

    // ---- Cámara -----------------------------------------------------------

    // Deja la camara lista para enviar frames. Devuelve false si no se pudo, para que
    // el que llama no siga adelante creyendo que hay imagen.
    async function encenderCamara() {
        if (stream) return true;
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            mostrarMensaje('Tu navegador no soporta el acceso a la cámara.', 'error');
            return false;
        }
        try {
            stream = await navigator.mediaDevices.getUserMedia({
                video: { width: { ideal: 640 }, height: { ideal: 480 } },
                audio: false
            });
            video.srcObject = stream;
            await video.play().catch(function () {});
            ajustarOverlay();
            btnCamara.hidden = false;
            claseEl.textContent = '';
            return true;
        } catch (err) {
            // getUserMedia puede haber concedido la camara y fallar despues, al engancharla
            // al <video>. Si no se suelta acá, stream queda seteado y todo el resto cree que
            // hay camara prendida: el proximo "Iniciar pase" arranca el loop sin imagen.
            if (stream) {
                stream.getTracks().forEach(function (t) { t.stop(); });
                stream = null;
            }
            video.srcObject = null;
            mostrarMensaje('No se pudo acceder a la cámara: ' + traducirError(err), 'error');
            return false;
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
        // El boton de apagar solo existe mientras haya algo que apagar.
        btnCamara.hidden = true;
        mostrarMensaje('Cámara apagada', 'info');
        claseEl.textContent = '';
    }

    // ---- Pase: toggle -----------------------------------------------------

    async function togglePase() {
        if (paseActivo) {
            // Detener corta el envio de frames pero deja la camara prendida: es la unica
            // razon por la que siguen siendo dos controles y no uno. Volver a pedir el
            // dispositivo cuesta un segundo en negro y, segun el navegador, otro permiso.
            detenerLoop();
            mostrarMensaje('Pase detenido. La cámara sigue encendida.', 'info');
            claseEl.textContent = '';
            limpiarOverlay();
            return;
        }
        // Un solo click hace las dos cosas. Tener la camara prendida sin el pase andando
        // no le sirve a nadie al arrancar, asi que no se pide como paso aparte.
        btnPase.disabled = true;
        mostrarMensaje('Encendiendo la cámara…', 'info');
        const listo = await encenderCamara();
        btnPase.disabled = false;
        if (listo) arrancarLoop();
    }

    function arrancarLoop() {
        if (!stream || paseActivo) return;
        paseActivo = true;
        btnPase.textContent = 'Detener pase';
        mostrarMensaje('Buscando rostros…', 'info');
        marcarFrame();
        loopId = setInterval(marcarFrame, INTERVALO_MS);
    }

    // Deja el pase completamente frenado: el envio de frames y tambien la pausa pendiente, que
    // de no cancelarse volveria a arrancar el loop unos segundos despues.
    function detenerLoop() {
        paseActivo = false;
        if (loopId) {
            clearInterval(loopId);
            loopId = null;
        }
        cancelarPausa();
        btnPase.textContent = 'Iniciar pase';
    }

    /**
     * Pausa el envío de frames por unos segundos tras una marca exitosa, para no bombardear
     * al servidor con el mismo docente ya marcado. El pase sigue ACTIVO durante la pausa:
     * lo que se frena es el envío, y al terminar la cuenta regresiva se reanuda solo.
     */
    function pausarLoopTrasMarcar(claseLabel) {
        cancelarPausa();
        if (!paseActivo) return;         // ya estaba detenido manualmente
        if (loopId) {
            clearInterval(loopId);       // freno el envío, pero el pase sigue activo
            loopId = null;
        }

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
            // Se reanuda solo si el pase sigue activo. Antes esto miraba el TEXTO del boton,
            // que es estado de presentacion y no de la logica.
            if (stream && paseActivo) {
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
            const datos = await resp.json();
            // Si mientras viajaba el pedido se detuvo el pase, esta respuesta ya no interesa:
            // pintarla dejaria un recuadro y un mensaje en pantalla despues de haber frenado.
            if (!paseActivo) return;
            renderizar(datos);
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
            return;
        }

        // 1) rostro detectado pero RECHAZADO → ROJO, con el motivo
        //    Puede ser porque no esta registrado o porque no se lo pudo distinguir de
        //    otro parecido. El servidor manda cual de los dos, asi que se muestra tal
        //    cual: se corrigen de forma distinta.
        if (!data.reconocido) {
            // Sin coordenadas el rechazo no apunta a nadie en particular: es el caso de
            // varias personas en cuadro. Recuadrar a una sola daria a entender que el
            // sistema la eligio, que es justo lo contrario de lo que dice el mensaje.
            if (data.x === null || data.x === undefined) {
                limpiarOverlay();
            } else {
                dibujarRecuadro(data.x, data.y, data.ancho, data.alto, '#e53935', 'No reconocido');
            }
            mostrarMensaje(data.mensaje, 'error');
            claseEl.textContent = '';
            return;
        }

        // 2) reconocido pero todavia sosteniendo la identidad → CIAN, sin nombre
        //    El nombre no se muestra a proposito: si el reconocimiento esta oscilando entre
        //    dos personas parecidas, mostrarlo haria aparecer y desaparecer el nombre
        //    equivocado en pantalla, que es justo lo que este paso viene a evitar.
        if (data.confirmando) {
            dibujarRecuadro(data.x, data.y, data.ancho, data.alto, '#00acc1', null);
            var faltan = Math.max(0, Math.ceil((data.objetivoMs - data.progresoMs) / 1000));
            mostrarMensaje('Sostené la posición… ' + faltan + ' s', 'info');
            claseEl.textContent = '';
            return;
        }

        // 3) reconocido + marcado (o ya estaba) → VERDE
        if (data.asistenciaMarcada) {
            const color = data.yaEstaba ? '#1976d2' : '#2e7d32';
            dibujarRecuadro(data.x, data.y, data.ancho, data.alto, color, data.docenteNombre);
            mostrarMensaje(data.mensaje, data.yaEstaba ? 'info' : 'success');
            claseEl.textContent = data.claseLabel || '';
            // Pausa breve para no bombardear el server con frames del mismo
            // docente que ya está marcado. Backend igual es idempotente.
            pausarLoopTrasMarcar(data.claseLabel);
            return;
        }

        // 4) reconocido pero NO hay clase ahora → VERDE igual
        //    El recuadro responde a UNA sola pregunta: si el sistema reconocio a la
        //    persona. Y la reconocio. Que no haya clase en curso es otra cosa, y lo dice
        //    el mensaje; pintarlo de amarillo hacia parecer que el reconocimiento habia
        //    fallado, cuando el unico que fallo era el horario.
        dibujarRecuadro(data.x, data.y, data.ancho, data.alto, '#2e7d32', data.docenteNombre);
        mostrarMensaje(data.mensaje, 'warn');
        claseEl.textContent = '';
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

    btnCamara.addEventListener('click', apagarCamara);
    btnPase.addEventListener('click', togglePase);
    window.addEventListener('pagehide', apagarCamara);
})();
