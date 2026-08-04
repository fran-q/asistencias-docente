/* =============================================================================
 *  grilla-detalle.js
 *
 *  Cuadro flotante con el detalle de una clase de la grilla semanal.
 *
 *  Por que. Antes cada bloque era un enlace directo a la edicion: un click
 *  hecho para "ver de que es esta clase" terminaba en un formulario, que es una
 *  pantalla de la que hay que salir sin guardar. Mirar y editar son dos
 *  intenciones distintas y ahora cuestan distinto: mirar es pasar el mouse,
 *  editar es un boton.
 *
 *  El cuadro se abre tambien con el foco del teclado, no solo con el mouse: si
 *  la unica forma de ver el detalle fuera pasar el puntero, quien navega
 *  tabulando no tendria manera de leerlo.
 * ========================================================================== */
(function (window, document) {
    'use strict';

    var cuadro = null;
    var abiertoPara = null;
    var timerCierre = null;

    function crearCuadro() {
        var d = document.createElement('div');
        d.className = 'grilla-detalle';
        d.setAttribute('role', 'dialog');
        d.hidden = true;
        // Si el puntero entra al propio cuadro no se cierra: hay un boton adentro, y
        // cerrarlo al ir a buscarlo lo volveria inalcanzable con el mouse.
        d.addEventListener('mouseenter', function () { clearTimeout(timerCierre); });
        d.addEventListener('mouseleave', cerrarConDemora);
        document.body.appendChild(d);
        return d;
    }

    function texto(v, alternativa) {
        return (v && v !== 'null' && v.trim() !== '') ? v : alternativa;
    }

    function abrir(bloque) {
        if (!cuadro) cuadro = crearCuadro();
        clearTimeout(timerCierre);
        abiertoPara = bloque;

        var d = bloque.dataset;
        cuadro.innerHTML = '';

        var h = document.createElement('p');
        h.className = 'grilla-detalle__titulo';
        h.textContent = texto(d.materia, d.materiaCodigo);
        cuadro.appendChild(h);

        var dl = document.createElement('dl');
        dl.className = 'grilla-detalle__datos';
        [
            ['Comisión',   d.comision],
            ['Docente',    texto(d.docente, 'Sin asignar')],
            ['Horario',    d.inicio + ' – ' + d.fin],
            ['Tolerancia', d.tolerancia + ' min']
        ].forEach(function (par) {
            var dt = document.createElement('dt'); dt.textContent = par[0];
            var dd = document.createElement('dd'); dd.textContent = par[1];
            dl.appendChild(dt); dl.appendChild(dd);
        });
        cuadro.appendChild(dl);

        var a = document.createElement('a');
        a.className = 'btn btn--ghost btn--sm';
        a.href = d.editar;
        a.textContent = 'Ir a editar la clase';
        cuadro.appendChild(a);

        cuadro.hidden = false;
        posicionar(bloque);
    }

    function posicionar(bloque) {
        var r = bloque.getBoundingClientRect();
        var c = cuadro.getBoundingClientRect();
        var margen = 8;

        // Por defecto a la derecha del bloque; si no entra, a la izquierda. Sin esto los
        // bloques de sabado y domingo abrian el cuadro fuera de la pantalla.
        var izq = r.right + margen;
        if (izq + c.width > window.innerWidth - margen) izq = r.left - c.width - margen;
        if (izq < margen) izq = margen;

        var top = r.top;
        if (top + c.height > window.innerHeight - margen) {
            top = window.innerHeight - c.height - margen;
        }
        if (top < margen) top = margen;

        cuadro.style.left = (izq + window.scrollX) + 'px';
        cuadro.style.top  = (top + window.scrollY) + 'px';
    }

    function cerrar() {
        if (cuadro) cuadro.hidden = true;
        abiertoPara = null;
    }

    // Demora corta al salir: sin ella, mover el puntero del bloque al cuadro --que estan
    // separados por unos pixeles-- lo cierra antes de llegar.
    function cerrarConDemora() {
        clearTimeout(timerCierre);
        timerCierre = setTimeout(cerrar, 180);
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('.grilla__item').forEach(function (b) {
            b.addEventListener('mouseenter', function () { abrir(b); });
            b.addEventListener('mouseleave', cerrarConDemora);
            b.addEventListener('focus', function () { abrir(b); });
            b.addEventListener('blur', cerrarConDemora);
            // Con el teclado no existe "pasar por encima": el click o Enter abre el
            // cuadro, y desde ahi se tabula hasta el boton de editar.
            b.addEventListener('click', function () { abrir(b); });
        });

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && abiertoPara) {
                var volverA = abiertoPara;
                cerrar();
                volverA.focus();
            }
        });

        window.addEventListener('scroll', function () {
            if (abiertoPara) posicionar(abiertoPara);
        }, { passive: true });
    });

})(window, document);
