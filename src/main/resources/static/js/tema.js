/* =============================================================================
 *  tema.js
 *
 *  Conmuta entre modo claro y oscuro (RNF-22).
 *
 *  Aca SOLO vive el boton. Quien aplica el tema al cargar es el script inline
 *  del <head>: tiene que correr antes del primer paint, y este archivo va con
 *  defer, o sea despues. Si se aplicara desde aca, cada pagina se dibujaria
 *  oscura y saltaria a clara un instante despues.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    var boton = document.getElementById('tema-toggle');
    if (!boton) return;

    boton.addEventListener('click', function () {
        var actual = document.documentElement.getAttribute('data-tema');
        var nuevo  = actual === 'claro' ? 'oscuro' : 'claro';
        document.documentElement.setAttribute('data-tema', nuevo);
        // Si el navegador no deja guardar (modo privado), el tema vale para esta
        // pantalla y se pierde al navegar. Es peor no poder cambiarlo.
        try { localStorage.setItem('tema', nuevo); } catch (e) { /* ignorado */ }
    });

})(window, document);
