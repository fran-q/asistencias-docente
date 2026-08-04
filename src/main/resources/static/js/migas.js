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

    // Cada seccion: su etiqueta, el grupo del menu al que pertenece, y como se
    // llama cada paso siguiente. El grupo NO es un enlace: es donde vive la
    // pantalla dentro del menu, y sirve para ubicarse aunque no se pueda clickear.
    var MAPA = {
        'carreras':   { etiqueta: 'Carreras',           grupo: 'Académico' },
        'materias':   { etiqueta: 'Materias',           grupo: 'Académico' },
        'comisiones': { etiqueta: 'Comisiones',         grupo: 'Académico' },
        'horarios':   { etiqueta: 'Horarios',           grupo: 'Académico' },
        'grilla':     { etiqueta: 'Grilla semanal',     grupo: 'Académico' },
        'asistencias':{ etiqueta: 'Listado del día',    grupo: 'Asistencias' },
        'asistencia': { etiqueta: 'Asistencias',        grupo: 'Asistencias' },
        'reportes':   { etiqueta: 'Reportes',           grupo: 'Asistencias' },
        'docentes':   { etiqueta: 'Docentes',           grupo: 'Personal' },
        'usuarios':   { etiqueta: 'Usuarios',           grupo: 'Personal' },
        'mi-institucion': { etiqueta: 'Mi institución', grupo: 'Personal' },
        'mi-cuenta':  { etiqueta: 'Mi cuenta',          grupo: null }
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

        var seccion = MAPA[partes[0]];
        var items = [crumb('Inicio', '/', false)];

        if (seccion) {
            // El grupo solo se agrega si aporta algo. En /asistencia/pase el grupo y la
            // seccion se llaman igual, y la ruta salia "Inicio / Asistencias / Asistencias".
            if (seccion.grupo && seccion.grupo !== seccion.etiqueta) {
                items.push(crumb(seccion.grupo, null, false));
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
