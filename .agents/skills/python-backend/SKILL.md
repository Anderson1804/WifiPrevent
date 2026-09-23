---
name: python-backend
description: Implementar o modificar el backend Python de WifiPrevent, incluida la API, evaluación de riesgo, persistencia y relé local.
---

# Backend de WifiPrevent

Trabaja en `backend/`. Consulta `backend/README.md` para los contratos y el entorno local. Si cambias respuestas, solicitudes o persistencia, revisa los consumidores de la app Android; si cambias el esquema de base de datos, incluye la migración Alembic correspondiente. Mantén la separación entre señales observadas e inferencias de riesgo: las señales de tráfico por sí solas no confirman una amenaza. No muestres credenciales, `DATABASE_URL` ni contenido de comunicaciones en registros o respuestas.

Ejecuta las pruebas pertinentes de `backend/tests/` con el entorno de pruebas del proyecto. No uses la base de datos de la aplicación para pruebas.

## Auditoría final obligatoria

Antes de terminar, revisa el diff completo de todos los archivos que modificaste durante la tarea, incluidos los cambios fuera de `backend/` si los hubo. Comprueba regresiones, efectos secundarios, compatibilidad de la API con Android, integridad de datos, migraciones, privacidad y exactitud de las conclusiones de riesgo. Distingue tus cambios de los que ya estaban presentes al empezar. Corrige los problemas encontrados y repite las verificaciones afectadas. En la respuesta final, resume el resultado de la auditoría y cualquier riesgo o comprobación pendiente.
