package edu.cent35.asistencias.usuario.web;

import edu.cent35.asistencias.shared.security.CustomUserDetails;
import edu.cent35.asistencias.usuario.application.UsuarioService;
import edu.cent35.asistencias.usuario.domain.RolCodigo;
import edu.cent35.asistencias.usuario.domain.Usuario;
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
 * CRUD de usuarios (admins) de la institucion del SUPERADMIN logueado.
 * Cubre RF-06.
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
        model.addAttribute("roles", RolCodigo.values());
        return "usuario/form-nuevo";
    }

    @PostMapping("/nuevo")
    public String crear(@Valid @org.springframework.web.bind.annotation.ModelAttribute("form") UsuarioCreateFormDto form,
                        BindingResult binding,
                        Model model,
                        RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute("roles", RolCodigo.values());
            return "usuario/form-nuevo";
        }

        try {
            Usuario creado = usuarioService.crear(
                form.getUsername(),
                form.getEmail(),
                form.getPassword(),
                form.getNombre(),
                form.getApellido(),
                form.getRol()
            );
            redirect.addFlashAttribute("flashMensaje",
                "Usuario '" + creado.getUsername() + "' creado correctamente.");
            return "redirect:/usuarios";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("roles", RolCodigo.values());
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
        model.addAttribute("roles", RolCodigo.values());
        return "usuario/form-editar";
    }

    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Long id,
                             @Valid @org.springframework.web.bind.annotation.ModelAttribute("form") UsuarioEditFormDto form,
                             BindingResult binding,
                             Model model,
                             @AuthenticationPrincipal CustomUserDetails actual,
                             RedirectAttributes redirect) {

        if (binding.hasErrors()) {
            model.addAttribute("usuario", usuarioService.buscarPorId(id));
            model.addAttribute("roles", RolCodigo.values());
            return "usuario/form-editar";
        }

        try {
            usuarioService.actualizar(
                id,
                form.getNombre(),
                form.getApellido(),
                form.getEmail(),
                form.getRol(),
                form.getActivo(),
                actual.getUsuarioId()
            );
            redirect.addFlashAttribute("flashMensaje", "Usuario actualizado correctamente.");
            return "redirect:/usuarios";
        } catch (IllegalArgumentException ex) {
            binding.reject("error.global", ex.getMessage());
            model.addAttribute("usuario", usuarioService.buscarPorId(id));
            model.addAttribute("roles", RolCodigo.values());
            return "usuario/form-editar";
        }
    }

    // ============================================================
    //  Reset de password (form separado)
    // ============================================================
    @GetMapping("/{id}/password")
    public String formPassword(@PathVariable Long id, Model model) {
        Usuario u = usuarioService.buscarPorId(id);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PasswordResetDto());
        }
        model.addAttribute("usuario", u);
        return "usuario/form-password";
    }

    @PostMapping("/{id}/password")
    public String resetearPassword(@PathVariable Long id,
                                   @Valid @org.springframework.web.bind.annotation.ModelAttribute("form") PasswordResetDto form,
                                   BindingResult binding,
                                   Model model,
                                   RedirectAttributes redirect) {

        if (binding.hasErrors() || !form.coincide()) {
            if (!form.coincide()) {
                binding.rejectValue("confirmacion", "error.match", "Las contraseñas no coinciden");
            }
            model.addAttribute("usuario", usuarioService.buscarPorId(id));
            return "usuario/form-password";
        }

        usuarioService.resetearPassword(id, form.getNuevaPassword());
        redirect.addFlashAttribute("flashMensaje", "Contraseña reseteada correctamente.");
        return "redirect:/usuarios";
    }

    // ============================================================
    //  Manejador de "no encontrado" - camufla cross-tenant
    // ============================================================
    @org.springframework.web.bind.annotation.ExceptionHandler(EntityNotFoundException.class)
    public String handleNotFound(EntityNotFoundException ex, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "El usuario solicitado no existe.");
        return "redirect:/usuarios";
    }
}
