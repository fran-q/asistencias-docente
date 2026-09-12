/*
 * La eleccion de camara del puesto, en la pantalla de Puestos (V026).
 *
 * Lista las camaras que ve el navegador de ESTA maquina, deja probar la elegida antes de
 * guardarla, y manda al servidor el identificador y el nombre. Solo aparece en el equipo
 * autorizado: el identificador vale unicamente en este navegador.
 */
(function () {
    'use strict';

    const form = document.getElementById('form-camara');
    if (!form) return;

    const select    = document.getElementById('camara-dispositivo');
    const etiqueta  = document.getElementById('camara-etiqueta');
    const estado    = document.getElementById('camara-estado');
    const probar    = document.getElementById('camara-probar');
    const cajaVista = document.getElementById('camara-vista-caja');
    const vista     = document.getElementById('camara-vista');

    const actualId     = form.dataset.camaraActual || '';
    const actualNombre = form.dataset.camaraNombre || '';
    let streamVista = null;

    async function listar() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
            estado.textContent = 'Este navegador no permite elegir la cámara.';
            select.disabled = true;
            probar.disabled = true;
            return;
        }

        let camaras = await camarasConectadas();
        let sinNombres = null;

        // Sin permiso de camara el navegador lista los aparatos pero oculta sus nombres. Se
        // pide la camara un instante para destaparlos, y se suelta enseguida.
        if (camaras.length && camaras.every(function (c) { return !c.label; })) {
            try {
                const s = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
                s.getTracks().forEach(function (t) { t.stop(); });
                camaras = await camarasConectadas();
            } catch (e) {
                sinNombres = 'Para ver el nombre de cada una, el navegador tiene que tener '
                    + 'permiso de cámara.';
            }
        }

        camaras.forEach(function (c, i) {
            const nombre = c.label || ('Cámara ' + (i + 1));
            const op = document.createElement('option');
            op.value = c.deviceId;
            op.textContent = nombre;
            op.dataset.nombre = nombre;
            select.appendChild(op);
        });

        // La guardada puede no estar conectada ahora. Se la muestra igual, marcada, para que
        // quede claro que el equipo va a caer a la predeterminada hasta que se la enchufe.
        const conectada = camaras.some(function (c) { return c.deviceId === actualId; });
        if (actualId && !conectada) {
            const op = document.createElement('option');
            op.value = actualId;
            op.textContent = actualNombre + ' (no está conectada)';
            op.dataset.nombre = actualNombre;
            select.appendChild(op);
        }
        select.value = actualId;
        sincronizarEtiqueta();

        if (!camaras.length) {
            estado.textContent = 'No se encontró ninguna cámara conectada a este equipo.';
            return;
        }
        estado.textContent = (camaras.length === 1
            ? 'Hay 1 cámara conectada a este equipo.'
            : 'Hay ' + camaras.length + ' cámaras conectadas a este equipo.')
            + (sinNombres ? ' ' + sinNombres : '');
    }

    async function camarasConectadas() {
        const todos = await navigator.mediaDevices.enumerateDevices();
        return todos.filter(function (d) { return d.kind === 'videoinput'; });
    }

    // El nombre viaja en un campo oculto porque el servidor no puede averiguarlo: solo el
    // navegador sabe que "a3f9..." es la Logitech de la derecha.
    function sincronizarEtiqueta() {
        const op = select.options[select.selectedIndex];
        etiqueta.value = (op && op.value) ? (op.dataset.nombre || op.textContent) : '';
    }

    // Probar antes de guardar: con dos camaras del mismo modelo, o nombres genericos como
    // "USB Camera", mirar la imagen es la unica forma de saber cual es cual.
    async function mostrarVista() {
        detenerVista();
        const id = select.value;
        try {
            streamVista = await navigator.mediaDevices.getUserMedia({
                video: id ? { deviceId: { exact: id } } : true,
                audio: false
            });
            vista.srcObject = streamVista;
            cajaVista.hidden = false;
            probar.textContent = 'Dejar de probar';
        } catch (e) {
            const motivo = (e && e.name === 'NotReadableError') ? 'la está usando otro programa.'
                : (e && e.name === 'OverconstrainedError') ? 'no está conectada.'
                : 'el navegador no dio permiso de cámara.';
            estado.textContent = 'No se pudo abrir esa cámara: ' + motivo;
        }
    }

    function detenerVista() {
        if (streamVista) {
            streamVista.getTracks().forEach(function (t) { t.stop(); });
            streamVista = null;
        }
        vista.srcObject = null;
        cajaVista.hidden = true;
        probar.textContent = 'Probar';
    }

    select.addEventListener('change', function () {
        sincronizarEtiqueta();
        if (streamVista) mostrarVista();
    });
    probar.addEventListener('click', function () {
        if (streamVista) detenerVista(); else mostrarVista();
    });
    // Se suelta la camara antes de irse: si quedara tomada, el pase no la podria abrir.
    form.addEventListener('submit', detenerVista);
    window.addEventListener('pagehide', detenerVista);

    listar().catch(function () {
        estado.textContent = 'No se pudieron listar las cámaras de este equipo.';
    });
})();
