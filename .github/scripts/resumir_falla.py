"""Cuenta, en la página de la corrida, por qué falló el build del CI.

El log de un job de GitHub Actions solo se puede leer con la sesión iniciada en GitHub. Las
anotaciones y el resumen de la corrida, en cambio, los ve cualquiera que abra la página. Este
script corre solo cuando el build falla y deja ahí lo necesario para entender la falla sin
abrir el log:

- los errores de compilación, de build.log, la copia de la salida que guarda el paso del build;
- las violaciones de Checkstyle, de sus reportes en build/reports/checkstyle;
- los tests que fallaron con su mensaje, de los XML que deja Gradle en build/test-results;
- y, si no fue nada de eso, lo que Gradle dice en "What went wrong".

Todo va al resumen de la corrida. Las primeras fallas van además como anotación de error, que
GitHub muestra arriba de todo y, si sabe el archivo y la línea, también sobre el código.

Existe porque el CI estuvo en rojo de mayo a octubre de 2026 sin que se viera por qué: la
causa --OpenCV pide GTK 2 en Linux-- se dedujo sin el log. Nunca falla: lo que no encuentra
lo dice y sigue.
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

# GitHub muestra hasta diez anotaciones de error por paso; las demás quedan en el resumen.
MAX_ANOTACIONES = 10
MAX_POR_SECCION = 30
MAX_MENSAJE = 800
LINEAS_DEL_FINAL = 40


def recortar(texto, limite=MAX_MENSAJE):
    texto = texto.replace("\r\n", "\n").strip()
    return texto if len(texto) <= limite else texto[:limite] + " [...]"


def cantidad(n, singular, plural):
    return f"{n} {singular if n == 1 else plural}"


def relativa(ruta):
    """Los reportes traen rutas absolutas de la máquina del CI; GitHub las quiere relativas a
    la raíz del repositorio, que es donde corre este script."""
    try:
        return os.path.relpath(ruta).replace(os.sep, "/")
    except ValueError:
        return ruta


def bloque(texto, sangria=""):
    # Dentro de un ítem de lista, cada renglón del bloque lleva la sangría del ítem: uno sin
    # ella cierra la lista. Y un mensaje con ``` adentro cerraría el bloque antes de tiempo.
    renglones = ["```"] + texto.replace("```", "'''").split("\n") + ["```"]
    return [sangria + renglon for renglon in renglones]


def leer_log():
    try:
        with open("build.log", encoding="utf-8", errors="replace") as f:
            return f.read()
    except OSError:
        return None


def errores_de_compilacion(log):
    return [
        {"archivo": relativa(m.group(1)), "linea": int(m.group(2)), "mensaje": m.group(3)}
        for m in re.finditer(r"^(\S.*?\.java):(\d+): error: (.+)$", log, re.MULTILINE)
    ]


def violaciones_de_checkstyle():
    violaciones = []
    for ruta in sorted(glob.glob("build/reports/checkstyle/*.xml")):
        try:
            reporte = ET.parse(ruta).getroot()
        except ET.ParseError:
            continue
        for archivo in reporte.iter("file"):
            for error in archivo.iter("error"):
                violaciones.append({
                    "archivo": relativa(archivo.get("name") or ""),
                    "linea": int(error.get("line") or 0) or None,
                    "mensaje": error.get("message") or "",
                    # El id de la regla si lo tiene (inyeccionPorCampo), si no la clase.
                    "regla": (error.get("source") or "").rsplit(".", 1)[-1],
                })
    return violaciones


def archivo_del_test(clase):
    ruta = "src/test/java/" + clase.split("$", 1)[0].replace(".", "/") + ".java"
    return ruta if os.path.exists(ruta) else None


def linea_en_la_traza(clase, traza):
    """La línea del test donde saltó la falla: el primer renglón de la traza que cae en su
    archivo."""
    nombre = clase.split("$", 1)[0].rsplit(".", 1)[-1]
    m = re.search(r"\(" + re.escape(nombre) + r"\.java:(\d+)\)", traza)
    return int(m.group(1)) if m else None


def tests_que_fallaron():
    fallas = []
    for ruta in sorted(glob.glob("build/test-results/*/TEST-*.xml")):
        try:
            suite = ET.parse(ruta).getroot()
        except ET.ParseError:
            continue
        for caso in suite.iter("testcase"):
            falla = caso.find("failure")
            if falla is None:
                falla = caso.find("error")
            if falla is None:
                continue
            clase = caso.get("classname") or ""
            traza = falla.text or ""
            tipo = falla.get("type") or ""
            mensaje = falla.get("message") or traza.strip().split("\n", 1)[0]
            # Gradle guarda en message el toString() de la excepción, que ya empieza con el
            # tipo; si no lo trae, se le agrega, porque el tipo solo ya dice mucho.
            if tipo and not mensaje.startswith(tipo):
                mensaje = f"{tipo}: {mensaje}"
            fallas.append({
                "titulo": clase.rsplit(".", 1)[-1] + " > " + (caso.get("name") or ""),
                "mensaje": mensaje,
                "archivo": archivo_del_test(clase),
                "linea": linea_en_la_traza(clase, traza),
            })
    return fallas


def que_dijo_gradle(log):
    """Las secciones "What went wrong": con --continue hay una por tarea que falló."""
    patron = r"^\* What went wrong:\n(.*?)(?=^\* Try:|^\* Exception is:|^\* Get more help|\Z)"
    return [m.group(1).strip() for m in re.finditer(patron, log, re.MULTILINE | re.DOTALL)]


def escapar_dato(texto):
    return texto.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escapar_propiedad(texto):
    return escapar_dato(texto).replace(":", "%3A").replace(",", "%2C")


def anotar(archivo, linea, titulo, mensaje):
    propiedades = []
    if archivo:
        propiedades.append("file=" + escapar_propiedad(archivo))
        if linea:
            propiedades.append("line=" + str(linea))
    propiedades.append("title=" + escapar_propiedad(titulo))
    print("::error " + ",".join(propiedades) + "::" + escapar_dato(recortar(mensaje)))


def main():
    log = leer_log()
    compilacion = errores_de_compilacion(log) if log else []
    estilo = violaciones_de_checkstyle()
    tests = tests_que_fallaron()
    gradle = que_dijo_gradle(log) if log else []

    md = ["## Por qué falló el build", ""]
    anotaciones = []

    if log is None:
        md += ["No hay `build.log`: el build no llegó a correr porque falló un paso anterior. "
               "Cuál, lo dice la lista de pasos de la corrida.", ""]

    if compilacion:
        md += [f"### No compila: {cantidad(len(compilacion), 'error', 'errores')}", ""]
        for e in compilacion[:MAX_POR_SECCION]:
            md.append(f"- `{e['archivo']}:{e['linea']}`: {e['mensaje']}")
            anotaciones.append((e["archivo"], e["linea"], "No compila", e["mensaje"]))
        md.append("")

    if estilo:
        md += [f"### Checkstyle: {cantidad(len(estilo), 'violación', 'violaciones')}", ""]
        for v in estilo[:MAX_POR_SECCION]:
            md.append(f"- `{v['archivo']}:{v['linea'] or '-'}` ({v['regla']}): {v['mensaje']}")
            anotaciones.append((v["archivo"], v["linea"], "Checkstyle: " + v["regla"], v["mensaje"]))
        md.append("")

    if tests:
        md += [f"### Tests que fallaron: {len(tests)}", ""]
        for t in tests[:MAX_POR_SECCION]:
            md.append(f"- **{t['titulo']}**")
            md += bloque(recortar(t["mensaje"]), sangria="  ")
            anotaciones.append((t["archivo"], t["linea"], t["titulo"], t["mensaje"]))
        md.append("")

    if gradle and not (compilacion or estilo or tests):
        md += ["### Lo que dice Gradle", ""]
        for seccion in gradle:
            md += bloque(recortar(seccion, 3000))
            anotaciones.append((None, None, "Gradle", seccion))
        md.append("")

    if log and not (compilacion or estilo or tests or gradle):
        md += [f"### Las últimas {LINEAS_DEL_FINAL} líneas de la salida", ""]
        md += bloque("\n".join(log.rstrip().splitlines()[-LINEAS_DEL_FINAL:]))
        md.append("")

    for archivo, linea, titulo, mensaje in anotaciones[:MAX_ANOTACIONES]:
        anotar(archivo, linea, titulo, mensaje)
    if len(anotaciones) > MAX_ANOTACIONES:
        md.append(f"Las anotaciones muestran las primeras {MAX_ANOTACIONES} de "
                  f"{len(anotaciones)}; acá están todas, hasta {MAX_POR_SECCION} por sección.")

    destino = os.environ.get("GITHUB_STEP_SUMMARY")
    texto = "\n".join(md) + "\n"
    if destino:
        with open(destino, "a", encoding="utf-8") as f:
            f.write(texto)
    else:
        print(texto)


if __name__ == "__main__":
    # Las anotaciones salen por la salida estándar, y los mensajes traen tildes.
    sys.stdout.reconfigure(encoding="utf-8")
    # Cualquier error, no uno en particular: un resumen roto no puede sumar una segunda falla
    # encima de la que vino a contar.
    try:
        main()
    except Exception as e:
        print("::warning title=Resumen de la falla::"
              + escapar_dato(f"No se pudo armar el resumen: {e!r}"))
