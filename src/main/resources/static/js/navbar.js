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

        menu.querySelectorAll('a, button').forEach(function (el) {
            el.addEventListener('click', function () {
                // Cerramos el drawer SIN animar: como el link navega a otra
                // pagina, no queremos ver el drawer deslizandose mientras carga.
                // nav-preload suprime la transicion; la pagina nueva ya arranca
                // limpia (script inline del layout).
                body.classList.add('nav-preload');
                setOpen(false);
            });
        });

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
