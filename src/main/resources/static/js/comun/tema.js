/* =============================================================================
 *  tema.js
 *
 *  Conmuta entre modo claro y oscuro (RNF-22).
 *
 *  Aca SOLO vive el boton. Quien aplica el tema al cargar es el script inline
 *  del <head>: tiene que correr antes del primer paint, y este archivo va con
 *  defer, o sea despues. Si se aplicara desde aca, cada pagina se dibujaria
 *  oscura y saltaria a clara un instante despues.
 *
 *  El cambio no es instantaneo: un tema se funde en el otro. Cuanto dura y como
 *  se ve vive en main.css ("Cambio de tema"); aca solo se elige el camino.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    var boton = document.getElementById('tema-toggle');
    if (!boton) return;

    var raiz = document.documentElement;
    var fin = null;

    function aplicar(tema) {
        raiz.setAttribute('data-tema', tema);
        // Si el navegador no deja guardar (modo privado), el tema vale para esta
        // pantalla y se pierde al navegar. Es peor no poder cambiarlo.
        try { localStorage.setItem('tema', tema); } catch (e) { /* ignorado */ }
    }

    // Se lee de la hoja y no se repite aca: si alguien ajusta la duracion en
    // main.css, la clase del camino alternativo se sigue quitando a tiempo.
    function duracionMs() {
        return parseFloat(getComputedStyle(raiz).getPropertyValue('--t-tema')) || 0;
    }

    boton.addEventListener('click', function () {
        var nuevo = raiz.getAttribute('data-tema') === 'claro' ? 'oscuro' : 'claro';

        if (document.startViewTransition) {
            document.startViewTransition(function () { aplicar(nuevo); });
            return;
        }

        // Sin transiciones de vista: la clase habilita el fundido de colores y se
        // quita al terminar, para que el hover recupere sus 120ms. Con un
        // temporizador y no con transitionend, que llega una vez por elemento y
        // por propiedad: el primero que llegara cortaria el fundido de los demas.
        raiz.classList.add('tema-cambiando');
        aplicar(nuevo);
        clearTimeout(fin);
        fin = setTimeout(function () {
            raiz.classList.remove('tema-cambiando');
        }, duracionMs() + 50);
    });

})(window, document);
