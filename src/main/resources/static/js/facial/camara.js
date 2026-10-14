/*
 * La cámara del puesto (V026).
 *
 * Las tres pantallas que capturan -el pase, el kiosco y el registro del rostro- piden la
 * cámara a través de acá y no directo a getUserMedia: así las tres respetan la cámara
 * elegida para este equipo, y las tres caen igual a la predeterminada cuando esa cámara no
 * está conectada.
 *
 * La cámara elegida llega en el atributo data-camara del <video>. Si no esta, se usa la
 * predeterminada del sistema, que es lo que hacian antes.
 */
window.CamaraDelPuesto = (function () {
    'use strict';

    /**
     * Enciende la cámara del puesto sobre ese <video> y devuelve el stream.
     *
     * Si la cámara elegida no está conectada, NO falla: usa la predeterminada y lo avisa en
     * pantalla. Una webcam USB desenchufada no puede dejar a un docente sin poder marcar.
     *
     * Los demás errores -permiso denegado, cámara ocupada por otro programa- si se
     * propagan. Esconderlos tomando otra cámara sería peor: la persona no sabría que hay
     * algo que resolver.
     */
    async function abrir(video, restricciones) {
        const base = Object.assign({}, restricciones || {});
        const elegida = (video && video.dataset) ? video.dataset.camara : null;

        if (elegida) {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({
                    video: Object.assign({}, base, { deviceId: { exact: elegida } }),
                    audio: false
                });
                quitarAviso(video);
                return stream;
            } catch (err) {
                if (!esCamaraAusente(err)) throw err;
                avisar(video, 'La cámara elegida para este equipo no está conectada. '
                    + 'Se está usando la predeterminada del sistema.');
            }
        }
        return navigator.mediaDevices.getUserMedia({ video: base, audio: false });
    }

    // OverconstrainedError es lo que devuelve el navegador cuando el deviceId exacto no
    // existe en esta máquina; algunos navegadores responden NotFoundError para el mismo caso.
    function esCamaraAusente(err) {
        return err && (err.name === 'OverconstrainedError' || err.name === 'NotFoundError');
    }

    // El aviso va pegado a la cámara y no por el sistema de mensajes de cada pantalla: son
    // tres pantallas con tres sistemas distintos, y este aviso tiene que verse en las tres.
    function avisar(video, texto) {
        const caja = video.closest('.camara-caja') || video;
        let aviso = caja.parentNode.querySelector('.camara-aviso');
        if (!aviso) {
            aviso = document.createElement('p');
            aviso.className = 'alert alert--warning camara-aviso';
            aviso.setAttribute('role', 'status');
            caja.insertAdjacentElement('afterend', aviso);
        }
        aviso.textContent = texto;
    }

    function quitarAviso(video) {
        const caja = video.closest('.camara-caja') || video;
        const aviso = caja.parentNode.querySelector('.camara-aviso');
        if (aviso) aviso.remove();
    }

    // Un silencio de un instante es normal --el sistema reajusta la cámara al abrirla o al
    // cambiar la luz--: se avisa recién si dura esto.
    const SILENCIO_TOLERADO_MS = 3000;

    /**
     * Avisa si la cámara deja de dar imagen con el stream ya abierto.
     *
     * Son dos casos distintos. 'ended' es definitivo: se desenchufó, el sistema la cortó u
     * otra aplicación se la llevó, y hay que volver a abrirla. 'mute' es una pausa: el
     * dispositivo dejó de mandar cuadros y a veces vuelve solo. Sin esto los dos se veían
     * igual que "no hay nadie adelante": la pantalla seguía diciendo que no detectaba ningún
     * rostro con la cámara muerta, y quien operaba podía tardar minutos en darse cuenta.
     *
     *   avisos.perdida   - el stream ya no sirve.
     *   avisos.sinImagen - lleva unos segundos sin cuadros; puede volver.
     *   avisos.volvio    - después de sinImagen, la imagen volvió.
     *
     * Devuelve la función que deja de vigilar, para cuando se apaga la cámara a propósito.
     * stop() no dispara 'ended', pero así ninguna pantalla depende de ese detalle.
     */
    function vigilar(stream, avisos) {
        const pistas = stream ? stream.getVideoTracks() : [];
        let espera = null;
        let sinImagen = false;
        let activo = true;

        function alTerminar() {
            if (!activo) return;
            dejarDeVigilar();
            if (avisos.perdida) avisos.perdida();
        }
        function alSilenciar() {
            if (!activo) return;
            clearTimeout(espera);
            espera = setTimeout(function () {
                sinImagen = true;
                if (avisos.sinImagen) avisos.sinImagen();
            }, SILENCIO_TOLERADO_MS);
        }
        function alVolver() {
            clearTimeout(espera);
            if (activo && sinImagen && avisos.volvio) avisos.volvio();
            sinImagen = false;
        }
        function dejarDeVigilar() {
            activo = false;
            clearTimeout(espera);
            pistas.forEach(function (p) {
                p.removeEventListener('ended', alTerminar);
                p.removeEventListener('mute', alSilenciar);
                p.removeEventListener('unmute', alVolver);
            });
        }

        pistas.forEach(function (p) {
            p.addEventListener('ended', alTerminar);
            p.addEventListener('mute', alSilenciar);
            p.addEventListener('unmute', alVolver);
        });
        return dejarDeVigilar;
    }

    return { abrir: abrir, vigilar: vigilar };
})();
