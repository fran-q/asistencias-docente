/* =============================================================================
 *  password-ui.js
 *  Mejoras UX para inputs type=password:
 *   - boton ojo para mostrar / ocultar el texto
 *   - bloqueo de copy / paste / cut por seguridad
 *
 *  Se aplica automaticamente a todo elemento .password-input que envuelva
 *  un <input type="password">.
 *
 *  HTML esperado:
 *    <div class="password-input">
 *      <input type="password" id="..." name="...">
 *      <button type="button" class="password-toggle" aria-label="Mostrar contraseña">👁</button>
 *    </div>
 * ========================================================================== */

(function (window, document) {
    'use strict';

    var EYE_OPEN  = '\u{1F441}';      // 👁
    var EYE_SHUT  = '\u{1F648}';      // 🙈

    function setupToggle(button) {
        var wrapper = button.closest('.password-input');
        if (!wrapper) return;
        var input = wrapper.querySelector('input');
        if (!input) return;

        button.addEventListener('click', function (ev) {
            ev.preventDefault();
            var visible = input.type === 'text';
            input.type = visible ? 'password' : 'text';
            button.textContent = visible ? EYE_OPEN : EYE_SHUT;
            button.setAttribute(
                'aria-label',
                visible ? 'Mostrar contraseña' : 'Ocultar contraseña'
            );
        });
    }

    function blockCopyPaste(input) {
        ['copy', 'cut', 'paste'].forEach(function (ev) {
            input.addEventListener(ev, function (e) {
                e.preventDefault();
                if (window.Toast) {
                    window.Toast.show(
                        'Por seguridad, no se permite ' + (ev === 'paste' ? 'pegar' : 'copiar') +
                        ' en campos de contraseña.',
                        'warning',
                        3000
                    );
                }
            });
        });
        // Tambien deshabilita el menu contextual sobre el input
        input.addEventListener('contextmenu', function (e) { e.preventDefault(); });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.password-toggle').forEach(setupToggle);
        document.querySelectorAll('input[type="password"]').forEach(blockCopyPaste);
    });

})(window, document);
