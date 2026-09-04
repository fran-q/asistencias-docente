/* =============================================================================
 *  registro-facial.js
 *  Captura guiada del modelo facial.
 *
 *  ----------------------------------------------------------------------------
 *  Qué cambió y por qué. Antes esto grababa 30 segundos y mandaba todos los
 *  frames; el servidor descartaba los malos y entrenaba con lo que quedara. El
 *  problema no eran los frames descartados sino los aceptados: alguien quieto
 *  30 segundos produce veinte fotos casi iguales, y un LBPH entrenado con eso
 *  aprende una sola pose. Después, en el pase, basta que la persona incline la
 *  cabeza para que la distancia se dispare.
 *
 *  Ahora la secuencia va por etapas. Cada una pide una pose, muestra en vivo
 *  qué corregir, y captura sola cuando la imagen sirve. Termina cuando están
 *  todas las etapas, no cuando se acaba un reloj: si por la luz alguien tarda
 *  el doble, tarda el doble y sale un modelo bueno.
 *
 *  ----------------------------------------------------------------------------
 *  Quién decide qué. El navegador decide CUÁNDO capturar; el servidor decide si
 *  la captura sirve. Toda evaluación es una llamada a /reconocimiento/detectar,
 *  que corre el mismo Haar Cascade que después entrena y reconoce. Se podría
 *  evaluar acá y sería más rápido, pero con otro detector: el recuadro diría
 *  "perfecto" y el entrenamiento después descartaría el frame. El feedback
 *  tiene que venir del mismo motor que toma la decisión final.
 *
 *  Ningún video ni foto se persiste.
 * ========================================================================== */
(function () {
    'use strict';

    const seccion = document.querySelector('.registro-facial');
    if (!seccion) return;

    const video       = document.getElementById('rf-video');
    const canvas      = document.getElementById('rf-canvas');
    const overlay     = document.getElementById('rf-overlay');
    const btnCamara   = document.getElementById('rf-btn-camara');
    const btnReiniciar= document.getElementById('rf-btn-reiniciar');
    const pasoEl      = document.getElementById('rf-paso');
    const instrEl     = document.getElementById('rf-instruccion');
    const detalleEl   = document.getElementById('rf-detalle');
    const feedbackEl  = document.getElementById('rf-feedback');
    const listaEtapas = document.getElementById('rf-etapas');
    const resultado   = document.getElementById('rf-resultado');
    const mensajeEl   = document.getElementById('rf-resultado-mensaje');

    if (!video || !btnCamara) return;

    const docenteId        = seccion.dataset.docenteId;
    const capturasPorEtapa = parseInt(seccion.dataset.capturasPorEtapa, 10) || 3;

    const etapas = [].slice.call(listaEtapas.querySelectorAll('.captura__etapa'))
        .map(function (li) {
            return {
                nodo: li,
                instruccion: li.dataset.instruccion,
                detalle: li.dataset.detalle
            };
        });

    /** Cada cuánto se le pide al servidor que evalúe el cuadro. */
    const INTERVALO_EVALUACION_MS = 600;

    /**
     * Cuántas lecturas buenas seguidas hacen falta para capturar. Con una sola
     * alcanzaría, pero dos evitan pescar el instante exacto en que la persona
     * pasa por la pose de camino a otra cosa.
     */
    const LECTURAS_BUENAS_SEGUIDAS = 2;

    /**
     * Pausa después de capturar. Sin esto las 3 capturas de una etapa saldrían
     * en menos de dos segundos y serían prácticamente la misma foto — justo el
     * problema que esta pantalla vino a resolver.
     */
    const PAUSA_ENTRE_CAPTURAS_MS = 900;

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    let stream = null;
    let evaluando = false;
    let loopId = null;
    let enPausa = false;

    let etapaActual = 0;
    let capturasDeLaEtapa = 0;
    let buenasSeguidas = 0;
    let capturas = [];
    let enviando = false;

    // ---- Cámara -----------------------------------------------------------

    async function toggleCamara() {
        if (stream) apagarCamara(); else await encenderCamara();
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
            btnReiniciar.hidden = false;
            ocultarMensaje();
            reiniciarSecuencia();
            arrancarLoop();
        } catch (err) {
            mostrarMensaje('No se pudo acceder a la cámara: ' + traducirError(err), 'error');
        }
    }

    // Corta el loop, libera la camara y deja la pantalla como al principio.
    function apagarCamara() {
        detenerLoop();
        limpiarOverlay();
        if (stream) {
            stream.getTracks().forEach(function (t) { t.stop(); });
            stream = null;
        }
        video.srcObject = null;
        btnCamara.textContent = 'Encender cámara';
        btnReiniciar.hidden = true;
        feedbackEl.textContent = 'Cámara apagada';
        feedbackEl.className = 'captura__feedback';
    }

    // ---- Secuencia --------------------------------------------------------

    function reiniciarSecuencia() {
        etapaActual = 0;
        capturasDeLaEtapa = 0;
        buenasSeguidas = 0;
        capturas = [];
        enPausa = false;
        etapas.forEach(function (e) {
            e.nodo.classList.remove('captura__etapa--lista', 'captura__etapa--activa');
            e.nodo.querySelector('.captura__etapa-marca').textContent = '';
        });
        pintarEtapa();
    }

    // Muestra la instruccion de la etapa actual y la marca como activa en la lista.
    function pintarEtapa() {
        etapas.forEach(function (e, i) {
            e.nodo.classList.toggle('captura__etapa--activa', i === etapaActual);
        });
        const etapa = etapas[etapaActual];
        if (!etapa) return;

        pasoEl.textContent = 'Paso ' + (etapaActual + 1) + ' de ' + etapas.length;
        instrEl.textContent = etapa.instruccion;
        detalleEl.textContent = etapa.detalle;
        actualizarMarca();
    }

    // Refresca el contador de capturas de la etapa en curso.
    function actualizarMarca() {
        const etapa = etapas[etapaActual];
        if (!etapa) return;
        etapa.nodo.querySelector('.captura__etapa-marca').textContent =
            capturasDeLaEtapa + '/' + capturasPorEtapa;
    }

    // Da la etapa por cumplida y pasa a la siguiente, o envia si era la ultima.
    function completarEtapa() {
        const etapa = etapas[etapaActual];
        etapa.nodo.classList.remove('captura__etapa--activa');
        etapa.nodo.classList.add('captura__etapa--lista');
        etapa.nodo.querySelector('.captura__etapa-marca').textContent = 'listo';

        etapaActual++;
        capturasDeLaEtapa = 0;
        buenasSeguidas = 0;

        if (etapaActual >= etapas.length) {
            enviar();
        } else {
            pintarEtapa();
        }
    }

    // ---- Loop de evaluación ----------------------------------------------

    function arrancarLoop() {
        if (loopId) return;
        loopId = setInterval(evaluar, INTERVALO_EVALUACION_MS);
    }

    // Frena la evaluacion periodica.
    function detenerLoop() {
        if (loopId) { clearInterval(loopId); loopId = null; }
    }

    // Iguala el tamano del canvas al del video, que recien se conoce al reproducir.
    function ajustarOverlay() {
        if (video.videoWidth > 0 && video.videoHeight > 0) {
            overlay.width  = video.videoWidth;
            overlay.height = video.videoHeight;
        }
    }

    // Saca el cuadro que se esta viendo, con la calidad de compresion que se pida.
    function cuadroActual(calidad) {
        canvas.width  = video.videoWidth;
        canvas.height = video.videoHeight;
        canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);
        return canvas.toDataURL('image/jpeg', calidad);
    }

    async function evaluar() {
        if (!stream || evaluando || enPausa || enviando) return;
        ajustarOverlay();

        // Calidad baja para evaluar (viaja cada 600 ms) y alta para lo que se
        // guarda: el frame que entrena se vuelve a sacar en capturar().
        const dataUrl = cuadroActual(0.6);

        evaluando = true;
        try {
            const headers = { 'Content-Type': 'application/json' };
            if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
            const resp = await fetch('/reconocimiento/detectar', {
                method: 'POST', headers: headers,
                body: JSON.stringify({ imagen: dataUrl })
            });
            // Equipo revocado con la captura guiada en curso. Se frena acá: seguir pidiendo
            // poses para capturas que despues no se van a poder guardar hace perder el
            // tiempo a las dos personas que estan frente a la camara.
            if (resp.status === 403) {
                detenerLoop();
                apagarCamara();
                mostrarMensaje('Este equipo ya no está autorizado para registrar rostros.', 'error');
                return;
            }
            if (!resp.ok) return;
            const datos = await resp.json();

            // La respuesta pudo tardar más que un apagado o un reinicio.
            if (!stream || enPausa || enviando) return;

            // Con varias personas en cuadro no se dibuja nada: el servidor manda el
            // recuadro del rostro mas grande, y pintarlo daria a entender que ese es el
            // que se va a registrar, cuando justamente el mensaje dice que no se puede.
            if (datos.rostroDetectado && datos.cantidadRostros === 1) {
                dibujarRecuadro(datos.x, datos.y, datos.ancho, datos.alto, datos.apta);
            } else {
                limpiarOverlay();
            }

            feedbackEl.textContent = datos.mensaje || '';
            feedbackEl.className = 'captura__feedback captura__feedback--'
                + (datos.apta ? 'ok' : 'corregir');

            if (datos.apta) {
                buenasSeguidas++;
                if (buenasSeguidas >= LECTURAS_BUENAS_SEGUIDAS) capturar();
            } else {
                buenasSeguidas = 0;
            }
        } catch (err) {
            // Error de red pasajero: no molestamos, la próxima vuelta reintenta.
        } finally {
            evaluando = false;
        }
    }

    // Guarda la captura, avanza el contador y hace una pausa para que la proxima salga distinta.
    function capturar() {
        capturas.push(cuadroActual(0.85));
        capturasDeLaEtapa++;
        buenasSeguidas = 0;
        actualizarMarca();

        if (capturasDeLaEtapa >= capturasPorEtapa) {
            completarEtapa();
            return;
        }

        // Respiro entre capturas de la misma etapa, para que no salgan clonadas.
        enPausa = true;
        feedbackEl.textContent = 'Capturada. Sostené la pose…';
        feedbackEl.className = 'captura__feedback captura__feedback--ok';
        setTimeout(function () { enPausa = false; }, PAUSA_ENTRE_CAPTURAS_MS);
    }

    // ---- Overlay ----------------------------------------------------------

    function dibujarRecuadro(x, y, ancho, alto, apta) {
        const ctx = overlay.getContext('2d');
        ctx.clearRect(0, 0, overlay.width, overlay.height);
        // Verde cuando ya sirve, amarillo cuando falta corregir algo: el color
        // dice lo mismo que el texto, para no tener que leerlo cada vez.
        ctx.strokeStyle = apta ? '#2e8b57' : '#ffc107';
        ctx.lineWidth   = Math.max(3, Math.round(overlay.width / 160));
        ctx.lineJoin    = 'round';
        ctx.strokeRect(x, y, ancho, alto);
    }

    // Borra el recuadro dibujado sobre el video.
    function limpiarOverlay() {
        if (overlay.getContext) {
            overlay.getContext('2d').clearRect(0, 0, overlay.width, overlay.height);
        }
    }

    // ---- Envío ------------------------------------------------------------

    async function enviar() {
        enviando = true;
        detenerLoop();
        limpiarOverlay();
        pasoEl.textContent = 'Listo';
        instrEl.textContent = 'Entrenando el modelo facial…';
        detalleEl.textContent = '';
        feedbackEl.textContent = capturas.length + ' capturas tomadas';
        feedbackEl.className = 'captura__feedback';
        mostrarMensaje('Esto puede tardar unos segundos.', 'info');

        try {
            const headers = { 'Content-Type': 'application/json' };
            if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
            const resp = await fetch('/docentes/' + docenteId + '/rostro/registrar', {
                method: 'POST', headers: headers,
                body: JSON.stringify({ capturas: capturas })
            });
            // Equipo revocado mientras se tomaban las capturas. Se avisa con el motivo real
            // en vez de "el servidor respondio 403", que no le dice nada a quien opera.
            if (resp.status === 403) {
                apagarCamara();
                mostrarMensaje('Este equipo ya no está autorizado para registrar rostros. '
                             + 'Las capturas no se guardaron.', 'error');
                return;
            }
            if (!resp.ok) throw new Error('El servidor respondió ' + resp.status);
            const datos = await resp.json();

            if (datos.exito) {
                apagarCamara();
                mostrarMensaje(datos.mensaje + ' Redirigiendo…', 'success');
                setTimeout(function () {
                    window.location.href = '/docentes/' + docenteId + '/editar';
                }, 1400);
            } else {
                // El servidor revalida y puede rechazar lo que acá pasó. Su
                // mensaje dice qué falló, asi que se muestra tal cual.
                mostrarMensaje(datos.mensaje, 'error');
                prepararReintento();
            }
        } catch (err) {
            mostrarMensaje('No se pudo registrar: ' + err.message, 'error');
            prepararReintento();
        }
    }

    // Deja la pantalla lista para volver a empezar despues de un rechazo.
    function prepararReintento() {
        enviando = false;
        reiniciarSecuencia();
        if (stream) {
            arrancarLoop();
        } else {
            feedbackEl.textContent = 'Encendé la cámara para volver a intentar';
        }
    }

    // ---- UI helpers -------------------------------------------------------

    function mostrarMensaje(texto, tipo) {
        mensajeEl.textContent = texto;
        resultado.className = 'alert alert--' + tipo;
        resultado.hidden = false;
    }

    // Esconde el cartel de resultado.
    function ocultarMensaje() { resultado.hidden = true; }

    // Convierte el error de getUserMedia en una explicacion que se entienda.
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
    btnReiniciar.addEventListener('click', function () {
        ocultarMensaje();
        reiniciarSecuencia();
        if (stream && !loopId) arrancarLoop();
    });
    window.addEventListener('pagehide', apagarCamara);
})();
