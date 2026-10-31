/* =============================================================================
 *  form-al-cambiar.js
 *
 *  Lo que hacen algunos combos al cambiar de valor. Vive acá y no en un atributo
 *  onchange porque la política de contenido (PoliticaDeContenidoFilter) no deja
 *  correr código escrito en el HTML: el combo seguiría viéndose bien y dejaría de
 *  hacer lo suyo sin ningún error a la vista.
 *
 *   - data-enviar-al-cambiar: manda su formulario.
 *   - data-accion-de y data-accion-plantilla: cambia el destino de otro formulario.
 *     En la plantilla, "{id}" se reemplaza por el valor elegido.
 * ========================================================================== */

(function (document) {
    'use strict';

    document.addEventListener('change', function (evento) {
        var campo = evento.target;
        if (!campo || !campo.hasAttribute) return;

        if (campo.hasAttribute('data-enviar-al-cambiar') && campo.form) {
            campo.form.submit();
            return;
        }

        var destino = campo.getAttribute('data-accion-de');
        var plantilla = campo.getAttribute('data-accion-plantilla');
        if (destino && plantilla) {
            var formulario = document.getElementById(destino);
            if (formulario) {
                formulario.action = plantilla.replace('{id}', encodeURIComponent(campo.value));
            }
        }
    });
})(document);
