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
    const progresoEl    = document.getElementById('pa-progreso');
    const progresoBarra = progresoEl.querySelector('.progreso__barra');

    if (!video || !btnCamara) return;

    /** Tiempo entre frames enviados al servidor. */
    const INTERVALO_MS = 1000;
    /**
     * Pausa tras marcar: el resultado queda en pantalla sin mandar cuadros. Eran 3 s y no
     * alcanzaban para leer la clase antes de que volviera "Buscando rostros".
     */
    const PAUSA_TRAS_MARCAR_MS = 4000;
    /**
     * Cuanto se sostiene un resultado que hay que leer --un rechazo, un "no tenes clase
     * ahora"-- antes de que lo pise el "No se detecta ningun rostro" del cuadro siguiente.
     * Antes duraba lo que tardaba ese cuadro, un segundo: alcanzaba con que el docente se
     * corriera un paso para que el motivo desapareciera sin leerse, y era justo el mensaje
     * que decia que habia que hacer.
     */
    const LECTURA_MINIMA_MS = 4000;

    const csrfToken  = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    // La ventana chica del pase (/asistencia/pase/ventana) es esta misma pantalla sin el
    // sistema alrededor. Se reconoce por el body y no por la URL.
    const esVentana = document.body.dataset.paseVentana === '1';
    // Mientras esta pantalla le cede el pase a esa ventana deja de publicar: si no, su
    // "apagado" pisaria el "andando" que la otra acaba de publicar.
    let cediendo = false;
    // Si hay otra ventana tomando asistencia, esta no puede: la camara es una sola.
    let bloqueadoPorOtra = false;

    // El estado que ven las otras ventanas (facial/pase-estado.js). Se pide en cada uso y no
    // se guarda al cargar: este archivo se ejecuta antes que el del layout.
    function estado() { return cediendo ? null : window.PaseEstado; }
    function publicar(que) { const e = estado(); if (e) e.publicar(que); }

    let stream = null;
    let loopId = null;
    let enVuelo = false;
    let pausaTimeoutId = null;
    let cuentaRegresivaId = null;

    // Si el pase esta activo o no. Es un estado propio y no se deduce de loopId ni del texto
    // del boton: durante la pausa posterior a una marca loopId queda en null aunque el pase
    // sigue activo, y con esa confusion el boton "Detener" terminaba arrancando otro loop.
    let paseActivo = false;
    // Deja de vigilar la camara (CamaraDelPuesto.vigilar). Null mientras no hay camara.
    let dejarDeVigilar = null;
    // La camara esta abierta pero no manda imagen: no se envian cuadros, que llegarian
    // negros y pisarian el aviso con "No se detecta ningun rostro".
    let camaraSinImagen = false;
    // Hasta cuando se sostiene en pantalla un resultado que hay que leer.
    let fijoHasta = 0;

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
            // La camara elegida para este puesto (V026), o la predeterminada si no hay
            // ninguna o si la elegida no esta conectada.
            stream = await CamaraDelPuesto.abrir(video, { width: { ideal: 640 }, height: { ideal: 480 } });
            video.srcObject = stream;
            camaraSinImagen = false;
            dejarDeVigilar = CamaraDelPuesto.vigilar(stream, {
                perdida: camaraPerdida,
                sinImagen: camaraSinImagenAviso,
                volvio: camaraVolvio
            });
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
        if (dejarDeVigilar) { dejarDeVigilar(); dejarDeVigilar = null; }
        camaraSinImagen = false;
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
        publicar('apagado');
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
        publicar('andando');
        marcarFrame();
        loopId = setInterval(marcarFrame, INTERVALO_MS);
        refrescarClases();
        refrescoClasesId = setInterval(refrescarClases, REFRESCO_CLASES_MS);
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
        ocultarProgreso();
        if (refrescoClasesId) { clearInterval(refrescoClasesId); refrescoClasesId = null; }
        btnPase.textContent = 'Iniciar pase';
        // Con la camara todavia prendida el pase esta quieto, no apagado: son dos estados
        // distintos y el punto del menu los muestra distinto. Apagar la camara publica lo suyo.
        if (stream) publicar('quieto');
    }

    /**
     * Pausa el envío de frames por unos segundos tras una marca exitosa, para no bombardear
     * al servidor con el mismo docente ya marcado. El pase sigue ACTIVO durante la pausa:
     * lo que se frena es el envío, y al terminar la cuenta regresiva se reanuda solo.
     */
    function pausarLoopTrasMarcar(claseLabel, tipoDeMarca) {
        cancelarPausa();
        if (!paseActivo) return;         // ya estaba detenido manualmente
        if (loopId) {
            clearInterval(loopId);       // freno el envío, pero el pase sigue activo
            loopId = null;
        }

        let restante = Math.round(PAUSA_TRAS_MARCAR_MS / 1000);
        actualizarCuentaRegresiva(restante, claseLabel, tipoDeMarca);
        cuentaRegresivaId = setInterval(function () {
            restante--;
            if (restante > 0) {
                actualizarCuentaRegresiva(restante, claseLabel, tipoDeMarca);
            }
        }, 1000);

        pausaTimeoutId = setTimeout(function () {
            cancelarPausa();
            ocultarProgreso();
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

    // Una entrada y una salida no dicen lo mismo: para la salida, claseLabel trae la
    // permanencia y las clases cubiertas, no una clase, asi que el texto tiene que cambiar.
    function actualizarCuentaRegresiva(segundos, claseLabel, tipoDeMarca) {
        let baseMsg;
        if (tipoDeMarca === 'SALIDA') {
            baseMsg = claseLabel
                ? 'Salida ya registrada: ' + claseLabel + '.'
                : 'Salida ya registrada.';
        } else {
            baseMsg = claseLabel
                ? 'Asistencia ya registrada para ' + claseLabel + '.'
                : 'Asistencia ya registrada.';
        }
        mostrarMensaje(baseMsg + ' Próximo escaneo en ' + segundos + ' s…', 'info');
    }

    async function marcarFrame() {
        if (!stream || enVuelo || camaraSinImagen) return;
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
            // El equipo dejo de estar autorizado: puede haber sido revocado desde otra
            // maquina con el pase ya andando. No se reintenta ni se sigue mandando cuadros
            // --ninguno va a entrar-- y se apaga la camara, porque tenerla encendida
            // capturando para nada es justo lo que el control existe para evitar.
            if (resp.status === 403) {
                detenerLoop();
                apagarCamara();
                mostrarMensaje('Este equipo ya no está autorizado para tomar asistencia.', 'error');
                claseEl.textContent = 'Pedile a la cuenta institucional que lo autorice de nuevo.';
                return;
            }
            // La sesion se vencio o se cerro desde otra pestaña. Llega como 401 con cuerpo
            // JSON --lo arma sesionVencidaEnApi-- y no como la redireccion al login, que
            // fetch seguiria hasta recibir HTML con estado 200: ahi resp.ok da true, el
            // resp.json() de abajo revienta, y el catch del final se lo come tomandolo por
            // un corte de red. Sin esto la camara seguia encendida mandando cuadros que no
            // registraban nada, sin decirlo.
            if (resp.status === 401) {
                detenerLoop();
                apagarCamara();
                mostrarMensaje('Se cerró la sesión. Volvé a entrar para seguir tomando asistencia.', 'error');
                claseEl.textContent = 'Las marcas que ya se registraron quedaron guardadas.';
                return;
            }
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
            ocultarProgreso();
            // Es el mensaje de rutina: no pisa un resultado que todavia se esta leyendo.
            if (Date.now() >= fijoHasta) {
                mostrarMensaje('No se detecta ningún rostro.', 'info');
                claseEl.textContent = '';
            }
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
            ocultarProgreso();
            mensajeParaLeer(data.mensaje, 'error');
            claseEl.textContent = '';
            avisarAlResto('error', data.mensaje);
            return;
        }

        // 2) reconocido pero todavia sosteniendo la identidad → CIAN, sin nombre
        //    El nombre no se muestra a proposito: si el reconocimiento esta oscilando entre
        //    dos personas parecidas, mostrarlo haria aparecer y desaparecer el nombre
        //    equivocado en pantalla, que es justo lo que este paso viene a evitar.
        if (data.confirmando) {
            dibujarRecuadro(data.x, data.y, data.ancho, data.alto, '#00acc1', null);
            var faltan = Math.max(0, Math.ceil((data.objetivoMs - data.progresoMs) / 1000));
            mostrarProgreso(data.progresoMs / data.objetivoMs, false);
            // Las otras ventanas ven el mismo avance, en una barra con forma de aviso.
            const e = estado();
            if (e) e.progreso(data.progresoMs / data.objetivoMs);
            mostrarMensaje('Sostené la posición… ' + faltan + ' s', 'info');
            claseEl.textContent = '';
            return;
        }

        // 3) reconocido + registrado → VERDE si entra, AZUL si sale
        if (data.asistenciaMarcada) {
            // Una entrada y una salida son hechos opuestos y no se pueden ver iguales: quien
            // opera mira la cara, no el texto, asi que la diferencia tiene que estar en el
            // recuadro. El azul quedo libre cuando "ya estaba marcado" dejo de existir en el
            // flujo normal: con bloques, la segunda pasada es la salida (RF-20, ADR-0017).
            const esSalida = data.tipoDeMarca === 'SALIDA';
            const color = esSalida ? tokenColor('--primary') : tokenColor('--success');
            const etiqueta = data.docenteNombre
                ? (esSalida ? 'SALE · ' : 'ENTRA · ') + data.docenteNombre
                : data.docenteNombre;
            dibujarRecuadro(data.x, data.y, data.ancho, data.alto, color, etiqueta);
            mostrarProgreso(1, true);
            mostrarMensaje(data.mensaje, esSalida ? 'info' : 'success');
            avisarAlResto(esSalida ? 'info' : 'success', data.mensaje, data.claseLabel);
            refrescarClases();
            claseEl.textContent = data.claseLabel || '';
            // Pausa breve para no bombardear el server con frames del mismo
            // docente que ya está marcado. Backend igual es idempotente.
            pausarLoopTrasMarcar(data.claseLabel, data.tipoDeMarca);
            return;
        }

        // 4) reconocido pero NO hay clase ahora → VERDE igual
        //    El recuadro responde a UNA sola pregunta: si el sistema reconocio a la
        //    persona. Y la reconocio. Que no haya clase en curso es otra cosa, y lo dice
        //    el mensaje; pintarlo de amarillo hacia parecer que el reconocimiento habia
        //    fallado, cuando el unico que fallo era el horario.
        dibujarRecuadro(data.x, data.y, data.ancho, data.alto, tokenColor('--success'), data.docenteNombre);
        ocultarProgreso();
        mensajeParaLeer(data.mensaje, 'warn');
        claseEl.textContent = '';
        avisarAlResto('warning', data.mensaje);
    }

    // El resultado del reconocimiento, para las ventanas que no estan mirando la camara.
    function avisarAlResto(clase, mensaje, detalle) {
        const e = estado();
        if (e) e.resultado(clase, mensaje, detalle);
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

    // ---- Clases de ahora -----------------------------------------------------

    // Cada cuanto se actualiza la lista mientras el pase anda. Solo mientras anda: con el
    // pase quieto, un pedido por minuto mantendria viva la sesion para siempre.
    const REFRESCO_CLASES_MS = 60000;
    let refrescoClasesId = null;

    async function refrescarClases() {
        const actual = document.getElementById('pa-clases');
        if (!actual) return;
        try {
            const resp = await fetch('/asistencia/pase/clases', { headers: { 'Accept': 'text/html' } });
            // Con la sesion vencida o el equipo desautorizado llega otra pantalla, detras de
            // una redireccion: eso no se pega en la tarjeta. Solo se reemplaza por la tarjeta.
            if (!resp.ok || resp.redirected) return;
            const html = await resp.text();
            if (html.indexOf('data-clases-de-ahora') === -1) return;
            actual.outerHTML = html;
        } catch (e) {
            /* Sin red: queda la lista anterior, y el proximo refresco lo reintenta. */
        }
    }

    // ---- Progreso de la confirmacion --------------------------------------

    // La barra muestra el dato del servidor --cuanto lleva sostenida la identidad y cuanto
    // hace falta--, no una animacion de relleno: si el docente se corre, la barra baja o se
    // va, que es justo lo que tiene que ver. Al marcar se llena de golpe y queda verde
    // durante la pausa.
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

    // ---- La camara se corta -----------------------------------------------

    // Definitivo: se desenchufo o se la llevo otra aplicacion. Se dice que paso y queda todo
    // listo para arrancar de nuevo con un clic.
    function camaraPerdida() {
        dejarDeVigilar = null;
        detenerLoop();
        limpiarOverlay();
        if (stream) {
            stream.getTracks().forEach(function (t) { t.stop(); });
            stream = null;
        }
        video.srcObject = null;
        camaraSinImagen = false;
        btnCamara.hidden = true;
        mostrarMensaje('Se perdió la cámara: se desconectó o la está usando otra aplicación.', 'error');
        claseEl.textContent = 'Revisá que esté conectada y apretá "Iniciar pase" para seguir.';
        publicar('apagado');
        avisarAlResto('error', 'Se perdió la cámara del pase.', 'Revisá que esté conectada.');
    }

    // Pausa: la camara sigue abierta pero no manda imagen. Se deja de enviar --llegarian
    // cuadros negros-- y se avisa; si vuelve, el pase sigue solo.
    function camaraSinImagenAviso() {
        camaraSinImagen = true;
        limpiarOverlay();
        ocultarProgreso();
        mostrarMensaje('La cámara dejó de mandar imagen. Si no vuelve en unos segundos, revisá la conexión.', 'warn');
        claseEl.textContent = '';
    }

    function camaraVolvio() {
        camaraSinImagen = false;
        mostrarMensaje(paseActivo ? 'Buscando rostros…' : 'Cámara encendida.', 'info');
    }

    // Resultado que hay que leer: se muestra ya y se sostiene LECTURA_MINIMA_MS.
    function mensajeParaLeer(texto, tipo) {
        mostrarMensaje(texto, tipo);
        fijoHasta = Date.now() + LECTURA_MINIMA_MS;
    }

    // ---- UI helpers -------------------------------------------------------

    // El recuadro se dibuja en un canvas, y ahi no llegan las variables CSS: hay que
    // pasarle un color ya resuelto. Se lee del documento en vez de repetir el hex
    // aca, para que siga al tema y a cualquier cambio de paleta. Se consulta en cada
    // dibujo y no una vez al cargar porque el tema se puede cambiar con el pase
    // andando.
    function tokenColor(nombre) {
        var v = getComputedStyle(document.documentElement).getPropertyValue(nombre).trim();
        return v || '#2e8b57';
    }

    // Los tipos que existen. Se listan para poder limpiarlos antes de poner el nuevo
    // sin tocar las otras clases del elemento.
    var TIPOS = ['success', 'error', 'warn', 'info'];

    function mostrarMensaje(texto, tipo) {
        // Cualquier mensaje puesto a proposito reemplaza al que se estaba sosteniendo.
        fijoHasta = 0;
        var antes = mensajeEl.className;

        mensajeEl.textContent = texto;
        TIPOS.forEach(function (t) {
            mensajeEl.classList.remove('pase__mensaje--' + t);
        });
        if (tipo) mensajeEl.classList.add('pase__mensaje--' + tipo);

        // El color salia de un hex escrito aca adentro (#4caf50, #e57373, #ffc107).
        // Eran colores de otra paleta, y al ser estilo inline el tema claro no los
        // podia corregir: sobre fondo blanco el verde del exito quedaba lavado.
        // Ahora el color lo decide la hoja de estilos, que si conoce los dos temas.

        // Un pase que confirma es el unico momento de la pantalla que merece
        // moverse: quien opera esta mirando la camara, no el panel, y necesita
        // saber de reojo que la marca entro. Solo al ENTRAR al estado, no en cada
        // repintado, para que no lata mientras el mensaje se mantiene.
        var yaEstaba = antes.indexOf('pase__mensaje--success') !== -1;
        if (tipo === 'success' && !yaEstaba) {
            mensajeEl.classList.remove('pase__mensaje--confirma');
            void mensajeEl.offsetWidth;              // reinicia la animacion
            mensajeEl.classList.add('pase__mensaje--confirma');
        }
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
    window.addEventListener('pagehide', function () {
        apagarCamara();
        // La ventana se va: el punto del menu se apaga ya, sin esperar a que el ultimo
        // estado publicado envejezca.
        publicar('cerrado');
    });

    // ---- La ventana aparte -------------------------------------------------

    // La ventana lleva nombre: abrirla dos veces trae la que ya esta, en vez de dejar dos
    // camaras peleandose por el mismo dispositivo.
    const VENTANA = 'visum-pase';
    const btnVentana = document.getElementById('pa-btn-ventana');

    if (btnVentana) {
        btnVentana.addEventListener('click', function () {
            const abierta = window.open('/asistencia/pase/ventana', VENTANA,
                                        'width=430,height=660,menubar=no,toolbar=no,location=no');
            if (!abierta) {
                mostrarMensaje('El navegador bloqueó la ventana del pase. Permitila y probá de nuevo.', 'error');
                return;
            }
            abierta.focus();
            if (bloqueadoPorOtra) return;        // ya estaba andando ahi: solo se la trae al frente
            // Esta pantalla le cede la camara: deja de publicar, la suelta y se va al inicio.
            cediendo = true;
            apagarCamara();
            window.location.href = '/';
        });
    }

    /*
     * Dos pases a la vez no pueden andar: la camara es una sola, y el segundo en pedirla no la
     * consigue. Si ya hay uno en otra ventana, esta pantalla lo dice en vez de dejar apretar
     * "Iniciar pase" para que falle. Se revisa cada tanto y no una sola vez al cargar, porque
     * la otra ventana se puede cerrar en cualquier momento.
     */
    function revisarOtraVentana() {
        if (esVentana || stream || paseActivo) return;     // esta pantalla ya esta usando la camara
        const hayOtra = !!(window.PaseEstado && window.PaseEstado.leer());
        if (hayOtra === bloqueadoPorOtra) return;

        bloqueadoPorOtra = hayOtra;
        btnPase.disabled = hayOtra;
        if (hayOtra) {
            mostrarMensaje('El pase ya está andando en otra ventana.', 'info');
            claseEl.textContent = 'Traela al frente para verlo, o cerrala para tomar asistencia desde acá.';
            if (btnVentana) btnVentana.textContent = 'Traer la ventana del pase al frente';
        } else {
            mostrarMensaje('Cámara apagada', 'info');
            claseEl.textContent = '';
            if (btnVentana) btnVentana.textContent = 'Seguir trabajando en otra pantalla';
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        revisarOtraVentana();
        setInterval(revisarOtraVentana, 2000);
        // La ventana chica se abre para tomar asistencia: arranca sola, sin pedir otro clic.
        if (document.body.dataset.autoIniciar === '1') togglePase();
    });
})();
