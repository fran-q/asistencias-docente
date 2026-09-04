/* =============================================================================
 *  academico-form.js
 *
 *  Dos ayudas para los formularios del modulo academico. Las dos son solo
 *  comodidad: el servidor revalida igual, porque un select se puede tocar desde
 *  afuera del navegador y porque la pagina puede quedar abierta mientras otro
 *  edita la carrera o la materia.
 *
 *   1) Materia: el select de anio se recorta a la duracion de la carrera
 *      elegida. Ofrecer "5° año" en una tecnicatura de tres es invitar al error
 *      y despues rechazarlo con un cartel.
 *
 *   2) Comision: al elegir la materia se propone su docente titular. Es quien
 *      dicta en la enorme mayoria de los casos; queda editable porque la
 *      excepcion existe y es justamente lo que hay que poder cargar.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    // ---- Materia: acotar el anio a la duracion de la carrera --------------

    function acotarAnios() {
        const carreraSel = document.getElementById('carreraId');
        const anioSel    = document.getElementById('anio');
        const hint       = document.getElementById('anio-hint');
        if (!carreraSel || !anioSel) return;

        function aplicar() {
            const opt = carreraSel.selectedOptions[0];
            const duracion = opt ? parseInt(opt.dataset.duracion, 10) : NaN;

            // Sin carrera elegida se dejan todas: recortar a cero dejaria el select vacio
            // y sin forma de entender por que.
            const tope = isNaN(duracion) ? 10 : duracion;

            // Se sacan del DOM en vez de ocultarlas: hidden + disabled las deja igual
            // en el arbol de accesibilidad, asi que un lector de pantalla seguia
            // anunciando "5° año ... 10° año" en una carrera de tres.
            let seleccionadoQuedaFuera = false;
            Array.prototype.slice.call(anioSel.options).forEach(function (o) {
                if (parseInt(o.value, 10) > tope) {
                    if (o.selected) seleccionadoQuedaFuera = true;
                    o.remove();
                }
            });
            // Y se reponen las que vuelven a entrar si se elige una carrera mas larga.
            for (let n = anioSel.options.length + 1; n <= tope; n++) {
                const o = document.createElement('option');
                o.value = String(n);
                o.textContent = n + '° año';
                anioSel.appendChild(o);
            }

            // Si el anio que estaba elegido ya no entra, se baja al ultimo valido en vez de
            // dejar el select mostrando una opcion deshabilitada.
            if (seleccionadoQuedaFuera) anioSel.value = String(tope);

            if (hint) {
                hint.textContent = isNaN(duracion)
                    ? 'En qué año del plan se cursa.'
                    : 'La carrera dura ' + duracion + (duracion === 1 ? ' año.' : ' años.');
            }
        }

        carreraSel.addEventListener('change', aplicar);
        aplicar();   // tambien al cargar, para el modo edicion
    }

    // ---- Comision: proponer el titular de la materia ----------------------

    function proponerTitular() {
        const materiaSel = document.getElementById('materiaId');
        const docenteSel = document.getElementById('docenteAsignadoId');
        const hint       = document.getElementById('docente-hint');
        if (!materiaSel || !docenteSel) return;

        // Solo se pisa el docente cuando lo cambia una persona, nunca al cargar la pagina:
        // en el modo edicion la comision ya tiene su docente elegido, y sobrescribirlo al
        // abrir el formulario borraria en silencio justamente la excepcion que se cargo.
        materiaSel.addEventListener('change', function () {
            const opt = materiaSel.selectedOptions[0];
            const titularId = opt ? (opt.dataset.titularId || '') : '';

            if (!titularId) {
                if (hint) {
                    hint.textContent = 'Esta materia no tiene docente titular cargado. '
                                     + 'Elegí quién dicta la comisión.';
                }
                return;
            }

            // Si el titular no esta entre las opciones (inactivo, por ejemplo) no se toca nada.
            const existe = Array.prototype.some.call(docenteSel.options,
                function (o) { return o.value === titularId; });
            if (!existe) {
                if (hint) {
                    hint.textContent = 'El titular de esta materia no está disponible. '
                                     + 'Elegí quién dicta la comisión.';
                }
                return;
            }

            docenteSel.value = titularId;
            if (hint) {
                hint.textContent = 'Se propuso a ' + (opt.dataset.titularNombre || 'el titular')
                                 + ', titular de la materia. Cambialo si dicta otra persona.';
            }
        });
    }

    // ---- Horario: recordar que materia es la comision elegida --------------

    /*
     *  El desplegable de comision es "buscable": al elegir, se cierra y deja el texto
     *  adentro del campo. Si el campo se scrollea o el nombre es largo, queda cortado y
     *  hay que volver a abrirlo para confirmar que se eligio bien. Esto lo repite debajo,
     *  en chico, donde no molesta y siempre se ve entero.
     */
    function recordarMateriaDeLaComision() {
        const sel  = document.getElementById('comisionId');
        const eco  = document.getElementById('comision-materia');
        if (!sel || !eco) return;

        function pintar() {
            const opt = sel.selectedOptions[0];
            if (!opt || !opt.value) { eco.textContent = ''; return; }
            eco.textContent = opt.textContent.trim();
        }

        sel.addEventListener('change', pintar);
        pintar();   // tambien al cargar, por si vuelve con errores de validacion
    }

    document.addEventListener('DOMContentLoaded', function () {
        acotarAnios();
        proponerTitular();
        recordarMateriaDeLaComision();
    });

})(window, document);
