/* =============================================================================
 *  ayuda.js
 *
 *  La explicacion de una pantalla se cierra y se vuelve a abrir.
 *
 *  Ciclos lectivos, dias sin clase, salidas pendientes y el alta de usuarios
 *  arrancan con un cuadro que cuenta como funcionan (aside.ayuda[data-ayuda]).
 *  Se lee una vez; despues estorba. El cuadro trae una cruz para cerrarlo, y el
 *  titulo de la pantalla una "i" discreta para volver a abrirlo, como la que
 *  explica donde esta el codigo de seguridad de una tarjeta. Lo cerrado se
 *  recuerda en este navegador, igual que el tema y la densidad: es una comodidad
 *  de quien mira, no un dato de la cuenta.
 *
 *  Sin JavaScript el cuadro queda abierto y no aparece ningun boton: se pierde el
 *  atajo, no la explicacion. Un cuadro que ya se cerro lo oculta el script del
 *  <head> de base.html antes del primer paint; si no, alcanzaba a verse y
 *  desaparecia al terminar de cargar.
 *
 *  La "i" se dibuja por CSS y no con texto: migas.js toma el nombre de la pantalla
 *  del <h1>, y con una letra adentro la miga diria "Ciclo lectivo 2026 i".
 * ========================================================================== */
(function (document) {
    'use strict';

    var CLAVE = 'ayudas-cerradas';

    function leer() {
        try { return (localStorage.getItem(CLAVE) || '').split(' ').filter(Boolean); }
        catch (e) { return []; }                        // modo privado
    }
    function guardar(lista) {
        try { localStorage.setItem(CLAVE, lista.join(' ')); } catch (e) { /* modo privado */ }
    }

    var titulo = document.querySelector('#contenido h1');
    var cuadro = document.querySelector('#contenido .ayuda[data-ayuda]');

    // Sin titulo no hay donde poner la "i": el cuadro queda abierto y sin cruz, porque
    // cerrarlo sin forma de volver a abrirlo seria perder la explicacion.
    if (titulo && cuadro) {
        var clave = cuadro.getAttribute('data-ayuda');
        var nombre = cuadro.getAttribute('aria-label') || 'Cómo funciona esta pantalla';
        cuadro.id = cuadro.id || 'ayuda-' + clave;

        var cerrar = document.createElement('button');
        cerrar.type = 'button';
        cerrar.className = 'ayuda__cerrar';
        cerrar.setAttribute('aria-label', 'Cerrar la explicación');
        cerrar.textContent = '×';
        cuadro.insertBefore(cerrar, cuadro.firstChild);

        var abrir = document.createElement('button');
        abrir.type = 'button';
        abrir.className = 'ayuda-boton';
        abrir.title = nombre;
        abrir.setAttribute('aria-label', nombre);
        abrir.setAttribute('aria-controls', cuadro.id);
        titulo.appendChild(abrir);

        var mostrar = function (visible) {
            cuadro.hidden = !visible;
            abrir.setAttribute('aria-expanded', visible ? 'true' : 'false');
        };
        var recordar = function (cerrado) {
            var lista = leer().filter(function (c) { return c !== clave; });
            if (cerrado) lista.push(clave);
            guardar(lista);
        };

        mostrar(leer().indexOf(clave) < 0);

        cerrar.addEventListener('click', function () {
            mostrar(false);
            recordar(true);
            // El foco quedaba en un boton que ya no se ve: pasa a la "i", que es por donde
            // se vuelve a abrir.
            abrir.focus();
        });
        abrir.addEventListener('click', function () {
            var abriendo = cuadro.hidden;
            mostrar(abriendo);
            recordar(!abriendo);
            if (abriendo) {
                cuadro.classList.remove('ayuda--abriendo');
                void cuadro.offsetWidth;                  // reinicia la animacion
                cuadro.classList.add('ayuda--abriendo');
            }
        });
    }

    // El ocultamiento del <head> ya cumplio: de aca en adelante manda el atributo hidden.
    var previo = document.getElementById('ayudas-cerradas');
    if (previo) previo.parentNode.removeChild(previo);
})(document);
