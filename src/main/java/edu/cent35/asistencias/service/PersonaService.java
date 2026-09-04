package edu.cent35.asistencias.service;

import java.util.Objects;
import edu.cent35.asistencias.repository.CambioIdentidadRepository;
import edu.cent35.asistencias.model.CambioIdentidad;
import edu.cent35.asistencias.dto.InstantaneaIdentidad;
import edu.cent35.asistencias.config.TenantContext;
import edu.cent35.asistencias.dto.ImpactoIdentidadDto;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.model.Persona;
import edu.cent35.asistencias.model.Usuario;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.repository.PersonaRepository;
import edu.cent35.asistencias.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Responde qué roles cumple una persona y a qué alcanza tocarla, para que ninguna pantalla
 * modifique una identidad compartida sin avisar (ADR-0016).
 * No hace altas ni bajas: solo mira. Quien crea o edita sigue siendo {@code DocenteService} o
 * {@code UsuarioService}, que consultan acá antes de escribir.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonaService {

    private final PersonaRepository personaRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocenteRepository docenteRepository;
    private final CambioIdentidadRepository cambioIdentidadRepository;

    // Busca una identidad por documento dentro de la institución actual.
    @Transactional(readOnly = true)
    public Optional<Persona> buscarPorDni(String dni) {
        return personaRepository.buscarPorDni(TenantContext.getRequired(), dni);
    }

    /**
     * Qué alcanza un alta de docente sobre un DNI que ya existe.
     *
     * <p>El nombre propuesto viaja aparte del registrado para que la pantalla pueda mostrarlos
     * enfrentados: es la única forma de que quien carga se dé cuenta de que tipeó mal el
     * documento. "Ya existe" no alcanza; "ya existe y se llama distinto" sí.
     */
    @Transactional(readOnly = true)
    public ImpactoIdentidadDto impactoDeAlta(Persona existente, String nombrePropuesto) {
        return construir(existente,
                         nombrePropuesto,
                         ImpactoIdentidadDto.Motivo.ALTA_SOBRE_PERSONA_EXISTENTE);
    }

    // Qué alcanza editar la identidad de una persona: si además tiene cuenta, o varios periodos
    // docentes, el cambio se ve en pantallas que quien edita no esta mirando.
    @Transactional(readOnly = true)
    public ImpactoIdentidadDto impactoDeEdicion(Persona persona, String nombrePropuesto) {
        return construir(persona,
                         nombrePropuesto,
                         ImpactoIdentidadDto.Motivo.EDICION_ALCANZA_VARIOS_ROLES);
    }

    /**
     * Si editar esta identidad hace falta confirmarlo.
     *
     * <p>Solo cuando la persona cumple más de un rol. Editar a alguien que únicamente es docente
     * no necesita ninguna advertencia: el cambio se ve entero en la pantalla donde se hizo, y
     * pedir confirmación ahí entrenaría a aceptar sin leer, que es peor que no preguntar.
     */
    @Transactional(readOnly = true)
    public boolean edicionRequiereConfirmacion(Persona persona) {
        // Una cuenta institucional no tiene persona detras (V018): no hay identidad compartida
        // con nadie, asi que no hay nada que advertir.
        if (persona == null) {
            return false;
        }
        Long tenantId = TenantContext.getRequired();
        boolean tieneCuenta = usuarioRepository.cuentaDe(tenantId, persona.getId()).isPresent();
        int periodos = docenteRepository.periodosDe(tenantId, persona.getId()).size();
        return tieneCuenta && periodos > 0;
    }

    /**
     * Registra qué campos de identidad cambiaron, quién los cambió y desde dónde.
     *
     * <p>Deja una fila por campo modificado y ninguna si no cambió nada: guardar "se editó la
     * persona 5" sin decir qué se editó no le sirve a nadie, y guardar la persona entera cada vez
     * multiplicaría las copias de datos personales sin agregar capacidad de respuesta.
     *
     * <p>Se llama <b>después</b> de escribir, con la instantánea tomada antes.
     */
    @Transactional
    public void registrarCambios(Persona persona, InstantaneaIdentidad antes, Long usuarioId,
                                 String origen) {
        InstantaneaIdentidad despues = InstantaneaIdentidad.de(persona);

        anotarSiCambio(persona, "dni",      antes.dni(),      despues.dni(),      usuarioId, origen);
        anotarSiCambio(persona, "nombre",   antes.nombre(),   despues.nombre(),   usuarioId, origen);
        anotarSiCambio(persona, "apellido", antes.apellido(), despues.apellido(), usuarioId, origen);
        anotarSiCambio(persona, "email",    antes.email(),    despues.email(),    usuarioId, origen);
        anotarSiCambio(persona, "telefono", antes.telefono(), despues.telefono(), usuarioId, origen);
    }

    // Historial de una identidad, del cambio mas reciente al mas viejo.
    @Transactional(readOnly = true)
    public List<CambioIdentidad> historialDe(Long personaId) {
        return cambioIdentidadRepository.historialDe(TenantContext.getRequired(), personaId);
    }

    private void anotarSiCambio(Persona persona, String campo, String anterior, String nuevo,
                                Long usuarioId, String origen) {
        if (Objects.equals(anterior, nuevo)) {
            return;
        }
        CambioIdentidad c = CambioIdentidad.builder()
            .personaId(persona.getId())
            .usuarioId(usuarioId)
            .campo(campo)
            .valorAnterior(anterior)
            .valorNuevo(nuevo)
            .origen(origen)
            .build();
        c.setInstitucionId(persona.getInstitucionId());
        cambioIdentidadRepository.save(c);

        log.info("Identidad modificada: persona_id={}, campo={}, por usuario_id={}, origen={}",
                 persona.getId(), campo, usuarioId, origen);
    }

    private ImpactoIdentidadDto construir(Persona persona, String nombrePropuesto,
                                          ImpactoIdentidadDto.Motivo motivo) {
        Long tenantId = TenantContext.getRequired();
        Optional<Usuario> cuenta = usuarioRepository.cuentaDe(tenantId, persona.getId());
        List<Docente> periodos = docenteRepository.periodosDe(tenantId, persona.getId());

        return ImpactoIdentidadDto.builder()
            .motivo(motivo)
            .personaId(persona.getId())
            .dni(persona.getDni())
            .nombreRegistrado(persona.getNombreCompleto())
            .nombrePropuesto(nombrePropuesto)
            .tieneCuenta(cuenta.isPresent())
            .usernameCuenta(cuenta.map(Usuario::getUsername).orElse(null))
            .tieneVinculoVigente(periodos.stream().anyMatch(d -> Boolean.TRUE.equals(d.getActivo())))
            .periodosDocentes(periodos.size())
            .build();
    }
}
