# Datos locales para el modelo supervisado

`templates/risk_observations_template.csv` define el contrato de entrenamiento.
Cada fila corresponde a una captura completa evaluada en un escenario autorizado.
El campo `reference_risk_level` debe fijarse con los criterios de referencia antes de
consultar el resultado del aplicativo. No copies el `risk_level` generado por las
reglas del backend: eso enseñaría al modelo a imitar esas mismas reglas.

## Recoger una fila desde el aplicativo

1. Realiza una captura completa autorizada de al menos 30 segundos y 100 paquetes.
   Debe disponer de observaciones del relé y estar guardada en el backend.
2. En el historial pulsa `Exportar métricas CSV`, confirma y elige una ubicación.
   Para conservarla solo en el dispositivo, elige una carpeta local en el selector.
   Puedes cancelar; eso no elimina ni modifica la sesión.
3. El archivo contiene un encabezado y una fila de métricas. Los primeros tres
   campos están vacíos. No incluye SSID, fechas ni identificadores de instalación o
   sesión, y tampoco incluye el resultado calculado por el aplicativo.
4. Completa esos campos con el código de escenario, la partición y el nivel fijados
   en el protocolo de referencia, antes de consultar la predicción del sistema.
   Un caso de navegación ordinaria no permite asignar un nivel de referencia por sí solo.
5. Reúne las filas en `observations/risk_observations.csv`, conservando exactamente
   un encabezado. Usa cada captura una sola vez; exportar nuevamente una sesión
   produce los mismos valores. Conserva la correspondencia entre capturas y
   escenarios en tu registro de investigación separado del conjunto de entrenamiento.

No alteres ni inventes las métricas para conseguir clases low, medium y high. Las
sesiones breves, controladas o sin métricas del relé no se exportan para entrenamiento.
Los ceros en las métricas del parser no sustituyen las observaciones del relé: son
grupos de campos distintos y se conservan con sus nombres originales.

## Etiquetas y evaluación

Usa códigos como `benign_01` o `scenario_12` en `scenario_id`; no incluyas nombres,
SSID, direcciones IP, dominios, credenciales, contenido ni identificadores de usuario.
Los campos `evaluation_split` aceptan `training` o `test`. Un escenario completo debe
permanecer en una sola partición para evitar que repeticiones de la misma prueba se
filtren de entrenamiento a test. Se requieren al menos cinco escenarios distintos de
cada clase en training, dos en test y las tres clases en ambas particiones.

La unidad que el prototipo puede clasificar hoy es la sesión completa y sus contadores
agregados. Esto no equivale a identificar cada amenaza o evento individual; para medir
el porcentaje de amenazas detectadas del instrumento se necesita un detector y un
registro por evento que todavía no existen en el aplicativo.

El archivo con las observaciones reales debe guardarse como
`data/observations/risk_observations.csv`; esta carpeta y los artefactos entrenados se
excluyen de Git. Solo se deben incorporar observaciones obtenidas en pruebas
controladas o autorizadas, después de la aprobación ética correspondiente. Los
registros incluidos en documentos que solo ilustran el cálculo del instrumento no
son un conjunto de entrenamiento.

El entrenamiento compara regresión logística, árbol de decisión y bosque aleatorio
con validación cruzada agrupada por escenario. Selecciona por F1 macro y reporta
exactitud balanceada, métricas por clase y matriz de confusión. Después mide una
partición test separada. Estas métricas describen ese conjunto; no prueban por sí
solas eficacia en MegaPlaza ni sustituyen el pretest/postest del estudio.
