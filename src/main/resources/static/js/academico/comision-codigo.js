/* =============================================================================
 *  comision-codigo.js
 *
 *  Sugiere el código de una comisión nueva cuando ya se eligió la materia y el
 *  período.
 *
 *  Por qué hace falta. El código es único por (materia, período), así que para
 *  saber cuál esta libre hay que mirar lo que ya existe. Antes eso se hacía a
 *  mano: abrir el listado, filtrar por la materia, ver qué había, volver al
 *  formulario y escribir la siguiente. El servidor ya sabe la respuesta.
 *
 *  Qué NO hace. No pisa lo que la persona escribió. En cuanto el campo tiene
 *  algo que no puso este script, deja de sugerir para siempre: el código suele
 *  ser texto --"Mañana", "Noche"-- y una sugerencia que sobreescribe eso es
 *  peor que no tener sugerencia.
 *
 *  Si el script no corre, el formulario anda igual: el campo queda vacío y se
 *  escribe a mano, que es como funcionaba hasta ahora.
 * ========================================================================== */
(function () {
    'use strict';

    var form = document.querySelector('form[data-codigo-sugerido]');
    if (!form) return;

    var campo = form.querySelector('#codigo');
    var materia = form.querySelector('#materiaId');
    var periodo = form.querySelector('#periodoId');
    if (!campo || !materia || !periodo) return;

    // Lo último que escribimos nosotros. Sirve para distinguir "el campo tiene
    // una sugerencia vieja" de "la persona escribió algo", que es lo único que
    // no se puede tocar.
    var sugerido = '';
    var pedido = 0;

    function loEscribioLaPersona() {
        var actual = campo.value.trim();
        return actual !== '' && actual !== sugerido;
    }

    function sugerir() {
        if (loEscribioLaPersona()) return;
        if (!materia.value || !periodo.value) {
            if (!loEscribioLaPersona()) { campo.value = ''; sugerido = ''; }
            return;
        }

        // Cada pedido lleva su número: si se cambia la materia dos veces rápido,
        // la respuesta vieja no puede pisar a la nueva.
        var mio = ++pedido;
        var url = form.getAttribute('data-codigo-sugerido')
            + '?materiaId=' + encodeURIComponent(materia.value)
            + '&periodoId=' + encodeURIComponent(periodo.value);

        fetch(url, { headers: { 'Accept': 'text/plain' } })
            .then(function (r) { return r.ok ? r.text() : ''; })
            .then(function (texto) {
                if (mio !== pedido) return;          // llegó tarde
                if (loEscribioLaPersona()) return;   // escribió mientras tanto
                sugerido = (texto || '').trim();
                campo.value = sugerido;
            })
            .catch(function () {
                // Sin red o con el servidor caído no se avisa nada: esto es una
                // comodidad, y el campo se puede escribir igual.
            });
    }

    materia.addEventListener('change', sugerir);
    periodo.addEventListener('change', sugerir);

    // Al abrir, por si el formulario ya viene con una materia elegida.
    sugerir();
})();
