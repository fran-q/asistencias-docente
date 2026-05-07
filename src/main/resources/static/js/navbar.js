/* =============================================================================
 *  navbar.js
 *  Toggle del menu hamburguesa para pantallas chicas (<= 900px).
 *
 *  HTML esperado en layout/base.html:
 *   <button class="navbar__toggle" aria-controls="navbar-menu"
 *           aria-expanded="false">...</button>
 *   <nav class="navbar__menu" id="navbar-menu">...</nav>
 *   <div class="navbar__backdrop"></div>
 *
 *  Comportamiento:
 *   - Click en hamburguesa => abre/cierra el drawer (clase body.nav-open)
 *   - Click en backdrop    => cierra
 *   - Esc                  => cierra
 *   - Click en cualquier link del drawer => cierra (asi al navegar queda limpio)
 *   - Resize a >= 901px    => cierra (por si quedo abierto al ensanchar)
 *
 *  La transicion visual la maneja el CSS (transform translateX).
 * ========================================================================== */

(function (window, document) {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var toggle   = document.querySelector('.navbar__toggle');
        var menu     = document.querySelector('.navbar__menu');
        var backdrop = document.querySelector('.navbar__backdrop');
        if (!toggle || !menu) return;

        var body = document.body;

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

        // Cerrar al click en cualquier link interno del drawer
        menu.querySelectorAll('a, button').forEach(function (el) {
            el.addEventListener('click', function () { setOpen(false); });
        });

        // Si el viewport supera el breakpoint, asegurarse de cerrar
        window.addEventListener('resize', function () {
            if (window.innerWidth >= 901 && body.classList.contains('nav-open')) {
                setOpen(false);
            }
        });
    });

})(window, document);
