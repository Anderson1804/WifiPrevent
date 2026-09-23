---
name: android-app
description: Implementar o modificar la app Android de WifiPrevent, incluida la interfaz, captura VPN y comunicación con el backend.
---

# App Android de WifiPrevent

Trabaja en `app/`. Consulta `backend/README.md` cuando el cambio afecte la captura, el transporte local o el contrato de la API. Conserva la autorización explícita del usuario antes de iniciar la captura VPN y distingue los modos `controlled` y `full` según su comportamiento real.

Verifica el cambio con la compilación y las pruebas pertinentes de Gradle. Si una prueba requiere emulador, dispositivo o servicios locales no disponibles, informa esa limitación con precisión.

## Auditoría final obligatoria

Antes de terminar, revisa el diff completo de todos los archivos que modificaste durante la tarea, incluidos los cambios fuera de `app/` si los hubo. Comprueba regresiones, efectos secundarios, permisos, exposición de datos, coherencia entre app y API, y que las afirmaciones de la interfaz coincidan con las capacidades implementadas. Distingue tus cambios de los que ya estaban presentes al empezar. Corrige los problemas encontrados y repite las verificaciones afectadas. En la respuesta final, resume el resultado de la auditoría y cualquier riesgo o comprobación pendiente.
