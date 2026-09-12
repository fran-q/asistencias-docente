/*
 * La camara del puesto (V026).
 *
 * Las tres pantallas que capturan -el pase, el kiosco y el registro del rostro- piden la
 * camara a traves de aca y no directo a getUserMedia: asi las tres respetan la camara
 * elegida para este equipo, y las tres caen igual a la predeterminada cuando esa camara no
 * esta conectada.
 *
 * La camara elegida llega en el atributo data-camara del <video>. Si no esta, se usa la
 * predeterminada del sistema, que es lo que hacian antes.
 */
window.CamaraDelPuesto = (function () {
    'use strict';

    /**
     * Enciende la camara del puesto sobre ese <video> y devuelve el stream.
     *
     * Si la camara elegida no esta conectada, NO falla: usa la predeterminada y lo avisa en
     * pantalla. Una webcam USB desenchufada no puede dejar a un docente sin poder marcar.
     *
     * Los demas errores -permiso denegado, camara ocupada por otro programa- si se
     * propagan. Esconderlos tomando otra camara seria peor: la persona no sabria que hay
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
    // existe en esta maquina; algunos navegadores responden NotFoundError para el mismo caso.
    function esCamaraAusente(err) {
        return err && (err.name === 'OverconstrainedError' || err.name === 'NotFoundError');
    }

    // El aviso va pegado a la camara y no por el sistema de mensajes de cada pantalla: son
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

    return { abrir: abrir };
})();
