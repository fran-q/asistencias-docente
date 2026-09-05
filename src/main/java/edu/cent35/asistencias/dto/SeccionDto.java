package edu.cent35.asistencias.dto;

/**
 * Una pantalla dentro de un grupo del menú, tal como se ofrece en la pantalla intermedia.
 *
 * @param titulo      cómo se llama en el menú, para que el usuario reconozca lo mismo en los dos lados
 * @param ruta        a dónde lleva
 * @param descripcion qué se hace ahí; sin esto la pantalla intermedia sería una lista de nombres
 * @param icono       nombre del fragmento de {@code layout/iconos.html}. Es el MISMO que usa la
 *                    barra lateral para ese destino, y esa es toda la gracia: quien ya ubicó el
 *                    reloj como "Horarios" en el menú lo reconoce en la tarjeta sin leer. Si acá
 *                    se pusiera otro, la tarjeta y el enlace del menú parecerían dos cosas
 *                    distintas.
 */
public record SeccionDto(String titulo, String ruta, String descripcion, String icono) {}
