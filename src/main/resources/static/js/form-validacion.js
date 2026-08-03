/* =============================================================================
 *  form-validacion.js
 *
 *  Muestra los errores de un formulario ANTES de enviarlo.
 *
 *  Por que. Hoy toda la validacion vive en el servidor: se completa el
 *  formulario, se envia, la pagina vuelve entera y recien ahi aparece que
 *  faltaba un campo. En formularios largos --el de horario tiene ocho-- eso
 *  obliga a volver a subir y buscar cual era.
 *
 *  Esto NO reemplaza la validacion del servidor ni la duplica: se apoya en las
 *  restricciones que el propio HTML ya declara (required, min, max, pattern),
 *  que a su vez salen de las anotaciones del DTO. Las reglas que necesitan la
 *  base --que el codigo sea unico, que el año entre en la carrera-- siguen
 *  resolviendose donde unicamente se pueden resolver, en el servidor.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    // Traduce el motivo del navegador a algo que se entienda sin saber HTML.
    function mensaje(campo) {
        var v = campo.validity;
        if (v.valueMissing)  return 'Este campo es obligatorio.';
        if (v.typeMismatch)  return campo.type === 'email'
            ? 'Escribí un correo válido.' : 'El formato no es válido.';
        if (v.patternMismatch) return 'El formato no es válido.';
        if (v.tooShort)      return 'Tiene que tener al menos ' + campo.minLength + ' caracteres.';
        if (v.tooLong)       return 'No puede pasar de ' + campo.maxLength + ' caracteres.';
        if (v.rangeUnderflow) return 'Tiene que ser ' + campo.min + ' o mayor.';
        if (v.rangeOverflow)  return 'No puede pasar de ' + campo.max + '.';
        if (v.stepMismatch)  return 'El valor no es válido.';
        return 'Revisá este campo.';
    }

    // Cada campo tiene su <span class="form-error"> del lado del servidor; se reusa
    // ese mismo hueco para no terminar con dos lugares donde puede aparecer un error.
    function huecoDeError(campo) {
        var grupo = campo.closest('.form-group');
        if (!grupo) return null;
        var span = grupo.querySelector('.form-error');
        if (!span) {
            span = document.createElement('span');
            span.className = 'form-error';
            grupo.appendChild(span);
        }
        return span;
    }

    function marcar(campo) {
        var span = huecoDeError(campo);
        if (campo.checkValidity()) {
            campo.classList.remove('is-invalid');
            if (span && span.dataset.cliente) { span.textContent = ''; delete span.dataset.cliente; }
            return true;
        }
        campo.classList.add('is-invalid');
        if (span) { span.textContent = mensaje(campo); span.dataset.cliente = '1'; }
        return false;
    }

    function conectar(form) {
        var campos = form.querySelectorAll('input, select, textarea');

        campos.forEach(function (campo) {
            // Al salir del campo, no mientras se escribe: marcar en rojo la primera letra
            // de un correo a medio tipear es ruido, no ayuda.
            campo.addEventListener('blur', function () { marcar(campo); });
            campo.addEventListener('input', function () {
                if (campo.classList.contains('is-invalid')) marcar(campo);
            });
        });

        form.addEventListener('submit', function (e) {
            var primerFallo = null;
            campos.forEach(function (campo) {
                if (!marcar(campo) && !primerFallo) primerFallo = campo;
            });
            if (primerFallo) {
                e.preventDefault();
                // Se lleva la vista al primer problema: en un formulario largo el error
                // puede quedar fuera de la pantalla y parece que el boton no hizo nada.
                primerFallo.focus();
                if (primerFallo.scrollIntoView) {
                    primerFallo.scrollIntoView({ block: 'center', behavior: 'smooth' });
                }
            }
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        // novalidate lo ponen las plantillas para quedarse con los mensajes propios en vez
        // de los globos del navegador; son justo los formularios que queremos cubrir.
        document.querySelectorAll('form[novalidate]').forEach(conectar);
    });

})(window, document);
