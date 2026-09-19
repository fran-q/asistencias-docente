package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.SeccionDto;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Qué pantallas ve cada rol dentro de cada grupo del menú.
 *
 * <p>Vive en un solo lugar porque la misma respuesta la necesitan tres cosas: la barra de
 * navegación, la pantalla intermedia de cada grupo y la decisión de a dónde lleva el click
 * en el grupo. Repartido, alcanzaba con agregar una pantalla y olvidarse de uno de los tres
 * para que el menú y la pantalla mostraran cosas distintas.
 *
 * <p>Notar que el <b>destino del click depende del rol</b>: si en ese grupo el usuario tiene
 * una sola pantalla, ir a una intermedia que solo ofrece esa única opción es un paso de más,
 * así que se va derecho. Es el caso de Personal para el rol ADMIN, que solo ve Docentes.
 */
@Service
public class SeccionService {

    /** Los tres grupos del menú, con su etiqueta y sus pantallas por rol, en su mismo orden. */
    public enum Grupo {
        ASISTENCIA("asistencia", "Asistencias", "Registro y consulta de la asistencia docente."),
        // La descripcion dice el orden de carga porque el menu ya no lo muestra: ahi los
        // destinos van del uso diario al anual, que es el orden inverso.
        ACADEMICO("academico", "Académico",
            "La oferta del año. Se carga en orden: ciclo lectivo, carrera, materia, comisión "
            + "y horario; cada paso necesita el anterior."),
        PERSONAL("personal", "Personal", "Docentes, cuentas de acceso y datos de la institución.");

        private final String ruta;
        private final String etiqueta;
        private final String descripcion;

        Grupo(String ruta, String etiqueta, String descripcion) {
            this.ruta = ruta;
            this.etiqueta = etiqueta;
            this.descripcion = descripcion;
        }

        public String getRuta()        { return ruta; }
        public String getEtiqueta()    { return etiqueta; }
        public String getDescripcion() { return descripcion; }
    }

    /**
     * Las pantallas del grupo que este usuario puede abrir.
     *
     * <p>El orden es el mismo que el del menú: quien ya se acostumbró a buscar "Reportes"
     * en tercer lugar lo encuentra en tercer lugar también acá.
     */
    public List<SeccionDto> pantallasDe(Grupo grupo, Authentication auth) {
        boolean esInstitucion = tieneRol(auth, "ROLE_INSTITUCION");

        return switch (grupo) {
            case ASISTENCIA -> List.of(
                new SeccionDto("Pase de asistencia", "/asistencia/pase",
                    "Reconocer al docente por cámara y registrar su asistencia.", "rostro"),
                new SeccionDto("Listado del día", "/asistencias",
                    "Las marcas de una fecha, con las ausencias calculadas.", "lista"),
                new SeccionDto("Reportes", "/reportes",
                    "Filtrar por período y exportar a CSV o PDF.", "grafico"));

            case ACADEMICO -> {
                List<SeccionDto> pantallas = new java.util.ArrayList<>();
                // Del uso diario al anual, igual que en la barra lateral: la grilla y los
                // horarios se miran seguido, y una carrera o un ciclo se cargan una vez al año.
                // El orden en que hay que cargarlos --ciclo, carrera, materia, comisión,
                // horario-- lo cuenta el texto de esta pantalla, que es donde hace falta.
                pantallas.add(new SeccionDto("Grilla semanal", "/grilla",
                    "Los horarios de una carrera vistos como calendario.", "grilla"));
                pantallas.add(new SeccionDto("Horarios", "/horarios",
                    "Las franjas semanales contra las que se marca la asistencia.", "reloj"));
                pantallas.add(new SeccionDto("Comisiones", "/comisiones",
                    "Las divisiones de cada materia y quién las dicta.", "personas"));
                pantallas.add(new SeccionDto("Materias", "/materias",
                    "Qué se dicta en cada carrera y en qué año.", "materia"));
                pantallas.add(new SeccionDto("Carreras", "/carreras",
                    "Los programas académicos de los que cuelga todo lo demás.", "libro"));
                // Ciclos y días sin clase son solo institucionales: definen cuándo el sistema
                // toma asistencia y cuándo genera ausencias, que no es una tarea del día a día.
                if (esInstitucion) {
                    pantallas.add(new SeccionDto("Días sin clase", "/dias-sin-clase",
                        "Feriados y suspensiones: no generan ausencia.", "calendarioX"));
                    pantallas.add(new SeccionDto("Ciclos lectivos", "/ciclos",
                        "Los períodos en los que el sistema toma asistencia.", "calendario"));
                }
                yield List.copyOf(pantallas);
            }

            case PERSONAL -> {
                List<SeccionDto> pantallas = new java.util.ArrayList<>();
                pantallas.add(new SeccionDto("Docentes", "/docentes",
                    "El personal docente, su consentimiento y su modelo facial.", "docente"));
                // Solo el rol INSTITUCION administra cuentas y los datos de la institución.
                if (esInstitucion) {
                    pantallas.add(new SeccionDto("Usuarios del sistema", "/usuarios",
                        "Las cuentas que pueden entrar a la aplicación.", "monitor"));
                    pantallas.add(new SeccionDto("Mi institución", "/mi-institucion",
                        "Nombre, CUIT y datos de contacto.", "edificio"));
                    pantallas.add(new SeccionDto("Puestos de captura", "/puestos",
                        "Los equipos desde los que se puede tomar asistencia por cámara.", "puesto"));
                }
                yield List.copyOf(pantallas);
            }
        };
    }

    /**
     * A dónde lleva hacer click en el grupo del menú.
     *
     * <p>Si el usuario tiene una sola pantalla en ese grupo, va derecho a ella: una pantalla
     * intermedia que ofrece una única opción es un click que no decide nada. Con dos o más,
     * lleva a la intermedia, que además es lo que hace que la miga de pan de ese grupo
     * apunte a algo que existe.
     */
    public String destinoDelGrupo(Grupo grupo, Authentication auth) {
        List<SeccionDto> pantallas = pantallasDe(grupo, auth);
        return pantallas.size() == 1 ? pantallas.get(0).ruta() : "/" + grupo.getRuta();
    }

    private boolean tieneRol(Authentication auth, String rol) {
        if (auth == null) return false;
        Set<String> roles = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        return roles.contains(rol);
    }
}
