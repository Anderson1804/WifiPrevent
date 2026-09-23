---
name: commits-por-cambio
description: Revisar cambios de WifiPrevent y crear y enviar commits separados por cambio funcional, con mensajes breves que expliquen cada uno.
---

# Commits y push por cambio en WifiPrevent

Usa esta skill cuando el usuario pida preparar, crear o enviar commits de los cambios del proyecto. Cuando el usuario pida ejecutar el flujo completo, crea los commits y haz el push ordinario a la rama remota correspondiente. Si pide solo revisar o preparar, limita las acciones a ese alcance.

1. Comprueba la rama activa, el remoto y el estado del repositorio. Identifica los cambios preexistentes y el alcance pedido. No asumas que todos los archivos modificados pertenecen al mismo cambio.
2. Lee el diff de cada archivo, incluidos archivos nuevos y cambios ya preparados. Cuando un archivo contenga varios cambios independientes, examina sus fragmentos por separado. Agrupa por propósito funcional y dependencias reales; conserva juntos el código y las pruebas de un mismo cambio. No mezcles cambios independientes para reducir el número de commits.
3. Antes de cada commit, prepara únicamente los archivos o fragmentos de ese grupo. Revisa el diff preparado para confirmar que incluye exactamente ese cambio y que no incorpora secretos, datos locales ni archivos ajenos. Ejecuta las verificaciones pertinentes cuando el cambio lo requiera.
4. Crea un commit por grupo con un título corto que describa el resultado y un cuerpo breve que resuma qué se hizo y por qué. El mensaje debe reflejar el contenido real del commit.
5. Envía ese commit a la rama remota correspondiente y confirma que el push terminó correctamente antes de preparar el siguiente grupo. Repite el proceso para cada cambio restante.

## Auditoría final

Revisa todos los commits creados y el estado final del repositorio. Confirma que cada commit contiene solo su cambio, que cada push llegó al remoto y que no quedaron cambios del alcance pedido sin tratar. Distingue los cambios que ya existían de los hechos durante esta tarea. Comunica los commits enviados con su resumen y señala cualquier archivo que quedó pendiente.

Si un cambio no se puede atribuir con seguridad, una verificación falla o el remoto rechaza el push, detén ese grupo y explica el impedimento. No fuerces el push, no reescribas historia publicada y no incluyas cambios ajenos para resolverlo.

