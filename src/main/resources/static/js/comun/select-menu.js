/* =============================================================================
 *  select-menu.js — Desplegable propio para <select> de pocas opciones
 *
 *  Por qué. El desplegable de un <select> nativo no se puede estilar: la lista
 *  que se abre no es parte de la página sino una ventana que dibuja el sistema
 *  operativo. En una app oscura eso significa una lista blanca con el color de
 *  acento del escritorio en el medio, que no se parece a nada de lo que la
 *  rodea. color-scheme ayuda --y está declarado-- pero sólo decide si el gris
 *  es claro u oscuro; el resto sigue siendo del sistema.
 *
 *  Por qué no select-buscable.js. Ese convierte el select en un campo donde se
 *  escribe para filtrar, y está pensado para listas largas: con veinte docentes
 *  buscar por apellido es lo que uno intenta primero. Su propio comentario
 *  aclara que no se aplica a todos porque en una lista de tres opciones el
 *  buscador estorba más de lo que ayuda. Acá hacen falta las mismas tres
 *  opciones de siempre, así que lo que se necesita es un menú, no un buscador.
 *
 *  Los dos comparten la lista --.buscable__lista y .buscable__opcion-- para que
 *  los dos desplegables de la aplicación se vean igual. Lo único propio es el
 *  botón que la abre.
 *
 *  Cómo. Mismo criterio que select-buscable y que el selector de hora: el
 *  <select> original NO se elimina. Queda oculto, sigue siendo el que se envía
 *  y el que el servidor valida, y este componente sólo escribe en él y dispara
 *  su evento change. Si el script no llegara a correr, el select nativo sigue
 *  visible y funciona como siempre: se oculta desde acá, nunca desde el CSS.
 *
 *  Uso:  <select data-menu> ... </select>
 * ========================================================================== */
(function (window, document) {
    'use strict';

    var secuencia = 0;

    function construir(select) {
        var id = 'menu-' + (++secuencia);

        var caja = document.createElement('div');
        caja.className = 'buscable menu';

        var boton = document.createElement('button');
        boton.type = 'button';               /* dentro de un <form> un button sin type envía */
        boton.className = 'menu__boton';
        boton.setAttribute('aria-haspopup', 'listbox');
        boton.setAttribute('aria-expanded', 'false');
        boton.setAttribute('aria-controls', id);
        if (select.getAttribute('aria-label')) {
            boton.setAttribute('aria-label', select.getAttribute('aria-label'));
        }

        var etiqueta = document.createElement('span');
        etiqueta.className = 'menu__texto';
        boton.appendChild(etiqueta);

        /* La flecha se dibuja acá y no con un fragmento de Thymeleaf: este script
           corre en el navegador y no puede pedirle un fragmento al servidor. */
        boton.insertAdjacentHTML('beforeend',
            '<svg class="menu__flecha" viewBox="0 0 24 24" width="14" height="14" fill="none" ' +
            'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" ' +
            'aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>');

        var lista = document.createElement('ul');
        lista.className = 'buscable__lista';
        lista.id = id;
        lista.setAttribute('role', 'listbox');
        lista.hidden = true;

        select.parentNode.insertBefore(caja, select);
        caja.appendChild(boton);
        caja.appendChild(lista);
        caja.appendChild(select);
        select.hidden = true;

        var opciones = Array.prototype.map.call(select.options, function (o) {
            return { valor: o.value, texto: o.textContent.trim() };
        });
        var resaltado = -1;

        function indiceActual() {
            for (var i = 0; i < opciones.length; i++) {
                if (opciones[i].valor === select.value) return i;
            }
            return 0;
        }

        function pintarEtiqueta() {
            var o = opciones[indiceActual()];
            etiqueta.textContent = o ? o.texto : '';
        }

        function pintarLista() {
            lista.innerHTML = '';
            opciones.forEach(function (o, i) {
                var li = document.createElement('li');
                li.className = 'buscable__opcion';
                li.id = id + '-' + i;
                li.setAttribute('role', 'option');
                li.setAttribute('aria-selected', o.valor === select.value ? 'true' : 'false');
                li.textContent = o.texto;
                if (o.valor === select.value) li.classList.add('buscable__opcion--actual');
                /* mousedown y no click: el click llega después del blur del botón, que
                   para entonces ya cerró la lista y canceló la elección. */
                li.addEventListener('mousedown', function (e) {
                    e.preventDefault();
                    elegir(i);
                });
                li.addEventListener('mousemove', function () {
                    resaltado = i;
                    marcarResaltado();
                });
                lista.appendChild(li);
            });
        }

        function marcarResaltado() {
            Array.prototype.forEach.call(lista.children, function (li, i) {
                li.classList.toggle('buscable__opcion--activa', i === resaltado);
            });
            var activa = lista.children[resaltado];
            if (activa) {
                boton.setAttribute('aria-activedescendant', activa.id);
                if (activa.scrollIntoView) activa.scrollIntoView({ block: 'nearest' });
            } else {
                boton.removeAttribute('aria-activedescendant');
            }
        }

        function abrir() {
            pintarLista();
            lista.hidden = false;
            boton.setAttribute('aria-expanded', 'true');
            /* Se abre parado en lo que está elegido, no en el primero: con las flechas
               se sale desde donde uno está, que es como se comporta el select nativo. */
            resaltado = indiceActual();
            marcarResaltado();
        }

        function cerrar() {
            if (lista.hidden) return;
            lista.hidden = true;
            boton.setAttribute('aria-expanded', 'false');
            boton.removeAttribute('aria-activedescendant');
        }

        function elegir(i) {
            var o = opciones[i];
            if (!o) return;
            select.value = o.valor;
            /* El change lo escuchan los filtros de tabla, igual que si se hubiera usado
               el select nativo: nada aguas abajo tiene que enterarse de este componente. */
            select.dispatchEvent(new Event('change', { bubbles: true }));
            pintarEtiqueta();
            cerrar();
            boton.focus();
        }

        boton.addEventListener('click', function () {
            if (lista.hidden) abrir(); else cerrar();
        });

        boton.addEventListener('keydown', function (e) {
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                e.preventDefault();
                if (lista.hidden) { abrir(); return; }
                resaltado += (e.key === 'ArrowDown' ? 1 : -1);
                if (resaltado < 0) resaltado = opciones.length - 1;
                if (resaltado >= opciones.length) resaltado = 0;
                marcarResaltado();
                return;
            }
            if (e.key === 'Home' || e.key === 'End') {
                if (lista.hidden) return;
                e.preventDefault();
                resaltado = (e.key === 'Home' ? 0 : opciones.length - 1);
                marcarResaltado();
                return;
            }
            if (e.key === 'Enter' || e.key === ' ' || e.key === 'Spacebar') {
                e.preventDefault();
                if (lista.hidden) abrir(); else elegir(resaltado);
                return;
            }
            if (e.key === 'Escape' && !lista.hidden) {
                e.preventDefault();
                /* stopPropagation: la misma tecla cierra el cajón de la barra lateral, y
                   si no se corta acá se cierran las dos cosas de un saque. */
                e.stopPropagation();
                cerrar();
            }
        });

        boton.addEventListener('blur', function () { setTimeout(cerrar, 0); });

        /* Si algo cambia el select por su cuenta --el navegador reponiendo el valor al
           volver atrás, u otro script-- la etiqueta tiene que acompañar. */
        select.addEventListener('change', pintarEtiqueta);

        /* El botón se dimensiona a la opción MÁS LARGA, no a la elegida.
           Un select nativo hace lo mismo, y por un buen motivo: si tomara el ancho de
           lo que está elegido, el control se encogería al pasar de "Todos los estados"
           a "Solo activos" y el resto de la fila de filtros se correría de lugar cada
           vez. Se mide de verdad, poniendo cada texto y leyendo el ancho, en vez de
           estimar con un número fijo que se rompe al cambiar una etiqueta. */
        function fijarAncho() {
            var previo = etiqueta.textContent;
            var ancho = 0;
            opciones.forEach(function (o) {
                etiqueta.textContent = o.texto;
                ancho = Math.max(ancho, boton.getBoundingClientRect().width);
            });
            etiqueta.textContent = previo;
            if (ancho) caja.style.minWidth = Math.ceil(ancho) + 'px';
        }

        pintarEtiqueta();
        fijarAncho();

        /* Y otra vez cuando terminen de cargar las fuentes: la primera medición puede
           caer con la tipografía de respaldo, que tiene otro ancho. */
        if (document.fonts && document.fonts.ready) {
            document.fonts.ready.then(fijarAncho);
        }
    }

    function iniciar() {
        Array.prototype.forEach.call(
            document.querySelectorAll('select[data-menu]'), construir);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }

})(window, document);
