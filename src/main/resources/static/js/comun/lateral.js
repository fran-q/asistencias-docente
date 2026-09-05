/* =============================================================================
 *  lateral.js — Barra lateral: cajón en pantallas chicas y colapso en escritorio
 *
 *  Dos comportamientos, un solo archivo, porque son dos caras de lo mismo: qué
 *  tanto de la navegación está a la vista.
 *
 *    · Cajón (menos de 1000 px). La barra sale del flujo y entra deslizándose
 *      sobre el contenido, con un velo detrás. La abre la hamburguesa de la
 *      barra superior.
 *
 *    · Colapso (1000 px o más). La barra se angosta a 70 px y deja sólo los
 *      iconos. La elección se guarda: en un monitor de 1366 px, ganar 200 px de
 *      ancho para la tabla es la diferencia entre ver la columna de acciones y
 *      no verla, y no tiene sentido volver a pedirlo en cada pantalla.
 *
 *  Nada de esto mide anchos con JavaScript. El modo lo decide una media query
 *  del CSS, así que no hay carrera contra el primer paint como la que había con
 *  el navbar horizontal, que medía el overflow real y a veces alcanzaba a
 *  dibujarse desplegado antes de saltar al cajón.
 *
 *  Lo que sí hace este archivo es marcar el enlace de la pantalla actual, que
 *  antes resolvía el mismo script de las migas para el navbar.
 * ========================================================================== */
(function () {
    'use strict';

    var raiz     = document.documentElement;
    var lateral  = document.getElementById('lateral');
    if (!lateral) return;

    var abrir    = document.getElementById('lateral-abrir');
    var colapsar = document.getElementById('lateral-colapsar');
    var velos    = document.querySelectorAll('[data-cerrar-cajon]');

    function guardar(clave, valor) {
        try { localStorage.setItem(clave, valor); } catch (e) { /* modo privado */ }
    }

    /* ---- Cajón ---- */
    function abrirCajon() {
        raiz.setAttribute('data-cajon', 'abierto');
        if (abrir) abrir.setAttribute('aria-expanded', 'true');
        /* El foco entra al cajón: si se quedara en la hamburguesa, tabular
           seguiría recorriendo la barra superior por detrás del velo. */
        var primero = lateral.querySelector('a, button');
        if (primero) primero.focus();
    }

    function cerrarCajon(devolverFoco) {
        if (raiz.getAttribute('data-cajon') !== 'abierto') return;
        raiz.setAttribute('data-cajon', 'cerrado');
        if (abrir) {
            abrir.setAttribute('aria-expanded', 'false');
            if (devolverFoco) abrir.focus();
        }
    }

    if (abrir) abrir.addEventListener('click', abrirCajon);

    Array.prototype.forEach.call(velos, function (velo) {
        velo.addEventListener('click', function () { cerrarCajon(true); });
    });

    /* Escape cierra, que es lo que espera cualquiera que abrió algo encima del
       contenido. */
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') cerrarCajon(true);
    });

    /* Al elegir un destino el cajón se cierra solo. En una navegación normal la
       página se recarga y da igual, pero si el enlace es un ancla o el destino
       tarda, quedaba abierto tapando lo que se acababa de pedir. */
    lateral.addEventListener('click', function (e) {
        if (e.target.closest('a')) cerrarCajon(false);
    });

    /* ---- Colapso ---- */
    if (colapsar) {
        colapsar.addEventListener('click', function () {
            var cerrado = raiz.getAttribute('data-lateral') === 'cerrado';
            var nuevo   = cerrado ? 'abierto' : 'cerrado';
            raiz.setAttribute('data-lateral', nuevo);
            guardar('lateral', nuevo);
            colapsar.setAttribute('aria-expanded', cerrado ? 'true' : 'false');

            /* Colapsada, el texto del botón no se ve, así que el título del
               tooltip pasa a ser la única forma de saber qué hace. */
            colapsar.title = cerrado ? 'Colapsar menú' : 'Expandir menú';
        });

        if (raiz.getAttribute('data-lateral') === 'cerrado') {
            colapsar.setAttribute('aria-expanded', 'false');
            colapsar.title = 'Expandir menú';
        }
    }

    /* ---- Enlace de la pantalla actual ----
       Se compara por prefijo de ruta y no por igualdad: /docentes/12/editar
       tiene que marcar Docentes. Gana el prefijo más largo, así /asistencia/pase
       no marca también /asistencias.

       aria-current además de la clase: la clase es para el ojo, aria-current es
       lo que anuncia un lector de pantalla al recorrer el menú. */
    (function marcarActivo() {
        var actual  = window.location.pathname;
        var enlaces = lateral.querySelectorAll('.lateral__link');
        var mejor   = null;
        var largo   = -1;

        Array.prototype.forEach.call(enlaces, function (a) {
            var ruta = a.getAttribute('href') || '';
            if (ruta === '/' ) {
                if (actual === '/' && largo < 0) { mejor = a; largo = 0; }
                return;
            }
            if (actual === ruta || actual.indexOf(ruta + '/') === 0) {
                if (ruta.length > largo) { mejor = a; largo = ruta.length; }
            }
        });

        if (mejor) {
            mejor.classList.add('lateral__link--activo');
            mejor.setAttribute('aria-current', 'page');
        }
    })();
})();
