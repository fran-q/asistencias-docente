package edu.cent35.asistencias.controller;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import edu.cent35.asistencias.service.ConfirmacionRequeridaException;
import edu.cent35.asistencias.dto.*;
import edu.cent35.asistencias.model.*;

import edu.cent35.asistencias.seguridad.UsuarioAutenticado;
import edu.cent35.asistencias.service.UsuarioService;
import edu.cent35.asistencias.model.RolCodigo;
import edu.cent35.asistencias.model.Usuario;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Administración de las cuentas de la propia institución (RF-06), reservada al rol
 * INSTITUCION. Permite crear admins, editarlos, activarlos o desactivarlos y resetearles la
 * contraseña, siempre dentro de la institución del usuario logueado.
 */
@Controller
@RequestMapping("/usuarios")
@PreAuthorize("hasRole('INSTITUCION')")
@RequiredArgsConstructor
@Slf4j
public class UsuarioController {

    private final UsuarioService usuarioService;

    // ============================================================
    //  Listado
    // ============================================================
    @GetMapping
    public String listar(Model model) {
        List<UsuarioListItemDto> items = usuarioService.listarMiInstitucion()
            .stream()
            .map(UsuarioListItemDto::from)
            .toList();
        model.addAttribute("usuarios", items);
        return "usuario/list";
    }

    // ============================================================
    //  Alta
    // ============================================================
    @GetMapping("/nuevo")
    public String formNuevo(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UsuarioCreateFormDto());
        }
        return "usuario/form-nuevo";
    }

    /**
     * Da de alta una cuenta de administrador. La contraseña se hashea antes de guardarse.
     *
     * <p>No recibe el rol: el servicio siempre crea ADMIN. La cuenta de institución existe una
     * sola vez y nace en el alta pública del establecimiento, así que acá no hay nada que elegir.
     */
    @PostMapping("/nuevo")
    public String crear(@Valid @org.springframework.web.bind.annotation.ModelAttribute("form") UsuarioCreateFormDto form,
                        BindingResult binding,
                        Model model,
                        RedirectAttributes redirect) {

        if (!form.coincide()) {
            binding.rejectValue("confirmacion", "error.match", "Las contraseñas no coinciden");
        }
        if (binding.hasErrors()) {
            return "usuario/form-nuevo";
        }

        try {
            Usuario creado = usuarioService.crear(
                form.getUsername(),
                form.getEmail(),
                form.getPassword(),
                form.getNombre(),
                form.getApellido()
            );
            redirect.addFlashAttribute("flashMensaje",
                "Usuario '" + creado.getUsername() + "' creado correctamente.");
            return "redirect:/usuarios";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            return "usuario/form-nuevo";
        }
    }

    // ============================================================
    //  Edicion
    // ============================================================
    @GetMapping("/{id}/editar")
    public String formEditar(@PathVariable Long id, Model model) {
        Usuario u = usuarioService.buscarPorId(id);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", UsuarioEditFormDto.from(u));
        }
        model.addAttribute("usuario", u);
        // La pantalla cambia segun el rol: una institucion lleva un solo campo de nombre y no
        // ofrece la baja. Se resuelve aca y no en la plantilla porque Thymeleaf no puede leer
        // constantes de una clase Java.
        model.addAttribute("esInstitucion",
            RolCodigo.INSTITUCION.name().equals(u.getRol().getCodigo()));
        return "usuario/form-editar";
    }

    /**
     * Edita los datos de la cuenta. No toca el username, la contraseña ni el rol.
     *
     * <p>El rol quedó fuera del formulario y fuera del servicio: una cuenta no cambia de rol
     * nunca. Si alguien tiene que pasar a otro rol, se le da de baja la cuenta y se le crea
     * otra, para que el historial quede partido donde efectivamente cambió quién era.
     */
    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Long id,
                             @Valid @org.springframework.web.bind.annotation.ModelAttribute("form") UsuarioEditFormDto form,
                             BindingResult binding,
                             Model model,
                             @AuthenticationPrincipal UsuarioAutenticado actual,
                             RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            reponerContexto(id, model);
            return "usuario/form-editar";
        }

        try {
            usuarioService.actualizar(
                id,
                form.getNombre(),
                form.getApellido(),
                form.getEmail(),
                form.getActivo(),
                actual.getUsuarioId(),
                form.isConfirmado()
            );
            redirect.addFlashAttribute("flashMensaje", "Usuario actualizado correctamente.");
            return "redirect:/usuarios";
        } catch (ConfirmacionRequeridaException ex) {
            // Esta cuenta pertenece a alguien que ademas da clases: el cambio de nombre se ve en
            // la ficha del docente y en los listados de asistencia. Se avisa antes de escribir.
            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("nombre", form.getNombre());
            campos.put("apellido", form.getApellido());
            campos.put("email", form.getEmail());
            campos.put("activo", String.valueOf(form.getActivo()));
            campos.values().removeIf(Objects::isNull);

            model.addAttribute("impacto", ex.getImpacto());
            model.addAttribute("camposOcultos", campos);
            model.addAttribute("accion", "/usuarios/" + id + "/editar");
            model.addAttribute("volverA", "/usuarios/" + id + "/editar");
            return "identidad/confirmar";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            reponerContexto(id, model);
            return "usuario/form-editar";
        }
    }

    // Repone lo que la pantalla de edicion necesita cuando el formulario vuelve con errores.
    // Si falta "esInstitucion", la plantilla lo evalua como falso y muestra la baja de una
    // cuenta que no se puede dar de baja.
    private void reponerContexto(Long id, Model model) {
        Usuario u = usuarioService.buscarPorId(id);
        model.addAttribute("usuario", u);
        model.addAttribute("esInstitucion",
            RolCodigo.INSTITUCION.name().equals(u.getRol().getCodigo()));
    }

    // ============================================================
    //  Por qué no hay pantalla para cambiarle la contraseña a otro
    // ============================================================
    //  Existía /usuarios/{id}/password, donde la institución le fijaba una contraseña
    //  nueva a cualquier cuenta. Se eliminó al exigir que TODO cambio de contraseña
    //  acredite el control del correo con un código de un solo uso: si el código va al
    //  dueño de la cuenta, que un tercero elija la contraseña deja de tener sentido.
    //
    //  Quien se quedó afuera usa "¿Olvidaste tu contraseña?" en el login y recibe su
    //  propio código. De paso desaparece que un administrador conozca la clave con la
    //  que otra persona entra, que es justamente lo que un hash existe para evitar.

    // ============================================================
    //  Manejador de "no encontrado" - camufla cross-tenant
    // ============================================================
    @org.springframework.web.bind.annotation.ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "El usuario solicitado no existe.");
        return "redirect:/usuarios";
    }
}
