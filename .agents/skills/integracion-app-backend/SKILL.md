---
name: integracion-app-backend
description: Implementar funciones de WifiPrevent que requieren cambios coordinados en la app Android y el backend Python.
---

# Integración de app y backend

Usa esta skill cuando una petición afecte tanto a `app/` como a `backend/`. Mantén una sola tarea funcional de extremo a extremo; organiza el trabajo por responsabilidad técnica sin exigir que el usuario divida su pedido.

Revisa `backend/README.md`, los esquemas y rutas de `backend/app/` y el cliente y repositorio de `app/src/main/java/com/anderson/wifiprevent/data/` que correspondan a la función. Define el contrato compartido a partir del comportamiento solicitado: campos, tipos, valores opcionales, errores, autenticación y compatibilidad con datos existentes. Implementa ambos lados y actualiza la documentación del contrato si cambia.

Comprueba el flujo completo desde la acción de la app hasta la respuesta y la presentación al usuario. Ejecuta las pruebas pertinentes del backend y de Android; usa emulador o dispositivo cuando sea necesario y esté disponible. Si una parte no puede verificarse en este entorno, explica qué comprobación falta. Respeta la autorización de captura VPN y evita presentar indicadores técnicos como pruebas concluyentes de una amenaza.

## Auditoría final obligatoria

Antes de terminar, revisa el diff completo de todos los archivos que modificaste en ambas partes y cualquier archivo adicional. Distingue los cambios propios de los que ya estaban presentes al empezar. Busca diferencias entre el contrato servido y el consumido, regresiones, errores de migración o datos históricos, exposición de información y textos de interfaz que prometan más de lo implementado. Corrige los hallazgos y repite las verificaciones afectadas. Resume en la respuesta final qué se comprobó, el resultado de la auditoría y cualquier limitación pendiente.

Esta skill no solicita commits ni push por sí misma. Cuando el usuario los pida, sigue también `commits-por-cambio` y separa los commits por cambio funcional.
