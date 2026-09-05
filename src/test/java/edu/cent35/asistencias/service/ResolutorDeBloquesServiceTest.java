package edu.cent35.asistencias.service;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.BloqueDeHorarios;
import edu.cent35.asistencias.model.Comision;
import edu.cent35.asistencias.model.Horario;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.Materia;
import edu.cent35.asistencias.repository.HorarioRepository;
import edu.cent35.asistencias.repository.InstitucionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Cubre el agrupamiento de clases en bloques de presencia (RF-75, RF-76): el borde exacto del
 * umbral de separación, la transitividad sin tope, las clases solapadas y que la materia no
 * intervenga.
 * <p>
 * El grueso son pruebas de {@code agrupar}, que es una función pura y no necesita mocks: el
 * borde entre "un bloque" y "dos" es de un minuto y hay que poder pararse encima sin levantar
 * Spring.
 */
@ExtendWith(MockitoExtension.class)
class ResolutorDeBloquesServiceTest {

    private static final Long TENANT_A = 1L;
    private static final Long DOCENTE_ID = 50L;

    @Mock private HorarioRepository horarioRepository;
    @Mock private InstitucionRepository institucionRepository;

    @InjectMocks private ResolutorDeBloquesService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_A);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    // ========================================================================
    //  agrupar - función pura sobre intervalos
    // ========================================================================

    @Nested
    @DisplayName("agrupar")
    class Agrupar {

        @Test
        @DisplayName("sin horarios no hay bloques")
        void sinHorarios() {
            assertThat(service.agrupar(List.of(), 60)).isEmpty();
            assertThat(service.agrupar(null, 60)).isEmpty();
        }

        @Test
        @DisplayName("una sola clase es un bloque de una clase")
        void unaSolaClase() {
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(1L, 18, 0, 20, 0)), 60);

            assertThat(bloques).hasSize(1);
            assertThat(bloques.get(0).cantidadDeClases()).isEqualTo(1);
            assertThat(bloques.get(0).horaInicio()).isEqualTo(LocalTime.of(18, 0));
            assertThat(bloques.get(0).horaFin()).isEqualTo(LocalTime.of(20, 0));
        }

        @Test
        @DisplayName("caso 1: clases contiguas exactas van juntas")
        void contiguasExactas() {
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(1L, 18, 0, 20, 0),
                        horario(2L, 20, 0, 22, 0)), 60);

            assertThat(bloques).hasSize(1);
            assertThat(bloques.get(0).cantidadDeClases()).isEqualTo(2);
            assertThat(bloques.get(0).horaInicio()).isEqualTo(LocalTime.of(18, 0));
            assertThat(bloques.get(0).horaFin()).isEqualTo(LocalTime.of(22, 0));
        }

        @Test
        @DisplayName("caso 2: hueco menor al umbral mantiene un solo bloque")
        void huecoMenorAlUmbral() {
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(1L, 18, 0, 20, 0),
                        horario(2L, 20, 30, 22, 0)), 60);

            assertThat(bloques).hasSize(1);
            assertThat(bloques.get(0).horaFin()).isEqualTo(LocalTime.of(22, 0));
        }

        @Test
        @DisplayName("borde: hueco exactamente igual al umbral todavía es un solo bloque")
        void huecoIgualAlUmbral() {
            // 20:00 -> 21:00 son 60 minutos justos, y la regla es "menor o igual".
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(1L, 18, 0, 20, 0),
                        horario(2L, 21, 0, 23, 0)), 60);

            assertThat(bloques).hasSize(1);
        }

        @Test
        @DisplayName("caso 3: un minuto más que el umbral ya son dos bloques")
        void huecoUnMinutoMayorAlUmbral() {
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(1L, 18, 0, 20, 0),
                        horario(2L, 21, 1, 23, 0)), 60);

            assertThat(bloques).hasSize(2);
            assertThat(bloques.get(0).horaFin()).isEqualTo(LocalTime.of(20, 0));
            assertThat(bloques.get(1).horaInicio()).isEqualTo(LocalTime.of(21, 1));
        }

        @Test
        @DisplayName("caso 4: clases contiguas de materias distintas van al mismo bloque")
        void materiasDistintasSeAgrupanIgual() {
            Horario matematica = horario(1L, 18, 0, 20, 0, "MAT", "Matemática");
            Horario literatura = horario(2L, 20, 0, 22, 0, "LIT", "Literatura");

            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(matematica, literatura), 60);

            // Lo que el bloque acredita es que la persona estuvo, no qué dictó.
            assertThat(bloques).hasSize(1);
            assertThat(bloques.get(0).cantidadDeClases()).isEqualTo(2);
        }

        @Test
        @DisplayName("caso 5: clases solapadas quedan en un solo bloque")
        void clasesSolapadas() {
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(1L, 18, 0, 20, 0),
                        horario(2L, 19, 0, 21, 0)), 0);

            // Con umbral 0: el hueco es negativo, y si se pisan la persona estuvo igual.
            assertThat(bloques).hasSize(1);
            assertThat(bloques.get(0).horaFin()).isEqualTo(LocalTime.of(21, 0));
        }

        @Test
        @DisplayName("una clase contenida en otra no acorta el fin del bloque")
        void claseContenidaEnOtra() {
            // 19:00-20:00 empieza después pero termina antes que 18:00-22:00. Si el fin del
            // bloque siguiera al último horario agregado en vez de al máximo, el bloque
            // cerraría a las 20:00 y partiría la clase larga.
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(1L, 18, 0, 22, 0),
                        horario(2L, 19, 0, 20, 0)), 0);

            assertThat(bloques).hasSize(1);
            assertThat(bloques.get(0).horaFin()).isEqualTo(LocalTime.of(22, 0));
            assertThat(bloques.get(0).ultimoHorario().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("caso 6: la cadena es transitiva y no tiene tope de duración")
        void cadenaTransitivaSinTope() {
            // 08:00 a 22:00 encadenado por huecos de 30 min: un solo bloque de catorce horas.
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(1L, 8, 0, 12, 0),
                        horario(2L, 12, 30, 16, 0),
                        horario(3L, 16, 30, 19, 0),
                        horario(4L, 19, 30, 22, 0)), 30);

            assertThat(bloques).hasSize(1);
            assertThat(bloques.get(0).cantidadDeClases()).isEqualTo(4);
            assertThat(bloques.get(0).horaInicio()).isEqualTo(LocalTime.of(8, 0));
            assertThat(bloques.get(0).horaFin()).isEqualTo(LocalTime.of(22, 0));
        }

        @Test
        @DisplayName("con umbral 0 solo se agrupan las contiguas exactas")
        void umbralCero() {
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(1L, 18, 0, 20, 0),
                        horario(2L, 20, 0, 22, 0),
                        horario(3L, 22, 1, 23, 0)), 0);

            assertThat(bloques).hasSize(2);
            assertThat(bloques.get(0).cantidadDeClases()).isEqualTo(2);
            assertThat(bloques.get(1).cantidadDeClases()).isEqualTo(1);
        }

        @Test
        @DisplayName("la entrada desordenada no cambia el resultado")
        void entradaDesordenada() {
            // findHoyParaDocente no ordena, así que el agrupador no puede depender del orden.
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(3L, 20, 0, 22, 0),
                        horario(1L, 8, 0, 10, 0),
                        horario(2L, 18, 0, 20, 0)), 60);

            assertThat(bloques).hasSize(2);
            assertThat(bloques.get(0).horaInicio()).isEqualTo(LocalTime.of(8, 0));
            assertThat(bloques.get(0).cantidadDeClases()).isEqualTo(1);
            assertThat(bloques.get(1).horaInicio()).isEqualTo(LocalTime.of(18, 0));
            assertThat(bloques.get(1).cantidadDeClases()).isEqualTo(2);
        }

        @Test
        @DisplayName("caso 7: los extremos del día no se unen por dar la vuelta al reloj")
        void extremosDelDiaNoSeUnen() {
            // 23:45 y 00:15 están a 30 minutos si uno cruza la medianoche, pero el bloque no
            // cruza: son dos jornadas. No hay aritmética circular en el agrupamiento, y el
            // horario que estuviera del otro lado de la medianoche además tendría otro
            // dia_semana, así que ni siquiera llega en la misma lista.
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(1L, 0, 15, 1, 0),
                        horario(2L, 23, 0, 23, 45)), 60);

            assertThat(bloques).hasSize(2);
            assertThat(bloques.get(0).horaInicio()).isEqualTo(LocalTime.of(0, 15));
            assertThat(bloques.get(1).horaInicio()).isEqualTo(LocalTime.of(23, 0));
        }

        @Test
        @DisplayName("dos bloques con varias clases cada uno")
        void dosBloquesConVariasClases() {
            List<BloqueDeHorarios> bloques = service.agrupar(
                List.of(horario(1L, 8, 0, 10, 0),
                        horario(2L, 10, 0, 12, 0),
                        horario(3L, 18, 0, 20, 0),
                        horario(4L, 20, 0, 22, 0)), 60);

            assertThat(bloques).hasSize(2);
            assertThat(bloques.get(0).cantidadDeClases()).isEqualTo(2);
            assertThat(bloques.get(0).horaFin()).isEqualTo(LocalTime.of(12, 0));
            assertThat(bloques.get(1).cantidadDeClases()).isEqualTo(2);
            assertThat(bloques.get(1).horaInicio()).isEqualTo(LocalTime.of(18, 0));
        }
    }

    // ========================================================================
    //  bloquesDelDia y bloqueEnCurso
    // ========================================================================

    @Nested
    @DisplayName("bloquesDelDia")
    class BloquesDelDia {

        @Test
        @DisplayName("aplica el umbral configurado en la institución")
        void aplicaElUmbralDeLaInstitucion() {
            // Con umbral 15 el hueco de 30 min parte el día en dos bloques; con 60, en uno.
            unLunesConHorarios(horario(1L, 18, 0, 20, 0), horario(2L, 20, 30, 22, 0));
            conUmbral((short) 15);

            assertThat(service.bloquesDelDia(DOCENTE_ID, UN_LUNES)).hasSize(2);
        }

        @Test
        @DisplayName("si la institución no aparece usa el umbral por defecto")
        void institucionAusenteUsaDefault() {
            unLunesConHorarios(horario(1L, 18, 0, 20, 0), horario(2L, 21, 0, 23, 0));
            when(institucionRepository.findById(TENANT_A)).thenReturn(Optional.empty());

            // Default 60: el hueco de 60 justos entra.
            assertThat(service.bloquesDelDia(DOCENTE_ID, UN_LUNES)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("bloqueEnCurso")
    class BloqueEnCurso {

        @Test
        @DisplayName("devuelve el bloque completo cuando alguna de sus clases está en ventana")
        void devuelveElBloqueCompleto() {
            unLunesConHorarios(horario(1L, 18, 0, 20, 0), horario(2L, 20, 0, 22, 0));
            conUmbral((short) 60);

            // 21:00 está dentro de la segunda clase, pero el bloque es el de las dos.
            Optional<BloqueDeHorarios> bloque =
                service.bloqueEnCurso(DOCENTE_ID, UN_LUNES.atTime(21, 0));

            assertThat(bloque).isPresent();
            assertThat(bloque.get().cantidadDeClases()).isEqualTo(2);
            assertThat(bloque.get().horaInicio()).isEqualTo(LocalTime.of(18, 0));
        }

        @Test
        @DisplayName("vacío cuando no hay ninguna clase en ventana")
        void vacioSinClaseEnVentana() {
            unLunesConHorarios(horario(1L, 18, 0, 20, 0));
            conUmbral((short) 60);

            assertThat(service.bloqueEnCurso(DOCENTE_ID, UN_LUNES.atTime(15, 0))).isEmpty();
        }

        @Test
        @DisplayName("entra por la tolerancia previa de la primera clase")
        void entraPorLaToleranciaPrevia() {
            unLunesConHorarios(horario(1L, 18, 0, 20, 0));
            conUmbral((short) 60);

            // 17:50 cae en [18:00 - 15, 20:00].
            assertThat(service.bloqueEnCurso(DOCENTE_ID, UN_LUNES.atTime(17, 50))).isPresent();
        }

        @Test
        @DisplayName("mientras el segundo bloque no abrió su ventana, sigue ganando el primero")
        void elSegundoBloqueNoSeAdelanta() {
            // Hueco de 15 min con umbral 10: son dos bloques. La ventana del segundo abre a
            // las 20:00 (20:15 menos su tolerancia de 15), así que a las 19:50 el único en
            // curso es el primero.
            unLunesConHorarios(horario(1L, 18, 0, 20, 0), horario(2L, 20, 15, 22, 0));
            conUmbral((short) 10);

            Optional<BloqueDeHorarios> bloque =
                service.bloqueEnCurso(DOCENTE_ID, UN_LUNES.atTime(19, 50));

            assertThat(bloque).isPresent();
            assertThat(bloque.get().horaInicio()).isEqualTo(LocalTime.of(18, 0));
        }

        @Test
        @DisplayName("con dos bloques en ventana a la vez elige el de inicio más cercano")
        void desempatePorInicioMasCercano() {
            // Para que dos bloques estén en ventana al mismo tiempo, la tolerancia tiene que
            // ser mayor que el umbral de separación. Hueco de 10 min con umbral 5: son dos
            // bloques, pero la ventana del segundo abre 19:55 (20:10 menos 15) y el primero
            // recién termina 20:00. A las 19:57 los dos están en curso.
            unLunesConHorarios(horario(1L, 18, 0, 20, 0), horario(2L, 20, 10, 22, 0));
            conUmbral((short) 5);

            Optional<BloqueDeHorarios> bloque =
                service.bloqueEnCurso(DOCENTE_ID, UN_LUNES.atTime(19, 57));

            assertThat(bloque).isPresent();
            // 20:10 está a 13 minutos y 18:00 a 117: gana el segundo bloque.
            assertThat(bloque.get().horaInicio()).isEqualTo(LocalTime.of(20, 10));
        }
    }

    // ========================================================================
    //  Fixtures
    // ========================================================================

    // 2026-05-25 fue lunes.
    private static final LocalDate UN_LUNES = LocalDate.of(2026, 5, 25);

    // Deja al docente con esos horarios el lunes, para los métodos que van al repositorio.
    private void unLunesConHorarios(Horario... horarios) {
        when(horarioRepository.findHoyParaDocente(eq(DOCENTE_ID), eq((byte) 1), any(), eq(TENANT_A)))
            .thenReturn(List.of(horarios));
    }

    // Fija el umbral de separación de la institución del tenant actual.
    private void conUmbral(short umbralMin) {
        Institucion institucion = Institucion.builder()
            .id(TENANT_A).nombre("CENT 35").umbralSeparacionMin(umbralMin).activo(true)
            .build();
        when(institucionRepository.findById(TENANT_A)).thenReturn(Optional.of(institucion));
    }

    // Horario de lunes con su propia comisión, así representa una comisión distinta del
    // mismo docente. Tolerancia 15, la de por defecto del sistema.
    private Horario horario(Long id, int inicioHora, int inicioMin, int finHora, int finMin) {
        return horario(id, inicioHora, inicioMin, finHora, finMin, "MAT", "Matemática");
    }

    private Horario horario(Long id, int inicioHora, int inicioMin, int finHora, int finMin,
                            String codigoMateria, String nombreMateria) {
        Materia materia = Materia.builder()
            .id(id).codigo(codigoMateria).nombre(nombreMateria).build();
        materia.setInstitucionId(TENANT_A);
        Comision comision = Comision.builder()
            .id(id).codigo("C" + id).materia(materia).activo(true).build();
        return Horario.builder()
            .id(id).comision(comision)
            .diaSemana((byte) 1)
            .horaInicio(LocalTime.of(inicioHora, inicioMin))
            .horaFin(LocalTime.of(finHora, finMin))
            .toleranciaMin((short) 15)
            .activo(true)
            .build();
    }
}
