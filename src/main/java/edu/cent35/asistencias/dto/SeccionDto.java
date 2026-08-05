package edu.cent35.asistencias.dto;

/**
 * Una pantalla dentro de un grupo del menú, tal como se ofrece en la pantalla intermedia.
 *
 * @param titulo      cómo se llama en el menú, para que el usuario reconozca lo mismo en los dos lados
 * @param ruta        a dónde lleva
 * @param descripcion qué se hace ahí; sin esto la pantalla intermedia sería una lista de nombres
 */
public record SeccionDto(String titulo, String ruta, String descripcion) {}
