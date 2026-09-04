/* =============================================================================
 *  select-buscable.js
 *
 *  Convierte un <select> largo en un campo donde se escribe para filtrar.
 *
 *  Por que. Con veinte docentes cargados, elegir uno en un desplegable nativo
 *  es recorrer la lista con la vista. Escribir tres letras del apellido es lo
 *  que cualquiera intenta hacer primero.
 *
 *  Como. El <select> original NO se elimina: queda oculto y sigue siendo el que
 *  se envia y el que el servidor valida --mismo criterio que el selector de
 *  hora--. Encima se dibuja un input de texto y una lista filtrada que solo
 *  escriben en el select. Si este script no llegara a correr, el select sigue
 *  ahi y funciona como siempre.
 *
 *  Se activa poniendo data-buscable en el <select>. No se aplica solo a todos
 *  porque en una lista de tres opciones el buscador estorba mas de lo que ayuda.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    // Saca tildes y pasa a minusculas: buscar "garcia" tiene que encontrar a Garcia.
    // Mismo criterio que el filtro de los listados, para que la app se comporte igual
    // en los dos lugares donde se busca escribiendo.
    function normalizar(t) {
        return (t || '').toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');
    }

    function construir(select) {
        var contenedor = document.createElement('div');
        contenedor.className = 'buscable';

        var input = document.createElement('input');
        input.type = 'text';
        input.className = 'buscable__input';
        input.autocomplete = 'off';
        input.setAttribute('role', 'combobox');
        input.setAttribute('aria-expanded', 'false');
        input.setAttribute('aria-autocomplete', 'list');
        if (select.id) input.setAttribute('aria-labelledby', select.id + '-label');

        var lista = document.createElement('ul');
        lista.className = 'buscable__lista';
        lista.setAttribute('role', 'listbox');
        lista.hidden = true;

        contenedor.appendChild(input);
        contenedor.appendChild(lista);
        select.parentNode.insertBefore(contenedor, select);
        contenedor.appendChild(select);
        select.hidden = true;

        var opciones = Array.prototype.map.call(select.options, function (o) {
            return { valor: o.value, texto: o.textContent.trim(), buscable: normalizar(o.textContent) };
        });
        var resaltado = -1;
        var visibles = [];

        function textoActual() {
            var o = select.selectedOptions[0];
            return o ? o.textContent.trim() : '';
        }

        function pintar(filtro) {
            var f = normalizar(filtro);
            visibles = f ? opciones.filter(function (o) { return o.buscable.indexOf(f) !== -1; })
                         : opciones.slice();
            lista.innerHTML = '';

            if (visibles.length === 0) {
                var vacio = document.createElement('li');
                vacio.className = 'buscable__vacio';
                vacio.textContent = 'Sin coincidencias';
                lista.appendChild(vacio);
                resaltado = -1;
                return;
            }

            visibles.forEach(function (o, i) {
                var li = document.createElement('li');
                li.className = 'buscable__opcion';
                li.setAttribute('role', 'option');
                li.textContent = o.texto;
                li.dataset.valor = o.valor;
                if (o.valor === select.value) li.classList.add('buscable__opcion--actual');
                // mousedown y no click: el click llega despues del blur del input, que
                // ya habria cerrado la lista y cancelado la seleccion.
                li.addEventListener('mousedown', function (e) {
                    e.preventDefault();
                    elegir(i);
                });
                lista.appendChild(li);
            });
            resaltado = 0;
            marcarResaltado();
        }

        function marcarResaltado() {
            Array.prototype.forEach.call(lista.children, function (li, i) {
                li.classList.toggle('buscable__opcion--activa', i === resaltado);
            });
            var activa = lista.children[resaltado];
            if (activa && activa.scrollIntoView) activa.scrollIntoView({ block: 'nearest' });
        }

        function abrir() {
            pintar('');
            lista.hidden = false;
            input.setAttribute('aria-expanded', 'true');
        }

        function cerrar() {
            lista.hidden = true;
            input.setAttribute('aria-expanded', 'false');
            // Se repone el texto de lo que este realmente elegido: si quedo escrito
            // algo a medias, el campo mostraria una cosa y el formulario mandaria otra.
            input.value = textoActual();
        }

        function elegir(i) {
            var o = visibles[i];
            if (!o) return;
            select.value = o.valor;
            select.dispatchEvent(new Event('change', { bubbles: true }));
            input.value = o.texto;
            cerrar();
        }

        input.value = textoActual();

        /*
         *  Al enfocar se abre la lista Y se selecciona el texto que haya.
         *
         *  Antes el campo quedaba con la opcion elegida escrita y el cursor al final, asi
         *  que para buscar otra cosa habia que borrarla a mano primero: si no, lo tipeado
         *  se pegaba atras --"TUP-003 — Aprogramacion"-- y el filtro no encontraba nada.
         *
         *  Seleccionando el texto, la primera tecla lo reemplaza, que es como se comporta
         *  la barra de direcciones del navegador y cualquier campo de busqueda. Y si en vez
         *  de escribir se elige de la lista, el texto seleccionado no se pierde: sigue ahi.
         *
         *  El setTimeout no es un parche: en el focus por click, el navegador coloca el
         *  cursor DESPUES de que corre este listener, y sin el la seleccion se deshace sola.
         */
        input.addEventListener('focus', function () {
            abrir();
            setTimeout(function () {
                if (document.activeElement === input) input.select();
            }, 0);
        });

        // Click sobre un campo ya enfocado: no dispara focus de nuevo, pero la intencion de
        // volver a hacer click sobre lo que ya esta escrito es cambiarlo.
        input.addEventListener('mouseup', function (e) {
            if (input.selectionStart === input.selectionEnd) {
                e.preventDefault();
                input.select();
            }
        });
        input.addEventListener('input', function () {
            pintar(input.value);
            lista.hidden = false;
            input.setAttribute('aria-expanded', 'true');
        });
        input.addEventListener('blur', function () { setTimeout(cerrar, 0); });

        input.addEventListener('keydown', function (e) {
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                e.preventDefault();
                if (lista.hidden) { abrir(); return; }
                if (visibles.length === 0) return;
                resaltado += (e.key === 'ArrowDown' ? 1 : -1);
                if (resaltado < 0) resaltado = visibles.length - 1;
                if (resaltado >= visibles.length) resaltado = 0;
                marcarResaltado();
            } else if (e.key === 'Enter') {
                if (!lista.hidden) {
                    // Solo se traga el Enter si la lista esta abierta: con el campo
                    // cerrado tiene que seguir enviando el formulario como cualquier otro.
                    e.preventDefault();
                    elegir(resaltado);
                }
            } else if (e.key === 'Escape') {
                cerrar();
            }
        });

        // El select puede cambiar por fuera --el formulario de comision propone el
        // titular de la materia-- y el input tiene que reflejarlo.
        select.addEventListener('change', function () {
            if (document.activeElement !== input) input.value = textoActual();
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('select[data-buscable]').forEach(construir);
    });

})(window, document);
