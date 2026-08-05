/* =============================================================================
 *  migas.js
 *
 *  Migas de pan: la ruta desde el inicio hasta donde estas parado.
 *
 *  Se arman en el navegador a partir de la URL, no las publica cada controlador.
 *  El motivo es el mismo por el que el resaltado del menu tambien se hace aca:
 *  la direccion ya dice donde estas, y pedirle el dato a veinte controladores
 *  seria repetir en veinte lugares algo que se puede deducir una vez. El costo
 *  de eso --que la ruta salga mal en una pantalla nueva-- se paga agregando una
 *  linea a este mapa, no tocando el controlador.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    // Cada seccion: su etiqueta y el grupo del menu al que pertenece. El grupo AHORA
    // es un enlace: cada uno tiene su pantalla, y a donde lleva lo decide el servidor
    // segun el rol --para el ADMIN, Personal va derecho a Docentes--. La clave del
    // grupo es la que se busca en los data-* del contenedor.
    var MAPA = {
        'carreras':   { etiqueta: 'Carreras',           grupo: 'Académico',   clave: 'academico' },
        'materias':   { etiqueta: 'Materias',           grupo: 'Académico',   clave: 'academico' },
        'comisiones': { etiqueta: 'Comisiones',         grupo: 'Académico',   clave: 'academico' },
        'horarios':   { etiqueta: 'Horarios',           grupo: 'Académico',   clave: 'academico' },
        'grilla':     { etiqueta: 'Grilla semanal',     grupo: 'Académico',   clave: 'academico' },
        'academico':  { etiqueta: 'Académico',          grupo: null,          clave: null },
        'asistencias':{ etiqueta: 'Listado del día',    grupo: 'Asistencias', clave: 'asistencia' },
        'asistencia': { etiqueta: 'Asistencias',        grupo: 'Asistencias', clave: 'asistencia' },
        'reportes':   { etiqueta: 'Reportes',           grupo: 'Asistencias', clave: 'asistencia' },
        'docentes':   { etiqueta: 'Docentes',           grupo: 'Personal',    clave: 'personal' },
        'usuarios':   { etiqueta: 'Usuarios',           grupo: 'Personal',    clave: 'personal' },
        'mi-institucion': { etiqueta: 'Mi institución', grupo: 'Personal',    clave: 'personal' },
        'personal':   { etiqueta: 'Personal',           grupo: null,          clave: null },
        'mi-cuenta':  { etiqueta: 'Mi cuenta',          grupo: null,          clave: null }
    };

    // Ultimo tramo de la ruta: que se esta haciendo sobre el registro.
    var ACCIONES = {
        'nueva': 'Nueva', 'nuevo': 'Nuevo', 'editar': 'Editar',
        'pase': 'Pase de asistencia', 'manual': 'Carga manual',
        'justificar': 'Justificar ausencia', 'rostro': 'Registro del rostro',
        'registrar': 'Registrar', 'consentimiento': 'Consentimiento',
        'ficha': 'Ficha del docente', 'constancia': 'Constancia',
        'otorgar': 'Otorgar', 'revocar': 'Revocar', 'password': 'Cambiar contraseña'
    };

    function crumb(texto, href, esUltimo) {
        var li = document.createElement('li');
        li.className = 'migas__item';
        if (esUltimo) {
            li.textContent = texto;
            // aria-current le dice al lector de pantalla cual es la pagina actual;
            // sin eso lee una lista de nombres sin saber en cual esta.
            li.setAttribute('aria-current', 'page');
        } else if (href) {
            var a = document.createElement('a');
            a.href = href;
            a.textContent = texto;
            li.appendChild(a);
        } else {
            // Los grupos del menu no tienen pantalla propia: se muestran apagados
            // en vez de como un enlace que no lleva a ningun lado.
            li.textContent = texto;
            li.classList.add('migas__item--grupo');
        }
        return li;
    }

    function construir() {
        var cont = document.getElementById('migas');
        if (!cont) return;

        var partes = window.location.pathname.split('/').filter(Boolean);

        // En el inicio no se dibuja nada: una miga sola que dice "Inicio" cuando
        // ya estas en el inicio no informa nada y solo ocupa una franja.
        if (partes.length === 0) {
            cont.parentElement.hidden = true;
            return;
        }

        var nav = cont.closest('.migas');
        var seccion = MAPA[partes[0]];
        var items = [crumb('Inicio', '/', false)];

        if (seccion) {
            // El destino del grupo lo pone el servidor porque depende del rol. Si no
            // viniera --por ejemplo si la barra no se renderizo-- el paso se muestra
            // igual sin enlace: se pierde el atajo, no la ubicacion.
            var destino = nav && seccion.clave ? nav.dataset[seccion.clave] : null;
            var rutaSeccion = '/' + partes[0];

            // El grupo se agrega solo si aporta algo, y hay dos formas de no aportar:
            //
            //   1. Que se llame igual que la seccion. En /asistencia/pase la ruta salia
            //      "Inicio / Asistencias / Asistencias".
            //   2. Que lleve al MISMO lugar que el paso siguiente. Le pasa al rol ADMIN
            //      en Personal, que al tener una sola pantalla va derecho a Docentes:
            //      quedaba "Personal → /docentes / Docentes → /docentes", dos pasos
            //      distintos para un unico destino.
            var aportaAlgo = seccion.grupo
                && seccion.grupo !== seccion.etiqueta
                && destino !== rutaSeccion;
            if (aportaAlgo) {
                items.push(crumb(seccion.grupo, destino || null, false));
            }
            items.push(crumb(seccion.etiqueta, '/' + partes[0], partes.length === 1));
        } else {
            items.push(crumb(partes[0], null, partes.length === 1));
        }

        // Del resto solo interesan las acciones; los ids numericos no se muestran
        // porque el numero de fila no le dice nada a quien esta leyendo la ruta.
        var restantes = partes.slice(1).filter(function (t) { return ACCIONES[t]; });
        restantes.forEach(function (t, i) {
            items.push(crumb(ACCIONES[t], null, i === restantes.length - 1));
        });

        // Si no hubo ninguna accion, el ultimo que quedo es la pagina actual.
        if (restantes.length === 0) {
            var ult = items[items.length - 1];
            ult.setAttribute('aria-current', 'page');
        }

        items.forEach(function (li) { cont.appendChild(li); });
    }

    document.addEventListener('DOMContentLoaded', construir);

})(window, document);
