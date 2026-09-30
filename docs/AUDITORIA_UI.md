# Auditoría y ajuste de la interfaz

Revisión del 29 de septiembre de 2026. El alcance es la presentación Android y su
navegación. Los registros existentes y los contratos del backend se conservan.

## Problemas y cambios

| Problema observado | Cambio implementado |
| --- | --- |
| Dos botones para abrir historiales distintos | Un destino Historial, con los tipos Análisis y Consultas rápidas |
| Inicio mezclaba información, seis acciones y explicaciones de desarrollo | Red, seguridad y acceso a Internet; analizar como acción principal y guardar consulta como secundaria |
| Contadores, motivos y explicaciones aparecían siempre | Tarjetas compactas y secciones desplegables con los detalles completos |
| La preparación de captura exigía varios pasos de interfaz | Al abrir Análisis se prepara una sesión nueva si no hay una previa y se comprueba la conexión al servidor |
| La prueba controlada tenía la misma prioridad que el análisis real del celular | Captura completa como acción principal y prueba controlada en Opciones de prueba |
| Confirmaciones mezclaban textos, UUID y contadores | Confirmación breve y resultado estructurado; método y métricas en detalles |
| Colores y textos sin una jerarquía consistente | Tema verde y azul oscuro, tarjetas, etiquetas de riesgo y tipografía común |

Unificar el historial es una decisión de interfaz: una consulta rápida y una captura
de tráfico siguen siendo registros diferentes. No se mezclan sus métricas ni se
borran los datos anteriores. Ambos tipos permanecen accesibles en el mismo lugar.

## Información que permanece disponible

- Nivel de riesgo expresado con texto y color, incluyendo resultados indeterminados
  y registros históricos sin evaluación.
- Motivos, recomendaciones, calidad de muestra, seguridad y método utilizado.
- Contadores de tráfico y categorías por puerto, con ausencia de datos distinguida
  de un cero observado. El modo controlado mantiene sus contadores de prueba.
- Filtros, paginación, resumen, compartir informe, exportar CSV y eliminar con
  confirmación. Exportación y eliminación aparecen en Detalles y acciones.
- Alcance limitado al celular y evaluación orientativa, sin promesas de confirmar ataques.
- Permisos de Android, guardado pendiente y reintento.

## Protección del flujo

La navegación no detiene la VPN. No se puede preparar una nueva sesión durante la
captura o la autorización, ni reemplazar una captura pendiente de guardar. La
pantalla y el tipo de historial se conservan ante recreaciones de la actividad.
Android sigue solicitando los permisos de captura; abrir Análisis no inicia la VPN.

## Verificación

- Compilación de la app y de las pruebas.
- 23 pruebas unitarias de Android.
- 7 pruebas instrumentadas en un Pixel 8 temporal: contexto, navegación y
  recreación, detalles plegados, reintento sin descartar la sesión, exportación con
  confirmación, bloqueo de exportación insuficiente y acción accesible con texto al 150 %.
- Inspección de capturas en modo claro, oscuro y texto ampliado. Los registros
  presentes en las capturas de pruebas son fixtures de interfaz, no evidencia del estudio.
- Auditoría de diferencias, conservación de datos, compatibilidad del cliente con
  las respuestas existentes y ausencia de modificaciones del backend o de la tesis.

Se actualizan solamente las dependencias de pruebas AndroidX JUnit y Espresso.
Espresso 3.7 corrige el acceso por reflexión a InputManager que fallaba en el Android
del emulador. [Notas oficiales de AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test).

El runner se ejecutó directamente en Android mediante ADB; la tarea de Gradle para
pruebas conectadas requería un componente UTP que no estaba en la caché. Esto no
impide compilar ni ejecutar las pruebas instrumentadas instaladas en el emulador.

## Comprobación en el Honor 400 Lite

1. Sincronizar Gradle y ejecutar app para actualizar la instalación.
2. Revisar Inicio y desplegar Datos de conexión.
3. Abrir Historial y alternar Análisis y Consultas rápidas; los registros deben conservarse.
4. Abrir Detalles y acciones de un análisis, filtrar y probar compartir o exportar.
5. Realizar una captura, cambiar entre pantallas, volver y finalizarla. Debe guardarse.
6. Revisar modo oscuro y tamaño de texto del teléfono. La captura física y la
   navegación durante una VPN activa requieren esta comprobación manual.
