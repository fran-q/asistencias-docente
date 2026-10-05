/* =============================================================================
 *  comision-codigo.js
 *
 *  Sugiere el codigo de una comision nueva cuando ya se eligio la materia y el
 *  periodo.
 *
 *  Por que hace falta. El codigo es unico por (materia, periodo), asi que para
 *  saber cual esta libre hay que mirar lo que ya existe. Antes eso se hacia a
 *  mano: abrir el listado, filtrar por la materia, ver que habia, volver al
 *  formulario y escribir la siguiente. El servidor ya sabe la respuesta.
 *
 *  Que NO hace. No pisa lo que la persona escribio. En cuanto el campo tiene
 *  algo que no puso este script, deja de sugerir para siempre: el codigo suele
 *  ser texto --"Manana", "Noche"-- y una sugerencia que sobreescribe eso es
 *  peor que no tener sugerencia.
 *
 *  Si el script no corre, el formulario anda igual: el campo queda vacio y se
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

    // Lo ultimo que escribimos nosotros. Sirve para distinguir "el campo tiene
    // una sugerencia vieja" de "la persona escribio algo", que es lo unico que
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

        // Cada pedido lleva su numero: si se cambia la materia dos veces rapido,
        // la respuesta vieja no puede pisar a la nueva.
        var mio = ++pedido;
        var url = form.getAttribute('data-codigo-sugerido')
            + '?materiaId=' + encodeURIComponent(materia.value)
            + '&periodoId=' + encodeURIComponent(periodo.value);

        fetch(url, { headers: { 'Accept': 'text/plain' } })
            .then(function (r) { return r.ok ? r.text() : ''; })
            .then(function (texto) {
                if (mio !== pedido) return;          // llego tarde
                if (loEscribioLaPersona()) return;   // escribio mientras tanto
                sugerido = (texto || '').trim();
                campo.value = sugerido;
            })
            .catch(function () {
                // Sin red o con el servidor caido no se avisa nada: esto es una
                // comodidad, y el campo se puede escribir igual.
            });
    }

    materia.addEventListener('change', sugerir);
    periodo.addEventListener('change', sugerir);

    // Al abrir, por si el formulario ya viene con una materia elegida.
    sugerir();
})();
