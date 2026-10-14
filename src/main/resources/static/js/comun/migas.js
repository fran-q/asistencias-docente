/* =============================================================================
 *  migas.js
 *
 *  Migas de pan: la ruta desde el inicio hasta donde estás parado.
 *
 *  Se arman en el navegador a partir de la URL, no las publica cada controlador.
 *  El motivo es el mismo por el que el resaltado del menú también se hace acá:
 *  la dirección ya dice dónde estás, y pedirle el dato a veinte controladores
 *  sería repetir en veinte lugares algo que se puede deducir una vez. El costo
 *  de eso --que la ruta salga mal en una pantalla nueva-- se paga agregando una
 *  línea a este mapa, no tocando el controlador.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    // Cada sección: su etiqueta y el grupo del menú al que pertenece. El grupo AHORA
    // es un enlace: cada uno tiene su pantalla, y a donde lleva lo decide el servidor
    // según el rol --para el ADMIN, Personal va derecho a Docentes--. La clave del
    // grupo es la que se busca en los data-* del contenedor.
    var MAPA = {
        'ciclos':     { etiqueta: 'Ciclos lectivos',    grupo: 'Académico',   clave: 'academico' },
        'dias-sin-clase': { etiqueta: 'Días sin clase', grupo: 'Académico',   clave: 'academico' },
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
        'usuarios':   { etiqueta: 'Usuarios del sistema', grupo: 'Personal',    clave: 'personal' },
        'mi-institucion': { etiqueta: 'Mi institución', grupo: 'Personal',    clave: 'personal' },
        'puestos':    { etiqueta: 'Puestos de captura', grupo: 'Personal',    clave: 'personal' },
        // A esta no se llega por el menú sino chocándose con el bloqueo, así que no
        // cuelga de ningún grupo: la miga solo tiene que decir dónde está parado.
        'puesto-requerido': { etiqueta: 'Puesto no autorizado', grupo: null, clave: null },
        'personal':   { etiqueta: 'Personal',           grupo: null,          clave: null },
        'mi-cuenta':  { etiqueta: 'Mi cuenta',          grupo: null,          clave: null }
    };

    // Último tramo de la ruta: qué se está haciendo sobre el registro.
    var ACCIONES = {
        'nueva': 'Nueva', 'nuevo': 'Nuevo', 'editar': 'Editar',
        'pase': 'Pase de asistencia', 'manual': 'Carga manual',
        'justificar': 'Justificar ausencia', 'rostro': 'Registro del rostro',
        'registrar': 'Registrar', 'consentimiento': 'Consentimiento',
        'constancia': 'Constancia',
        'otorgar': 'Otorgar', 'revocar': 'Revocar', 'password': 'Cambiar contraseña',
        // Vistas de un registro hacia sus hijos: /carreras/{id}/materias y
        // /materias/{id}/comisiones. Sin estas entradas la miga se cortaba en el padre
        // y no decía donde estaba parado quien las abría.
        'materias': 'Materias de la carrera',
        'comisiones': 'Comisiones de la materia'
    };

    function crumb(texto, href, esUltimo) {
        var li = document.createElement('li');
        li.className = 'migas__item';
        if (esUltimo) {
            li.textContent = texto;
            // aria-current le dice al lector de pantalla cuál es la página actual;
            // sin eso lee una lista de nombres sin saber en cuál esta.
            li.setAttribute('aria-current', 'page');
        } else if (href) {
            var a = document.createElement('a');
            a.href = href;
            a.textContent = texto;
            li.appendChild(a);
        } else {
            // Los grupos del menú no tienen pantalla propia: se muestran apagados
            // en vez de como un enlace que no lleva a ningún lado.
            li.textContent = texto;
            li.classList.add('migas__item--grupo');
        }
        return li;
    }

    // El título de la pantalla, para cuando la ruta no alcanza para nombrarla.
    function tituloDeLaPantalla() {
        var h1 = document.querySelector('#contenido h1');
        var texto = h1 ? h1.textContent.replace(/\s+/g, ' ').trim() : '';
        return texto || null;
    }

    function construir() {
        var cont = document.getElementById('migas');
        if (!cont) return;

        var partes = window.location.pathname.split('/').filter(Boolean);

        // En el inicio no se dibuja nada: una miga sola que dice "Inicio" cuando
        // ya estás en el inicio no informa nada y solo ocupa una franja.
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
            // igual sin enlace: se pierde el atajo, no la ubicación.
            var destino = nav && seccion.clave ? nav.dataset[seccion.clave] : null;
            var rutaSeccion = '/' + partes[0];

            // El grupo se agrega solo si aporta algo, y hay dos formas de no aportar:
            //
            //   1. Que se llame igual que la sección. En /asistencia/pase la ruta salía
            //      "Inicio / Asistencias / Asistencias".
            //   2. Que lleve al MISMO lugar que el paso siguiente. Le pasa al rol ADMIN
            //      en Personal, que al tener una sola pantalla va derecho a Docentes:
            //      quedaba "Personal → /docentes / Docentes → /docentes", dos pasos
            //      distintos para un único destino.
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

        // Del resto solo interesan las acciones; los ids numéricos no se muestran
        // porque el número de fila no le dice nada a quien esta leyendo la ruta.
        var restantes = partes.slice(1).filter(function (t) { return ACCIONES[t]; });
        restantes.forEach(function (t, i) {
            items.push(crumb(ACCIONES[t], null, i === restantes.length - 1));
        });

        // Sin ninguna acción hay dos casos. Con la sección sola (/docentes), la sección es
        // la página actual. Con algo más en la ruta que el mapa no nombra --un registro,
        // /ciclos/5, o una pantalla propia, /asistencias/bloques/pendientes-- la página
        // actual es otra, y la ruta sola no sabe como se llama: se toma del título de la
        // pantalla. Antes la miga terminaba en la sección y decía "estás en Ciclos
        // lectivos" con el ciclo 2026 abierto.
        if (restantes.length === 0) {
            var titulo = partes.length > 1 ? tituloDeLaPantalla() : null;
            if (titulo) {
                items.push(crumb(titulo, null, true));
            } else {
                items[items.length - 1].setAttribute('aria-current', 'page');
            }
        }

        items.forEach(function (li) { cont.appendChild(li); });
    }

    document.addEventListener('DOMContentLoaded', construir);

})(window, document);
