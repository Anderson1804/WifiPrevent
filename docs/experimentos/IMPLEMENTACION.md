# Implementación experimental y comprobación en el celular

Actualización del 30 de septiembre de 2026. La app y el backend incluyen mediciones
temporales, patrones observados, exportación, laboratorio acotado, entrenamiento y
cálculo de indicadores. Estos módulos no sustituyen las ejecuciones del estudio.
No hay un modelo de eventos entrenado con datos de campo ni resultados del anexo 2.

## Estado de los puntos

| Punto | Implementación | Comprobación que falta |
| --- | --- | --- |
| Escenarios y referencias | Catálogo, controles parecidos y preparación de borradores desde evidencia del receptor | Elegir casos distinguibles y aprobar la rúbrica independiente |
| Mediciones temporales | Conexiones y datagramas con tiempos relativos; intentos TCP fallidos; grupos anónimos | Validar las mediciones físicas y el ruido de fondo |
| Detección y riesgo separados | Patrones por reglas y predicciones ML por ventanas; historial y recibos conservan los resultados | Medir errores y justificar si un patrón representa una amenaza del estudio |
| ML | Dos clasificadores, evento y riesgo; árbol y Random Forest frente a dummy; validación por grupo | Reunir y revisar el dataset, entrenar y evaluar clases reales |
| Suricata | Importador EVE con equivalencias explícitas y regla de laboratorio como plantilla | Instalar o disponer del motor y ejecutar el pretest comparable |
| Anexo 2 | Emparejamiento, falsas alarmas, omisiones, duplicados, porcentajes y comparación de pares | Ejecutar el protocolo con referencias aprobadas |

## Actualizar el prototipo

Desde la raíz de WifiPrevent, con los servicios de PostgreSQL disponibles:

```powershell
.\backend\.venv\Scripts\python.exe -m alembic -c .\backend\alembic.ini upgrade head
.\backend\.venv\Scripts\python.exe .\backend\local_services.py restart
```

Después sincronizar Gradle y ejecutar `app` desde Android Studio. Las revisiones
`b830e14206aa` y `c094bba76add` agregan campos JSON. Los registros anteriores
conservan sus datos y muestran ausencia de mediciones temporales. No borrar ni
reinstalar la app: su clave de instalación da acceso al historial existente.

## Primera comprobación sin generador

1. Iniciar una captura completa de al menos 60 segundos en la red privada propia.
2. Navegar y finalizar. Abrir Detalles del análisis o Detalles y acciones del historial.
3. Revisar Patrones de conexión y el número de observaciones. Puede no aparecer
   ningún patrón; eso no significa que toda la red sea segura.
4. Exportar observaciones temporales CSV y el registro experimental JSON. El CSV
   contiene ventanas de 30 segundos, incluidas las vacías; sus etiquetas quedan
   sin completar. El JSON conserva observaciones y predicciones, sin SSID ni
   identificador de instalación o sesión.
5. Comprobar que una sesión anterior sigue accesible. Si hay guardado pendiente,
   reintentarlo antes de iniciar otra. La serie se conserva en el almacenamiento
   privado de Android para el reintento.

## Qué mide y qué significa

El relé conserva como máximo 2048 observaciones y una hora de tiempo por captura.
Si se excede un límite, lo declara incompleto y se abstiene de evaluar patrones o
exportar el CSV temporal. El JSON sí puede exportarse para diagnosticar el límite.
También se limita a 4096 huellas de destinos. No se guardan IP, dominio, puerto
exacto, contenido ni contraseñas en la serie: el puerto se sustituye por categoría.

El grupo de destino es un número local de la captura. Los tiempos empiezan cuando
se prepara el relé, antes de que Android autorice y arranque la VPN. No equiparar
esos tiempos directamente con el cronómetro del TUN; el JSON contiene la fecha UTC
del origen para correlacionar con el receptor.

Las reglas de piloto observan 20 conexiones exitosas al mismo grupo en 10 segundos,
10 fallos al mismo grupo en 10 segundos y cadenas de al menos seis conexiones
separadas entre cuatro y seis segundos. Son parámetros exploratorios versionados,
no umbrales de amenazas validados. La subida legítima conserva su control porque
la advertencia de volumen de salida puede activarse sin existir una amenaza.

Sin modelo de eventos aprobado, el riesgo de cada patrón es `unknown`. La
evaluación general de la conexión sigue teniendo su método independiente. Cuando
un modelo de eventos aprobado existe, clasifica ventanas completas de 30 segundos,
incluidas las normales; esas predicciones se conservan al guardar. No recalcular
el resultado histórico cuando cambia el modelo.

## Receptor del laboratorio

Si el receptor se ejecuta en una VM Kali, seguir la
[preparación del receptor autónomo en Kali](KALI_RECEPTOR.md). Solo se copia un
archivo Python; no hace falta instalar el backend completo allí.

El celular y el relé deben alcanzar un receptor propio en una IPv4 privada distinta
de la IP del relé excluida del túnel. Una VM con red accesible o una segunda interfaz
de red ya configurada son opciones. No inventar ni añadir una IP que pudiera estar
ocupada. Verificar el recorrido de la captura antes de medir.

En el equipo receptor con Python y este proyecto, reemplazar la IP del ejemplo
por una dirección que ese equipo tenga asignada:

```powershell
.\backend\.venv\Scripts\python.exe .\backend\experiment.py receiver --bind 192.168.18.80 --allowed-client 192.168.18.20 --log .\backend\data\experiments\receptor-piloto-01.jsonl
```

`--allowed-client` es la IP del relé, que establece las conexiones al receptor;
no necesariamente la IP del celular. La regla de firewall del receptor, si se
necesita, debe limitar 8765/TCP a ese origen y a la red privada. El receptor escucha
una IP concreta, no toda la red, y registra códigos, tiempos y bytes sintéticos.
Usar un archivo de log nuevo por lote. Su límite es 1000 solicitudes y 3 MiB por
solicitud. Ctrl+C lo detiene.

Para comprobarlo únicamente en la PC puede omitirse `--bind`: usa loopback. Ese
modo no valida que el tráfico real del celular entre al TUN.

## Generador del celular

La sección Laboratorio de pruebas aparece solamente en compilaciones debug. Para
habilitarla, iniciar una captura completa. Indicar la IP privada del receptor.
El generador abre una conexión nueva por acción y admite estos perfiles:

- Ráfaga: 20 conexiones en aproximadamente 10 segundos; máximo dos por segundo.
- Distribuidas: 20 conexiones durante aproximadamente 40 segundos.
- Periódicas: seis conexiones con separaciones de cinco segundos.
- Subida legítima: 2 MiB de datos sintéticos en una conexión.

Mantener la pantalla abierta. Salir cancela la rutina; no finalizar la captura
antes de que termine. La recepción confirmada puede ser menor que lo programado;
comprobar el código mostrado contra el log del receptor. El perfil no asigna una
intención o nivel de riesgo. Ráfaga y periodicidad pueden ser actividades legítimas.
Completar 60 segundos de captura y después finalizar y exportar.

## Preparar una ejecución y sus referencias

Copiar a una carpeta local el JSON exportado y el registro del receptor. Por ejemplo:

```powershell
.\backend\.venv\Scripts\python.exe .\backend\experiment.py prepare-run .\backend\data\experiments\captura.json .\backend\data\experiments\receptor-piloto-01.jsonl --execution-id lab-CODIGO_MOSTRADO --scenario S06 --pair par-01 --output .\backend\data\experiments\borrador-postest.json
```

El preparador exige recepción dentro del intervalo capturado. El borrador queda
`valid_for_evaluation: false`, `reviewed: false` y sin nivel de riesgo de referencia.
Para S06 y S07, el estado es `candidate_simulation`. No convierte recepción en
amenaza confirmada ni interpreta la predicción de la app como referencia.

Revisar de forma independiente la unidad, evidencia, finalidad, conexiones
realmente recibidas, reloj, criterios de impacto/probabilidad y rúbrica. Mantener
los controles S04/S05 equivalentes. Si las características son idénticas, ML no
puede identificar la intención. Dejar documentada esa limitación y excluir esa
afirmación de detección del estudio.

## Entrenar y evaluar

Las filas exportadas son ventanas completas de 30 segundos. Conservar esa unidad
en entrenamiento e inferencia. Completar manualmente las columnas `group_id`,
`evaluation_split`, `reference_event_type`, `reference_risk_level` y
`reference_reviewed`. Agrupar capturas, ventanas y variantes relacionadas en la
misma partición. No usar códigos, escenarios, fechas, predicciones, IP o SSID como
características del modelo.

El CSV exige controles `normal` y al menos un tipo de patrón, además de los tres
niveles de riesgo. Por clase necesita al menos tres grupos en entrenamiento y dos
reservados en prueba, y representación en los tres pliegues. Son condiciones de
funcionamiento del programa, no una justificación del tamaño muestral de la tesis.

```powershell
.\backend\.venv\Scripts\python.exe .\backend\experiment.py train-events .\backend\data\experiments\eventos-etiquetados.csv --output .\backend\data\experiments\modelo-piloto --data-kind pilot
```

El modelo de piloto nunca se activa. Un conjunto de estudio revisado puede usar
`--data-kind study --approve-inference --output .\backend\data\event_models`.
Además de esa aprobación explícita, ambas cabezas deben superar la línea dummy
en F1 macro del conjunto reservado. Revisar precisión, sensibilidad, errores por
clase y falsas alarmas; superar dummy no garantiza utilidad ni mejora a Suricata.
Cambiar el modelo entre ejecuciones, no durante una captura experimental.

## Pretest con Suricata

El motor Suricata debe instalarse o estar disponible en el entorno de laboratorio.
En esta sesión no estaba instalado; WSL tampoco pudo enumerar un entorno usable
(`REGDB_E_CLASSNOTREG`). No se ejecutó un pretest real ni se crearon sus resultados.

Definir el punto de observación entre relé y receptor y verificar que Suricata ve
esas conexiones. El relé crea conexiones nuevas; los SYN y paquetes de Suricata
no equivalen a los contadores originales del TUN. Comparar eventos observables
por ambos mecanismos, con condiciones y versiones fijadas.

La regla `suricata_lab.rules.example` solo es una plantilla para conteo de SYN en
un receptor propio. Su dirección de documentación debe sustituirse y la regla
validarse antes de usarla. No demuestra intención maliciosa ni sustituye la
referencia. Registrar retransmisiones y diferencias con conexiones exitosas.

Completar una copia de `suricata_mapping_vacio.json` con firmas realmente utilizadas:
cada firma necesita `event_type` y `severity_to_risk`. No suponer que severidad 1
equivale automáticamente a riesgo alto. El importador solo conserva tipos, tiempos
y niveles mapeados; no exporta IP, firma textual ni contenido.

```powershell
.\backend\.venv\Scripts\python.exe .\backend\experiment.py import-suricata .\backend\data\experiments\eve.json --mapping .\backend\data\experiments\suricata-mapping.json --started-at-utc FECHA_UTC_DEL_JSON --output .\backend\data\experiments\detecciones-pretest.json
```

## Calcular el anexo 2

Copiar `manifesto_vacio.json`, fijar la rúbrica y tolerancia antes del conjunto final
y agregar las ejecuciones revisadas a `executions`. Cada ejecución usa el formato
del borrador, con referencias y detecciones verificadas. Para pretest, usar
`phase: pretest`, `mechanism: suricata` y las detecciones importadas. Para postest,
usar `phase: postest`, `mechanism: wifiprevent` y las predicciones guardadas de la app.

Conservar códigos de referencia equivalentes en el mismo `comparison_pair_id`.
El programa rechaza comparaciones con referencias diferentes. Las referencias
normales no suman amenazas; `candidate_simulation` no cuenta como amenaza de
referencia y excluye la ejecución de un manifiesto `study`. La ausencia de amenazas
deja vacío el porcentaje de detección. Un riesgo `unknown` no cuenta como acierto
cuando existe referencia válida.

Las predicciones normales sin alertas pueden registrarse en `risk_assignments`,
con `reference_event_id` y `risk_level` derivados de las `window_assessments` del
JSON. No copiar el nivel de referencia a ese campo. No asignar un mismo evento
dos veces ni reutilizar una alerta para contar múltiples detecciones.

```powershell
.\backend\.venv\Scripts\python.exe .\backend\experiment.py report .\backend\data\experiments\manifesto-revisado.json --output .\backend\data\experiments\informe-01
```

Produce `anexo2.csv` y `evaluacion.json`, con omisiones, falsas alarmas, duplicados,
coincidencias y diferencias en puntos porcentuales. Sin ejecuciones, el comando
falla explícitamente en lugar de producir resultados ficticios.

El análisis inferencial está desactivado para pilotos. Si el protocolo del estudio
justifica pares independientes y permutables, puede fijar `statistics_protocol`
con `method: paired-permutation-v1` y `paired_units_independent: true`. Con al menos
cinco pares comparables por indicador, calcula permutación bilateral de diferencias,
intervalo bootstrap por pares y ajuste Holm de los indicadores evaluados. Este
requisito no acredita potencia estadística. No cambia automáticamente la hipótesis
de la tesis ni interpreta un valor p como prueba de eficacia general.

## Contrato y privacidad

La API conserva autorización por instalación. El canal local del relé ahora
comprueba el propietario de la captura y evita que otra instalación la reinicie
durante la ventana de una hora. Hay un acumulador activo: ejecutar las pruebas
del laboratorio con un solo celular. Reiniciar el relé elimina ese estado temporal.

La solicitud de análisis añade `temporal_capture` opcional. Los recibos e historial
añaden `detected_events` y `window_assessments`; Android conserva la serie para
reintentos. Los clientes anteriores pueden seguir enviando lecturas sin serie.
Las exportaciones autenticadas son `/{session_id}/event-observations` para CSV y
`/{session_id}/experiment-observation` para JSON, bajo `/api/v1/analysis-sessions`.
Ambas usan `Cache-Control: no-store` y excluyen identificadores de instalación/red.

Los registros, datasets y modelos experimentales quedan fuera de Git en
`backend/data/experiments` y `backend/data/event_models`. El contenido de paquetes
no se almacena en la app. Las trazas que Suricata necesite para el laboratorio
deben tener una conservación y finalidad propias, separadas de la aplicación.
El backend y SOCKS5 siguen siendo servicios locales de desarrollo; usar otra red
pública requiere resolver acceso y transporte seguros, no publicar estos puertos.

## Verificación del software

Se comprobaron compilación Android, pruebas unitarias, pruebas instrumentadas en
un emulador temporal, migraciones en PostgreSQL de pruebas, contrato de guardado,
compatibilidad con registros anteriores, límite de datos, reintentos, autenticación,
exportación, controles de fuga de datos y cálculo del anexo. Los fixtures del
entrenamiento se crean en temporales, no en el directorio de modelos activos.

Quedan pendientes la ejecución del generador y receptor con el Honor, el pretest
real con Suricata y el dataset revisado. Los números de pruebas de software no son
mediciones de desempeño de la tesis.

[Regresar al diseño de escenarios](README.md).
[Referencia de permutaciones pareadas de SciPy](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.permutation_test.html).
