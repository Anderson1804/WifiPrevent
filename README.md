# WiFiPrevent

WiFiPrevent es un prototipo académico de Android para observar metadatos de una
conexión Wi-Fi y generar una evaluación preventiva preliminar. La aplicación usa
Kotlin y Jetpack Compose; el servicio local usa Python, FastAPI y PostgreSQL.

## Inicio rápido en Windows

1. Abre la carpeta del proyecto en Android Studio.
2. Inicia PostgreSQL, FastAPI y el relé local desde una terminal en la raíz:

   ```powershell
   .\backend\.venv\Scripts\python.exe .\backend\local_services.py start
   ```

   Para comprobarlos usa `status`; para reiniciar backend y relé, `restart`; para
   detener backend y relé, `stop`. PostgreSQL permanece iniciado.

3. Ejecuta la configuración `app` desde Android Studio. El emulador usa la dirección
   especial `10.0.2.2` para comunicarse con la PC. El teléfono debe estar en la misma
   red privada, tener configurada la IP de la PC en `app/build.gradle.kts` y contar
   con las reglas de firewall descritas en [la guía del backend](backend/README.md).
4. En la app puedes guardar una consulta de conexión, revisar el historial y ejecutar
   una validación controlada. La captura completa es experimental: requiere Android
   13 o superior y autorización expresa para iniciar la VPN.

## Qué hace el prototipo

- Consulta datos que Android expone sobre la conexión Wi-Fi.
- Guarda sesiones de análisis en el backend y las separa por instalación.
- En captura completa, reenvía tráfico IPv4 por un relé SOCKS5 y conserva contadores
  agregados. Las categorías del relé se infieren por puerto de destino.
- Presenta evaluación basada en reglas, calidad de muestra e indicaciones preventivas.
- Permite consultar, filtrar, borrar y compartir resúmenes del historial.
- Incluye un flujo supervisado para entrenar y evaluar un modelo con capturas autorizadas.

La evaluación es orientativa: no inspecciona contenido y no confirma por sí sola una
amenaza. Hasta contar con datos autorizados y etiquetados, el backend mantiene sus
reglas versionadas. No hay un modelo entrenado incluido ni activo por defecto. La
captura completa actual solo enruta IPv4.

## Documentación

- [Guía técnica, arquitectura y explicación de Kotlin/Python](docs/GUIA_TECNICA_DEL_PROYECTO.md)
- [Configuración local, PostgreSQL, teléfono, API y pruebas del backend](backend/README.md)
- [Uso y licencia de hev-socks5-tunnel](docs/third-party/hev-socks5-tunnel.md)

El backend local está preparado para desarrollo y pruebas en una red privada. Antes
de usarlo en una red pública o desplegarlo se requieren HTTPS, autenticación adecuada,
revisión de firewall y una política de conservación de registros.
