/* =============================================================================
 *  navbar.js
 *  Navbar adaptativa: el modo compacto (hamburguesa + drawer) se activa
 *  AUTOMATICAMENTE cuando los links no entran en el ancho disponible -
 *  NO usa breakpoints fijos. Se mide overflow real con scrollWidth y se
 *  toggle la clase body.nav-compact en cada resize.
 *
 *  Comportamiento:
 *   - Click en hamburguesa => abre/cierra el drawer (clase body.nav-open)
 *   - Click en backdrop    => cierra
 *   - Esc                  => cierra
 *   - Click en link interno => cierra (asi al navegar queda limpio)
 *   - Resize que ya entra todo => quita compact y cierra el drawer si estaba abierto
 *
 *  La logica de medicion:
 *   1. Quitamos temporalmente body.nav-compact (vuelve a layout desktop)
 *   2. Medimos navbar.scrollWidth vs navbar.clientWidth
 *   3. Si overflow => agregamos body.nav-compact (drawer mode)
 *   4. Si no => queda como esta (desktop mode)
 * ========================================================================== */

(function (window, document) {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var navbar   = document.querySelector('.navbar');
        var toggle   = document.querySelector('.navbar__toggle');
        var menu     = document.querySelector('.navbar__menu');
        var backdrop = document.querySelector('.navbar__backdrop');
        if (!navbar || !toggle || !menu) return;

        var body = document.body;

        // --- Toggle del drawer ---
        function setOpen(open) {
            body.classList.toggle('nav-open', open);
            toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        }

        toggle.addEventListener('click', function () {
            setOpen(!body.classList.contains('nav-open'));
        });

        if (backdrop) backdrop.addEventListener('click', function () { setOpen(false); });

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && body.classList.contains('nav-open')) {
                setOpen(false);
                toggle.focus();
            }
        });

        // Solo los links cierran el drawer. Los botones de grupo NO: sirven para
        // desplegar, y si cerraran el drawer nunca se llegaria a ver el submenu.
        menu.querySelectorAll('a').forEach(function (el) {
            el.addEventListener('click', function () {
                // Cerramos el drawer SIN animar: como el link navega a otra
                // pagina, no queremos ver el drawer deslizandose mientras carga.
                // nav-preload suprime la transicion; la pagina nueva ya arranca
                // limpia (script inline del layout).
                body.classList.add('nav-preload');
                setOpen(false);
            });
        });

        // --- Grupos desplegables ---
        var grupos = [].slice.call(menu.querySelectorAll('.navbar__grupo'));

        function cerrarGrupos(excepto) {
            grupos.forEach(function (g) {
                if (g === excepto) return;
                g.classList.remove('navbar__grupo--abierto');
                g.querySelector('.navbar__grupo-boton').setAttribute('aria-expanded', 'false');
            });
        }

        function abrirGrupo(grupo, boton) {
            grupo.classList.add('navbar__grupo--abierto');
            boton.setAttribute('aria-expanded', 'true');
            // Un solo grupo abierto a la vez: dos submenus superpuestos en la barra
            // se tapan entre si.
            cerrarGrupos(grupo);
        }

        var timerCierre = null;

        grupos.forEach(function (grupo) {
            var boton = grupo.querySelector('.navbar__grupo-boton');

            // El desplegable se abre al PASAR EL MOUSE, y el click navega a la pantalla
            // del grupo. Antes el click hacia las dos cosas, y por eso el grupo no era un
            // lugar al que se pudiera ir: la miga de pan lo nombraba y no llevaba a nada.
            //
            // El boton es un <a> con href, asi que si este script no corre el click sigue
            // navegando. Lo que se pierde sin JS es el desplegable, no el acceso.
            grupo.addEventListener('mouseenter', function () {
                clearTimeout(timerCierre);
                abrirGrupo(grupo, boton);
            });
            // Demora corta al salir: el submenu esta unos pixeles debajo del boton, y sin
            // ella el puntero lo cierra en el camino de uno al otro.
            grupo.addEventListener('mouseleave', function () {
                clearTimeout(timerCierre);
                timerCierre = setTimeout(function () { cerrarGrupos(null); }, 200);
            });

            // Con teclado no existe "pasar por encima": la flecha abajo abre el submenu
            // y desde ahi se tabula. Enter y Espacio quedan para el enlace, que navega.
            boton.addEventListener('keydown', function (e) {
                if (e.key === 'ArrowDown') {
                    e.preventDefault();
                    abrirGrupo(grupo, boton);
                    var primero = grupo.querySelector('.navbar__sublink');
                    if (primero) primero.focus();
                }
            });

            // En pantallas chicas el menu es un cajon lateral y no hay hover: ahi el
            // click tiene que poder abrir el grupo en vez de navegar.
            boton.addEventListener('click', function (e) {
                if (!document.body.classList.contains('nav-compact')) return;
                e.preventDefault();
                e.stopPropagation();
                var abierto = grupo.classList.contains('navbar__grupo--abierto');
                if (abierto) { cerrarGrupos(null); }
                else { abrirGrupo(grupo, boton); }
            });
        });

        // Click en cualquier otro lado cierra lo que este abierto, que es lo que
        // uno espera de un desplegable.
        document.addEventListener('click', function () { cerrarGrupos(null); });

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') cerrarGrupos(null);
        });

        // Marca el grupo que contiene la pantalla actual, para no perder de vista
        // donde uno esta parado ahora que los enlaces viven adentro del desplegable.
        (function marcarActivo() {
            var aqui = window.location.pathname;
            menu.querySelectorAll('.navbar__sublink, .navbar__link').forEach(function (a) {
                var destino = a.getAttribute('href');
                if (!destino) return;
                // Coincidencia exacta, o prefijo para las pantallas hijas
                // (/docentes/7/editar sigue siendo la seccion Docentes).
                var esAqui = destino === '/'
                    ? aqui === '/'
                    : aqui === destino || aqui.indexOf(destino + '/') === 0;
                if (!esAqui) return;

                a.classList.add('navbar__link--activo');
                var grupo = a.closest('.navbar__grupo');
                if (grupo) grupo.classList.add('navbar__grupo--activo');
            });
        })();

        // --- Deteccion de overflow ---
        function evaluateLayout() {
            // Quitamos compact para medir el "natural" desktop layout
            var wasCompact = body.classList.contains('nav-compact');
            if (wasCompact) body.classList.remove('nav-compact');

            // Forzar reflow antes de medir
            // eslint-disable-next-line no-unused-expressions
            void navbar.offsetWidth;

            // scrollWidth = ancho real del contenido; clientWidth = ancho visible.
            // Si scrollWidth > clientWidth, los hijos no entran y hay overflow horizontal.
            var overflow = navbar.scrollWidth > navbar.clientWidth + 1;

            if (overflow) {
                body.classList.add('nav-compact');
            } else if (body.classList.contains('nav-open')) {
                // Volvio a entrar todo: si el drawer estaba abierto, cerrarlo
                setOpen(false);
            }

            // Recordar la decision para que la proxima pagina arranque ya en
            // el modo correcto (script inline del layout) y no parpadee.
            try {
                sessionStorage.setItem('navCompact', overflow ? '1' : '0');
            } catch (e) { /* sin sessionStorage: no pasa nada */ }
        }

        var resizeTimer;
        function scheduleEvaluate() {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(evaluateLayout, 80);
        }

        // Initial run + resize
        evaluateLayout();

        // Reactivar las transiciones despues de pintar el estado inicial: el
        // doble requestAnimationFrame garantiza que el navegador ya pinto el
        // modo compacto sin animar, y a partir de aca las interacciones del
        // usuario (abrir/cerrar drawer) SI animan. Es idempotente.
        function quitarPreload() { body.classList.remove('nav-preload'); }

        requestAnimationFrame(function () {
            requestAnimationFrame(quitarPreload);
        });
        // Respaldo: si la pagina cargo con la pestana en segundo plano, rAF
        // queda pausado y nav-preload no se quitaria. El evento 'load' SI se
        // dispara con la pestana oculta, asi que garantiza que no quede pegado.
        window.addEventListener('load', quitarPreload);

        window.addEventListener('resize', scheduleEvaluate);

        // ResizeObserver es mas robusto: detecta cambios incluso si no hay resize
        // de window (ej: panel lateral del browser que aparece/desaparece).
        if (typeof ResizeObserver === 'function') {
            try {
                new ResizeObserver(scheduleEvaluate).observe(navbar);
            } catch (e) { /* navegador viejo, ignoramos */ }
        }
    });

})(window, document);
