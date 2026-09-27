package edu.cent35.asistencias.service;

import edu.cent35.asistencias.dto.AsistenciaReporteRowDto;
import edu.cent35.asistencias.dto.GraficosDeAsistenciaDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las tres cuentas que alimentan los gráficos del reporte (RF-33).
 *
 * <p>Va como test de unidad porque acá lo único que hay son sumas y un reparto de anchos; que
 * eso llegue dibujado a la pantalla lo cubre {@code GraficosDelReporteIT}. Lo que se prueba
 * son las decisiones que no se ven en el resultado: qué pasa con una clase sin dato de
 * salida, qué pasa cuando los porcentajes no cierran en cien, y contra qué se mide una barra
 * del ranking.
 */
class GraficosDeAsistenciaServiceTest {

    private final GraficosDeAsistenciaService service = new GraficosDeAsistenciaService();

    // Dos lunes seguidos de junio de 2026 y el miércoles del primero.
    private static final LocalDate LUNES_1 = LocalDate.of(2026, 6, 1);
    private static final LocalDate MIERCOLES_1 = LocalDate.of(2026, 6, 3);
    private static final LocalDate LUNES_2 = LocalDate.of(2026, 6, 8);

    // ========================================================================
    //  Semana a semana
    // ========================================================================

    @Test
    @DisplayName("Las clases se agrupan por semana calendario, tomando el lunes")
    void agrupaPorSemana() {
        GraficosDeAsistenciaDto g = service.resumir(List.of(
            clase(LUNES_1, 120, 120),
            clase(MIERCOLES_1, 120, 60),      // misma semana que el lunes
            clase(LUNES_2, 120, 120)));

        assertThat(g.getSemanas()).hasSize(2);
        assertThat(g.getSemanas().get(0).getLunes()).isEqualTo(LUNES_1);
        assertThat(g.getSemanas().get(0).getPorcentaje())
            .as("180 dictados sobre 240 programados en la primera semana")
            .isEqualTo(75);
        assertThat(g.getSemanas().get(1).getPorcentaje()).isEqualTo(100);
    }

    @Test
    @DisplayName("Una clase sin dato de salida no baja el porcentaje de su semana")
    void laClaseSinDatoNoSuma() {
        GraficosDeAsistenciaDto g = service.resumir(List.of(
            clase(LUNES_1, 120, 120),
            clase(LUNES_1, 120, null)));      // sin marca de salida

        assertThat(g.getSemanas()).hasSize(1);
        assertThat(g.getSemanas().get(0).getPorcentaje())
            .as("contarla como cero diría que esa clase no se dictó, y lo único que se sabe "
                + "es que falta la marca")
            .isEqualTo(100);
        assertThat(g.getClasesSinDato()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una semana entera sin dato se dibuja al ras y sin porcentaje, no en cero")
    void laSemanaSinDatoNoEsCero() {
        GraficosDeAsistenciaDto g = service.resumir(List.of(clase(LUNES_1, 120, null)));

        GraficosDeAsistenciaDto.ColumnaSemanal semana = g.getSemanas().get(0);
        assertThat(semana.isSinDato()).isTrue();
        assertThat(semana.getPorcentaje()).isNull();
        assertThat(semana.getAlto())
            .as("una columna en cero afirmaría que no se dictó nada; esto es la ausencia de "
                + "una afirmación")
            .isEqualTo(2f);
    }

    @Test
    @DisplayName("Las columnas se reparten el lienzo y ninguna se sale")
    void lasColumnasEntranEnElLienzo() {
        List<AsistenciaReporteRowDto> muchas = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            muchas.add(clase(LUNES_1.plusWeeks(i), 120, 120));
        }

        GraficosDeAsistenciaDto g = service.resumir(muchas);

        assertThat(g.getSemanas()).hasSize(30);
        assertThat(g.getSemanas()).allSatisfy(s -> {
            assertThat(s.getX()).isGreaterThanOrEqualTo(0f);
            assertThat(s.getX() + s.getAncho())
                .as("la última columna no puede pasarse del ancho del lienzo")
                .isLessThanOrEqualTo(720f);
            assertThat(s.getAncho()).isGreaterThan(0f);
        });
    }

    // ========================================================================
    //  Reparto de estados
    // ========================================================================

    @Test
    @DisplayName("Los tres tramos de la barra suman 100 aunque los porcentajes no cierren")
    void losTramosSuman100() {
        // Un tercio cada uno: redondeados dan 33+33+33=99, y en una barra apilada ese punto
        // que falta se ve como una raya al final.
        GraficosDeAsistenciaDto g = service.resumir(List.of(
            clase(LUNES_1, 120, 120, "PRESENTE"),
            clase(LUNES_1, 120, 120, "TARDE"),
            clase(LUNES_1, 120, 0, "AUSENTE")));

        GraficosDeAsistenciaDto.RepartoDeEstados e = g.getEstados();
        assertThat(e.getPresentes()).isEqualTo(1);
        assertThat(e.getTarde()).isEqualTo(1);
        assertThat(e.getAusentes()).isEqualTo(1);
        assertThat(e.getAnchoPresentes() + e.getAnchoTarde() + e.getAnchoAusentes())
            .as("el último tramo se lleva lo que sobra en vez de calcularse aparte")
            .isEqualTo(100);
    }

    @Test
    @DisplayName("Sin ninguna clase, los anchos van en cero y no se divide por cero")
    void sinClasesNoRompe() {
        GraficosDeAsistenciaDto g = service.resumir(List.of());

        assertThat(g.isVacio()).isTrue();
        assertThat(g.getEstados().total()).isZero();
        assertThat(g.getEstados().getAnchoPresentes()).isZero();
        assertThat(g.getSemanas()).isEmpty();
        assertThat(g.getDocentes()).isEmpty();
    }

    // ========================================================================
    //  Ranking
    // ========================================================================

    @Test
    @DisplayName("El ranking ordena por ausencias y dice sobre cuantas clases")
    void elRankingOrdenaYContextualiza() {
        GraficosDeAsistenciaDto g = service.resumir(List.of(
            clase(LUNES_1, 120, 0, "AUSENTE", "Pérez, Ana"),
            clase(LUNES_1, 120, 0, "AUSENTE", "Pérez, Ana"),
            clase(LUNES_1, 120, 0, "AUSENTE", "Gómez, Luis"),
            clase(LUNES_1, 120, 120, "PRESENTE", "Gómez, Luis")));

        assertThat(g.getDocentes()).hasSize(2);
        assertThat(g.getDocentes().get(0).getEtiqueta()).isEqualTo("Pérez, Ana");
        assertThat(g.getDocentes().get(0).getAusencias()).isEqualTo(2);
        assertThat(g.getDocentes().get(0).getClases())
            .as("dos ausencias en dos clases y dos en cuarenta no son lo mismo")
            .isEqualTo(2);
        assertThat(g.getDocentes().get(0).getAncho())
            .as("el primero llena la barra: el ancho va contra el máximo de la lista")
            .isEqualTo(100);
        assertThat(g.getDocentes().get(1).getAncho())
            .as("y el segundo, la mitad de ese máximo")
            .isEqualTo(50);
    }

    @Test
    @DisplayName("Quien no falto no aparece en el ranking de ausencias")
    void sinAusenciasNoEntraAlRanking() {
        GraficosDeAsistenciaDto g = service.resumir(List.of(
            clase(LUNES_1, 120, 120, "PRESENTE", "Pérez, Ana")));

        assertThat(g.getDocentes())
            .as("un ranking de ausencias con gente que no falto no señala nada")
            .isEmpty();
        assertThat(g.getMaterias()).isEmpty();
    }

    // ------------------------------------------------------------------------

    private AsistenciaReporteRowDto clase(LocalDate fecha, int programados, Integer efectivos) {
        return clase(fecha, programados, efectivos, "PRESENTE");
    }

    private AsistenciaReporteRowDto clase(LocalDate fecha, int programados, Integer efectivos,
                                          String estado) {
        return clase(fecha, programados, efectivos, estado, "Pérez, Ana");
    }

    private AsistenciaReporteRowDto clase(LocalDate fecha, int programados, Integer efectivos,
                                          String estado, String docente) {
        String[] partes = docente.split(", ");
        return AsistenciaReporteRowDto.builder()
            .fecha(fecha)
            .docenteApellido(partes[0]).docenteNombre(partes[1])
            .materiaCodigo("PRG1").materiaNombre("Programación I")
            .estado(estado).metodo("AUTOMATICO")
            .minutosProgramados(programados).minutosEfectivos(efectivos)
            .build();
    }
}
