package edu.cent35.asistencias.service;
import edu.cent35.asistencias.dto.InstantaneaIdentidad;
import edu.cent35.asistencias.model.*;
import edu.cent35.asistencias.repository.*;

import edu.cent35.asistencias.repository.ComisionRepository;
import edu.cent35.asistencias.repository.MateriaRepository;
import edu.cent35.asistencias.model.Docente;
import edu.cent35.asistencias.repository.DocenteRepository;
import edu.cent35.asistencias.config.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * ABM de los vínculos docentes de la institución actual (RF-07, ADR-0016): la identidad se
 * guarda en {@code Persona} y acá vive el período laboral, con su legajo y sus fechas.
 * Una persona puede tener varios vínculos: si vuelve después de haberse ido, se reutiliza su
 * persona y se abre un período nuevo, en vez de pisar las fechas del anterior. La baja es
 * lógica y se bloquea mientras siga siendo titular de materias o esté asignada a comisiones.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocenteService {

    private final DocenteRepository docenteRepository;
    private final PersonaRepository personaRepository;
    private final PersonaService personaService;
    private final MateriaRepository materiaRepository;
    private final ComisionRepository comisionRepository;

    @Transactional(readOnly = true)
    // Lista los vínculos del tenant, vigentes primero.
    public List<Docente> listar() {
        return docenteRepository.listarDelTenant(TenantContext.getRequired());
    }

    @Transactional(readOnly = true)
    // Solo los vigentes, para poblar los combos de los formularios.
    public List<Docente> activosParaSelector() {
        return docenteRepository.listarVigentesDelTenant(TenantContext.getRequired());
    }

    @Transactional(readOnly = true)
    // Busca por id validando que sea del tenant actual; si no, responde "no encontrado".
    // Va por la consulta con JOIN FETCH y no por findById: este último no pasa por el filtro
    // de tenant y además dejaría la persona sin cargar para cuando la use la vista.
    public Docente buscarPorId(Long id) {
        Long tenantId = TenantContext.getRequired();
        return docenteRepository.buscarDelTenant(tenantId, id)
            .orElseThrow(() -> {
                log.warn("Cross-tenant blocked: tenant {} intento acceder docente id={}", tenantId, id);
                return new EntityNotFoundException("Docente no encontrado");
            });
    }

    @Transactional
    // Da de alta un docente, exigiendo DNI y legajo sin repetir dentro de la institución.
    public Docente crear(String dni, String legajo, String nombre, String apellido,
                         String email, String telefono, Long usuarioActualId) {
        return crear(dni, legajo, nombre, apellido, email, telefono, false, usuarioActualId);
    }

    @Transactional
    // Igual que el anterior, con la confirmacion ya dada: solo entra por aca el segundo intento,
    // despues de que alguien miro la pantalla de aviso y dijo que si.
    public Docente crear(String dni, String legajo, String nombre, String apellido,
                         String email, String telefono, boolean confirmado,
                         Long usuarioActualId) {

        Long tenantId = TenantContext.getRequired();

        String dniNorm    = blankToNull(dni);
        String legajoNorm = blankToNull(legajo);

        if (dniNorm == null) throw new IllegalArgumentException("El DNI es obligatorio.");
        if (legajoNorm != null && docenteRepository.existeLegajoVigente(tenantId, legajoNorm)) {
            throw new IllegalArgumentException("Ya existe un docente con legajo '" + legajoNorm + "' en esta institución.");
        }

        // Si el DNI ya está en la institución no es necesariamente un error: puede ser alguien
        // que administra y ahora además va a dar clases, o alguien que se fue y vuelve. Lo que
        // no puede haber son dos vínculos abiertos para la misma persona.
        Persona persona = personaRepository.buscarPorDni(tenantId, dniNorm).orElse(null);
        if (persona != null) {
            if (docenteRepository.vinculoVigenteDe(tenantId, persona.getId()).isPresent()) {
                throw new IllegalArgumentException(
                    "Ya existe un docente activo con DNI '" + dniNorm + "' en esta institución.");
            }

            // Acá estaba el agujero: el alta daba por sentado que era un reingreso y le
            // reescribía la identidad a esa persona. Con un DNI mal tipeado —que es un error de
            // una tecla— eso le cambiaba el nombre a otra, y nadie se enteraba. Ahora se
            // pregunta, y hasta que alguien confirme no se toca nada.
            if (!confirmado) {
                String propuesto = apellido.trim() + ", " + nombre.trim();
                throw new ConfirmacionRequeridaException(
                    personaService.impactoDeAlta(persona, propuesto));
            }

            InstantaneaIdentidad antes = InstantaneaIdentidad.de(persona);
            persona.setNombre(nombre.trim());
            persona.setApellido(apellido.trim());
            persona.setEmail(blankToNull(email));
            persona.setTelefono(blankToNull(telefono));
            personaRepository.save(persona);
            // Un reingreso confirmado puede venir con datos distintos a los guardados: queda
            // registrado igual que cualquier otra edicion, con su propio origen.
            personaService.registrarCambios(persona, antes, usuarioActualId, "REINGRESO");
            log.info("Alta de docente sobre persona existente CONFIRMADA: persona_id={}, institucion_id={}",
                     persona.getId(), tenantId);
        } else {
            persona = Persona.builder()
                .dni(dniNorm)
                .nombre(nombre.trim())
                .apellido(apellido.trim())
                .email(blankToNull(email))
                .telefono(blankToNull(telefono))
                .build();
            persona.setInstitucionId(tenantId);
            persona = personaRepository.save(persona);
        }

        Docente d = Docente.builder()
            .persona(persona)
            .legajo(legajoNorm)
            // La fecha de alta es el momento en que se carga, no un dato a tipear: quien
            // esta cargando al docente esta aca ahora, y pedirsela solo habilita el error.
            .fechaAlta(LocalDate.now())
            .activo(true)
            .build();
        d.setInstitucionId(tenantId);

        Docente saved = docenteRepository.save(d);
        log.info("Docente creado: id={}, persona_id={}, institucion_id={}",
                 saved.getId(), persona.getId(), tenantId);
        return saved;
    }

    @Transactional
    // Edita los datos del docente, cuidando que DNI y legajo no choquen con otro.
    public Docente actualizar(Long id, String dni, String legajo, String nombre, String apellido,
                              String email, String telefono, Long usuarioActualId) {
        return actualizar(id, dni, legajo, nombre, apellido, email, telefono, false, usuarioActualId);
    }

    @Transactional
    // Igual que el anterior, con la confirmacion ya dada.
    public Docente actualizar(Long id, String dni, String legajo, String nombre, String apellido,
                              String email, String telefono, boolean confirmado,
                              Long usuarioActualId) {

        Long tenantId = TenantContext.getRequired();
        Docente d = buscarPorId(id);
        Persona persona = d.getPersona();

        String dniNuevo    = blankToNull(dni);
        String legajoNuevo = blankToNull(legajo);

        if (dniNuevo == null) throw new IllegalArgumentException("El DNI es obligatorio.");
        if (personaRepository.existeDniEnOtra(tenantId, dniNuevo, persona.getId())) {
            throw new IllegalArgumentException("Ya existe otra persona con DNI '" + dniNuevo + "'.");
        }
        if (legajoNuevo != null && !legajoNuevo.equals(d.getLegajo())
                && docenteRepository.existeLegajoVigenteEnOtro(tenantId, legajoNuevo, id)) {
            throw new IllegalArgumentException("Ya existe otro docente con legajo '" + legajoNuevo + "'.");
        }

        // La confirmacion va DESPUES de validar, no antes: preguntar "seguro que querés cambiar
        // esto en todos sus roles" para despues rechazar el formulario por un DNI mal formado
        // seria hacer decidir sobre algo que no se iba a guardar igual.
        //
        // Si esta persona ademas tiene cuenta en el sistema, el cambio de nombre se ve tambien
        // ahi. Quien edita esta mirando la pantalla de docentes y no tiene por que saberlo.
        if (!confirmado && personaService.edicionRequiereConfirmacion(persona)) {
            String propuesto = apellido.trim() + ", " + nombre.trim();
            throw new ConfirmacionRequeridaException(
                personaService.impactoDeEdicion(persona, propuesto));
        }

        // La identidad se edita del lado de la persona. Si esa persona además tiene cuenta en
        // el sistema, el cambio de nombre se refleja también ahí, que es justamente el punto
        // de haberla separado: antes había que corregirla en dos lugares.
        // La foto del antes se toma aca, con la entidad todavia sin tocar: guardarse una
        // referencia a la persona no serviria, porque se edita en el lugar y devolveria el
        // estado nuevo.
        InstantaneaIdentidad antes = InstantaneaIdentidad.de(persona);

        persona.setDni(dniNuevo);
        persona.setNombre(nombre.trim());
        persona.setApellido(apellido.trim());
        persona.setEmail(blankToNull(email));
        persona.setTelefono(blankToNull(telefono));
        personaRepository.save(persona);
        personaService.registrarCambios(persona, antes, usuarioActualId, "DOCENTE");

        // Del vínculo solo se edita el legajo. La fecha de alta no se toca: es el registro de
        // cuando ingreso, no un campo mas del legajo.
        d.setLegajo(legajoNuevo);

        Docente saved = docenteRepository.save(d);
        log.info("Docente actualizado: id={}, persona_id={}", id, persona.getId());
        return saved;
    }

    /**
     * Por qué NO se puede dar de baja este docente, o null si se puede.
     *
     * <p>Existe para que la pantalla sepa la respuesta <b>antes</b> de que la persona haga
     * click. Sin esto, dar de baja a alguien que es titular de una materia abría el cuadro de
     * confirmación, pedía una fecha, y recién después del envío aparecía el rechazo: tres
     * pasos para llegar a un "no". Ahora el listado avisa de entrada y el cuadro queda para
     * las bajas que efectivamente se pueden hacer.
     *
     * <p>No reemplaza la validación de {@link #darDeBaja}: entre que se dibuja la pantalla y
     * se envía el formulario, otra persona puede haberle asignado una comisión.
     */
    @Transactional(readOnly = true)
    public String motivoQueImpideLaBaja(Long docenteId) {
        long materias = materiaRepository.countByDocenteTitularIdAndActivoTrue(docenteId);
        long comisiones = comisionRepository.countByDocenteAsignadoIdAndActivoTrue(docenteId);
        if (materias == 0 && comisiones == 0) {
            return null;
        }
        // Cada parte trae su propio verbo. Un prefijo comun --"es"-- daba "es asignado a
        // 2 comisiones" cuando no habia materias de por medio.
        StringBuilder sb = new StringBuilder("No se puede dar de baja: ");
        if (materias > 0) {
            sb.append("es titular de ").append(materias)
              .append(materias == 1 ? " materia" : " materias");
        }
        if (materias > 0 && comisiones > 0) {
            sb.append(" y");
        }
        if (comisiones > 0) {
            sb.append(materias > 0 ? " " : "").append("está asignado a ").append(comisiones)
              .append(comisiones == 1 ? " comisión" : " comisiones");
        }
        return sb.append(". Reasignalas primero.").toString();
    }

    // Baja logica con la fecha en que dejo de prestar servicios; se bloquea si sigue
    // siendo titular o esta asignado a comisiones activas.
    @Transactional
    public void darDeBaja(Long id, LocalDate fechaBaja, Long usuarioActualId) {
        Docente d = buscarPorId(id);
        if (Boolean.FALSE.equals(d.getActivo())) {
            throw new IllegalArgumentException("El docente ya está inactivo.");
        }
        validarFechaBaja(fechaBaja, d.getFechaAlta());

        long materiasComoTitular = materiaRepository.countByDocenteTitularIdAndActivoTrue(id);
        long comisionesAsignadas = comisionRepository.countByDocenteAsignadoIdAndActivoTrue(id);

        if (materiasComoTitular > 0 || comisionesAsignadas > 0) {
            throw new IllegalArgumentException(
                "No se puede dar de baja: el docente es titular de " + materiasComoTitular +
                " materia(s) y está asignado a " + comisionesAsignadas + " comisión(es) activas. " +
                "Reasignalas primero.");
        }

        d.setActivo(false);

        d.setFechaBaja(fechaBaja);
        d.setDadoDeBajaPor(usuarioActualId);
        docenteRepository.save(d);
        log.info("Docente dado de baja: id={}, fecha_baja={}, por usuario_id={}",
                 id, fechaBaja, usuarioActualId);
    }

    @Transactional
    // Reactiva un docente dado de baja.
    public void darDeAlta(Long id) {
        Docente d = buscarPorId(id);
        if (Boolean.TRUE.equals(d.getActivo())) {
            throw new IllegalArgumentException("El docente ya está activo.");
        }
        d.setActivo(true);
        // Se borra la fecha de baja: dejarla puesta describiria a un docente activo que
        // ademas figura como desvinculado, que es una contradiccion.
        d.setFechaBaja(null);
        d.setDadoDeBajaPor(null);
        docenteRepository.save(d);
        log.info("Docente reactivado: id={}", id);
    }

    // La baja se carga despues del hecho, asi que puede ser anterior a hoy pero nunca
    // futura ni anterior al ingreso del docente.
    private void validarFechaBaja(LocalDate fechaBaja, LocalDate fechaAlta) {
        if (fechaBaja == null) {
            throw new IllegalArgumentException("La fecha de baja es obligatoria.");
        }
        if (fechaBaja.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de baja no puede ser futura.");
        }
        if (fechaAlta != null && fechaBaja.isBefore(fechaAlta)) {
            throw new IllegalArgumentException(
                "La fecha de baja no puede ser anterior a la de alta (" +
                fechaAlta.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ").");
        }
    }

    // Normaliza campos opcionales: deja null si viene vacío o solo con espacios.
    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
