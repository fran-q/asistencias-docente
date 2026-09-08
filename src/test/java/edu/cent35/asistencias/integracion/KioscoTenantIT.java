package edu.cent35.asistencias.integracion;

import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.model.Institucion;
import edu.cent35.asistencias.model.PuestoCaptura;
import edu.cent35.asistencias.model.Rol;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.InstitucionRepository;
import edu.cent35.asistencias.repository.PuestoCapturaRepository;
import edu.cent35.asistencias.repository.RolRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import edu.cent35.asistencias.service.PuestoCapturaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que resolver la institución desde el equipo autorizado —sin sesión de por medio—
 * no rompa el aislamiento entre instituciones (RF-84, RF-88, ADR-0019).
 * <p>
 * Es el único punto del sistema donde el tenant sale de algo que no es el usuario
 * autenticado, así que un error acá no es un bug de funcionalidad: es una fuga entre
 * instituciones. Por eso se prueba contra la base real y con dos tenants sembrados, y no con
 * mocks: lo que se está ejerciendo es la consulta que decide de quién son los datos.
 */
@SpringBootTest
@ActiveProfiles("test")
class KioscoTenantIT {

    @Autowired private PuestoCapturaService puestoService;
    @Autowired private PuestoCapturaRepository puestoRepository;
    @Autowired private InstitucionRepository institucionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;

    private Long institucionA;
    private Long institucionB;
    private Usuario cuentaA;
    private Usuario cuentaB;

    @BeforeEach
    void preparar() {
        TenantContext.clear();
        limpiar();

        institucionA = institucionRepository.save(
            Institucion.builder().nombre("Instituto A").activo(true).build()).getId();
        institucionB = institucionRepository.save(
            Institucion.builder().nombre("Instituto B").activo(true).build()).getId();

        Rol r = new Rol();
        r.setCodigo("INSTITUCION");
        r.setDescripcion("Cuenta institucional");
        Rol rol = rolRepository.save(r);

        cuentaA = cuenta("admin.a", institucionA, rol);
        cuentaB = cuenta("admin.b", institucionB, rol);
    }

    @AfterEach
    void limpiarDespues() {
        TenantContext.clear();
        limpiar();
    }

    // ========================================================================
    //  Aislamiento
    // ========================================================================

    @Test
    @DisplayName("El token de una institucion resuelve esa institucion y ninguna otra")
    void cadaTokenResuelveLoSuyo() {
        String tokenA = kioscoEn(institucionA, cuentaA);
        String tokenB = kioscoEn(institucionB, cuentaB);

        assertThat(puestoService.resolverKiosco(tokenA))
            .get().extracting(PuestoCaptura::getInstitucionId).isEqualTo(institucionA);
        assertThat(puestoService.resolverKiosco(tokenB))
            .get().extracting(PuestoCaptura::getInstitucionId).isEqualTo(institucionB);
    }

    @Test
    @DisplayName("Un token inventado no resuelve ninguna institucion")
    void tokenInventadoNoResuelve() {
        kioscoEn(institucionA, cuentaA);

        assertThat(puestoService.resolverKiosco("esto-no-es-un-token")).isEmpty();
        assertThat(puestoService.resolverKiosco("")).isEmpty();
        assertThat(puestoService.resolverKiosco(null)).isEmpty();
    }

    @Test
    @DisplayName("La resolucion no se apoya en el TenantContext que hubiera quedado seteado")
    void noHeredaElTenantDelContexto() {
        // Si la consulta se apoyara en el contexto en vez de en el token, un hilo con el
        // tenant de A resolveria el puesto de B como si fuera de A.
        String tokenB = kioscoEn(institucionB, cuentaB);
        TenantContext.set(institucionA);

        assertThat(puestoService.resolverKiosco(tokenB))
            .get().extracting(PuestoCaptura::getInstitucionId).isEqualTo(institucionB);
    }

    // ========================================================================
    //  Qué habilita y qué no
    // ========================================================================

    @Test
    @DisplayName("Un puesto designado pero sin kiosco habilitado NO resuelve")
    void designadoNoAlcanza() {
        // Designar dice "la captura ocurre aca"; habilitar el kiosco dice "ademas puede
        // ocurrir sin nadie mirando". Son dos permisos y hacen falta los dos (RF-85).
        String token = puestoService.designar(institucionA, "Secretaria", cuentaA)
            .getTokenEnClaro();

        assertThat(puestoService.resolverKiosco(token)).isEmpty();
    }

    @Test
    @DisplayName("Un puesto revocado NO resuelve, aunque tuviera el kiosco habilitado")
    void revocadoNoResuelve() {
        String token = kioscoEn(institucionA, cuentaA);
        PuestoCaptura p = unicoDe(institucionA);
        p.setActivo(false);
        puestoRepository.save(p);

        assertThat(puestoService.resolverKiosco(token)).isEmpty();
    }

    // ========================================================================
    //  Vencimiento por inactividad (RF-88)
    // ========================================================================

    @Test
    @DisplayName("Una credencial usada hace poco sigue sirviendo")
    void credencialEnUsoSirve() {
        String token = kioscoEn(institucionA, cuentaA);
        conUltimoUso(institucionA, LocalDateTime.now().minusDays(3));

        assertThat(puestoService.resolverKiosco(token)).isPresent();
    }

    @Test
    @DisplayName("Una credencial sin uso por mas dias que el limite deja de servir")
    void credencialVencidaNoSirve() {
        // Lo que caduca es el token de una maquina que dejo de usarse: la que se robaron, la
        // que se dio de baja. En uso normal se renueva sola en cada marca.
        String token = kioscoEn(institucionA, cuentaA);
        conUltimoUso(institucionA, LocalDateTime.now().minusDays(31));

        assertThat(puestoService.resolverKiosco(token)).isEmpty();
    }

    @Test
    @DisplayName("Un puesto recien habilitado, sin usar todavia, si resuelve")
    void reciendHabilitadoResuelve() {
        // Sin ultimoUsoEn cuenta desde creadoEn. Tratarlo como vencido dejaria el kiosco sin
        // poder arrancar nunca: el primer uso no podria ocurrir.
        String token = kioscoEn(institucionA, cuentaA);
        assertThat(unicoDe(institucionA).getUltimoUsoEn()).isNull();

        assertThat(puestoService.resolverKiosco(token)).isPresent();
    }

    @Test
    @DisplayName("El limite de dias es configurable")
    void elLimiteEsConfigurable() {
        String token = kioscoEn(institucionA, cuentaA);
        conUltimoUso(institucionA, LocalDateTime.now().minusDays(5));

        ReflectionTestUtils.setField(puestoService, "diasDeInactividad", 3L);
        assertThat(puestoService.resolverKiosco(token)).isEmpty();

        ReflectionTestUtils.setField(puestoService, "diasDeInactividad", 30L);
        assertThat(puestoService.resolverKiosco(token)).isPresent();
    }

    // ========================================================================
    //  Fixtures
    // ========================================================================

    // Designa un puesto y ademas lo habilita para operar sin sesion.
    private String kioscoEn(Long institucionId, Usuario designante) {
        String token = puestoService
            .designar(institucionId, "Secretaria " + institucionId, designante)
            .getTokenEnClaro();
        PuestoCaptura p = unicoDe(institucionId);
        p.setKioscoHabilitado(true);
        p.setKioscoHabilitadoEn(LocalDateTime.now());
        p.setKioscoHabilitadoPor(designante);
        puestoRepository.save(p);
        return token;
    }

    private PuestoCaptura unicoDe(Long institucionId) {
        return puestoRepository.deInstitucion(institucionId).get(0);
    }

    private void conUltimoUso(Long institucionId, LocalDateTime cuando) {
        PuestoCaptura p = unicoDe(institucionId);
        p.setUltimoUsoEn(cuando);
        puestoRepository.save(p);
    }

    private Usuario cuenta(String username, Long institucionId, Rol rol) {
        Usuario u = Usuario.builder()
            .username(username)
            .passwordHash("x")
            .email(username + "@test.local")
            .rol(rol)
            .activo(true)
            .build();
        u.setInstitucionId(institucionId);
        return usuarioRepository.save(u);
    }

    private void limpiar() {
        puestoRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        institucionRepository.deleteAll();
    }
}
