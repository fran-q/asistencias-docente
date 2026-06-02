# Manuales

Documentación de uso y técnica del sistema.

| Archivo | Audiencia |
|---|---|
| `manual-administrador.md` | Personal administrativo de la institución (roles INSTITUCION y ADMIN). Cómo usar el sistema día a día. |
| `manual-tecnico.md` | Desarrolladores y administradores de sistemas. Instalación, configuración, backup, troubleshooting, despliegue. |

## Cómo exportar a PDF

Los manuales están en **Markdown** para que sean fáciles de mantener y
revisar en GitHub. Para la entrega académica conviene tener también PDF.

### Opción A — Pandoc (recomendado)

Instalar [Pandoc](https://pandoc.org/installing.html) y, opcionalmente,
una distribución LaTeX como [MiKTeX](https://miktex.org/) (Windows) para
PDFs con buena tipografía.

```bash
# PDF simple
pandoc manual-administrador.md -o manual-administrador.pdf

# PDF con estilo más prolijo (requiere LaTeX)
pandoc manual-administrador.md \
  -o manual-administrador.pdf \
  --pdf-engine=xelatex \
  -V geometry:margin=2.5cm \
  -V mainfont="DejaVu Sans" \
  -V fontsize=11pt \
  --toc \
  --toc-depth=2
```

Idem para `manual-tecnico.md`.

### Opción B — Visual Studio Code

1. Instalar extensión **"Markdown PDF"** (yzane).
2. Abrir el `.md` → botón derecho → "Markdown PDF: Export (pdf)".

### Opción C — Online

Pegar el contenido en [md-to-pdf.fly.dev](https://md-to-pdf.fly.dev/) o
similar. Cuidado con la información sensible si el manual tiene rutas
de claves o datos internos.

## Mantenimiento

- **Si cambia la UI** (botones, menús): actualizar **manual-administrador.md**.
- **Si cambia la configuración** (properties, dependencias, esquema BD):
  actualizar **manual-tecnico.md**.
- **Tras cada Sprint que agrega features**: revisar ambos.
