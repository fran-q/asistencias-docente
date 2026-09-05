# Tipografías

IBM Plex Sans e IBM Plex Mono, servidas desde acá y no desde un CDN: la app corre
en la red del establecimiento y no puede depender de que haya salida a internet
para verse bien.

Sans en tres pesos (400, 500, 600) y Mono en dos (400, 500). Son los que declara
`@font-face` en `css/main.css`; agregar un peso más significa agregar también su
archivo, o el navegador lo sintetiza y se ve deforme.

Mono se usa para el dato de identidad y de reloj —DNI, legajo, horarios, CUIT,
códigos de materia—: en fuente proporcional esos valores se comparan peor entre
filas de una tabla.

- Origen: https://github.com/IBM/plex
- Licencia: SIL Open Font License 1.1 (`LICENSE.txt`), uso comercial permitido.

Las rutas `/fonts/**` están exceptuadas en `SecurityConfig` y en
`WebMvcConfig.SIN_INTERCEPTAR`. Si faltara cualquiera de las dos, cada `.woff2`
recibe la redirección al login, `@font-face` falla en silencio y la app vuelve a
la tipografía del sistema sin decir por qué.
