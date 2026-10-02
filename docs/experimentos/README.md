# Escenarios y referencias para la evaluación de WiFiPrevent

Plan del 30 de septiembre de 2026. Este primer paso define qué observar y cómo
comprobarlo. No contiene ejecuciones reales, amenazas detectadas, porcentajes ni
modelos entrenados. No modifica el proyecto de tesis ni la captura de la app.

El diseño de este documento corresponde al primer paso. Para el estado posterior
de los módulos implementados y las comprobaciones pendientes, consultar
[Implementación experimental](IMPLEMENTACION.md). Las limitaciones descritas como
ausentes en la revisión inicial son la línea base, no el estado actual del código.

## Resultado de la revisión

Los datos actuales permiten verificar transporte, volumen, categorías por puerto
y exposición de la conexión. No bastan para contar amenazas individuales ni
atribuir una intención maliciosa. La primera evaluación debe comprobar si los
patrones elegidos son distinguibles con los datos que podemos recoger.

El objetivo de trabajo es detectar patrones observables en el tráfico IPv4 propio
del celular y evaluar eventos con referencias independientes. La exposición Wi-Fi
y el riesgo de un evento son resultados distintos. Una red abierta no constituye
por sí sola una amenaza detectada en el indicador del anexo 2.

## Evidencia de la implementación actual

| Parte revisada | Lo que permite | Limitación para los experimentos |
| --- | --- | --- |
| `AnalysisSessionReading` | Duración, bytes, paquetes, seguridad, portal y totales del relé | Una fila representa una sesión; no contiene eventos ni intervalos |
| `RelayTrafficMetrics.observe` | TCP exitoso, datagramas UDP y categorías por puertos 53, 80 y 443 | No interpreta solicitudes, respuestas, autenticación ni cifrado real |
| Conexión TCP del relé | Incrementa el contador después de conectar al destino | Los intentos fallidos no están incluidos |
| Destinos únicos | Cuenta huellas HMAC del host, sin exponer direcciones | No distingue puertos del mismo host ni conserva una secuencia temporal |
| Motor nativo | Contadores IPv4 del TUN | No observa tramas Wi-Fi, ARP ni el tráfico de otros dispositivos; IPv6 queda fuera |
| Evaluación por reglas | Seguridad Wi-Fi y advertencia por volumen de salida | No es un detector validado de ataques |
| ML y exportación | Base de clasificación de riesgo por sesión | Sin modelo activo; faltan datos de referencia y detección por evento |

Una ráfaga de 20 conexiones y 20 conexiones distribuidas pueden tener los mismos
totales. El contador de conexiones tampoco equivale al de solicitudes: una conexión
persistente puede transportar muchas solicitudes. Estas diferencias deben resolverse
antes de usar esos datos como detecciones.

## Catálogo de escenarios

El archivo [catalogo_escenarios.csv](catalogo_escenarios.csv) contiene los casos.
Los nombres no son etiquetas para entrenar y los estados no son resultados.

| Código | Actividad | Función en el piloto | Con datos actuales |
| --- | --- | --- | --- |
| S00 | Celular sin actividad deliberada | Control del ruido de fondo | Observación parcial; no asumir cero tráfico |
| S01 | Navegación legítima por HTTPS | Control normal | Volumen y puertos observables |
| S02 | Descarga legítima de archivo de prueba | Control de volumen entrante | Observable |
| S03 | Subida legítima de archivo de prueba | Control de falsa alarma por salida | Observable; puede activar la advertencia actual |
| S04 | Ráfaga legítima de conexiones de una aplicación de prueba | Control parecido a S06 | Totales visibles; distribución temporal ausente |
| S05 | Sondeo legítimo periódico de estado | Control parecido a S07 | Totales visibles; periodicidad ausente |
| S06 | Ráfaga automatizada marcada como abuso simulado en un servidor propio | Candidato de detección | Necesita intervalos y asociación a un evento |
| S07 | Comunicación periódica que imita un beacon en un servidor propio | Candidato de detección | Necesita tiempos; no demuestra malware |
| S08 | Igual cantidad de conexiones que S06, distribuida en el tiempo | Contraste de temporalidad | Puede resultar indistinguible actualmente |
| S09 | Petición HTTP legítima en servicio propio | Control de exposición por puerto | Puerto 80 visible; no es amenaza por sí solo |
| S10 | Misma actividad en redes propias con seguridad distinta | Control de exposición Wi-Fi | Tipo de seguridad si Android lo informa |
| S11 | Desconexión y reconexión de Wi-Fi | Control funcional | No participa en el conteo de amenazas |

S06 y S07 son candidatos de laboratorio, no amenazas confirmadas en redes públicas.
No se asignará automáticamente un riesgo alto. Sus niveles de referencia requieren
criterios de impacto, probabilidad y evidencia acordados antes del conjunto final.

Si S04 y S06, o S05 y S07, entregan los mismos datos, el sistema no puede deducir
la intención solo a partir de esos datos. El escenario se descarta para una
afirmación de detección de amenazas o se incorpora otra evidencia compatible con
el alcance. No se renombra una anomalía como ataque para completar el anexo.

Quedan fuera de esta primera selección: detección de evil twin, desautenticación,
ARP spoofing, robo de credenciales, contenido de HTTPS y actividad ajena al celular.
La interfaz IP de VpnService no constituye un sensor de tramas Wi-Fi.

## Laboratorio y condiciones previas

Se utilizarán el Honor 400 Lite, una red privada propia y la PC con backend y relé.
El destino de pruebas será un servicio propio en una dirección distinta de la
dirección del relé excluida del túnel. Puede alojarse en una máquina virtual con
red accesible; hay que verificar primero el enrutamiento. Probar contra la misma
IP excluida podría evitar la captura y producir una conclusión incorrecta.

No hay que instalar un sensor portátil para los usuarios. Suricata será una
herramienta del laboratorio en la PC o en una VM, con acceso comprobado al tráfico
entre el relé y el destino. No verá necesariamente los paquetes originales del TUN:
el relé crea nuevas conexiones TCP. Documentar ese punto de observación y limitar
la comparación a eventos de aplicación que ambos mecanismos puedan observar.

Antes de ejecutar candidatos se necesita un generador acotado, un receptor de
pruebas y un registro independiente de acciones. No están implementados en este
paso. Usar archivos y datos sintéticos; no tráfico de terceros ni credenciales.

El piloto debe verificar primero que la actividad pasa por el túnel, que llega al
receptor y que incrementa las métricas esperadas. El relé tiene un acumulador activo:
ejecutar un solo celular y una sola sesión experimental a la vez.

## Piloto propuesto

1. Empezar con S01, S03 y S11 para comprobar transporte, una posible falsa alarma
   por subida legítima y recuperación del guardado.
2. Después de incorporar mediciones temporales y el generador, comparar S06 con
   S04 y S08; comparar S07 con S05. Mantener igual seguridad Wi-Fi en cada contraste.
3. Utilizar sesiones orientativas de 60 segundos y cinco repeticiones iniciales
   por variante. Son pruebas de factibilidad, no el tamaño de muestra de la tesis.
4. Para la primera ráfaga, proponer 20 conexiones nuevas durante 10 segundos, con
   máximo 2 conexiones por segundo a un destino propio y carga pequeña. No saturar
   el servicio. Distribuir 20 conexiones durante 40 segundos en S08.
5. Para la periodicidad, proponer una conexión nueva cada 5 segundos durante 30
   segundos. S05 debe tener un perfil equivalente para poner a prueba la limitación
   de inferir intención. Los valores son parámetros del generador, no umbrales de
   detección validados.
6. Verificar conexiones realmente establecidas, además de las programadas. Si el
   navegador reutiliza conexiones, no asumir que cumplió el patrón. El generador
   deberá controlar esa condición sin modificar el tráfico observado.
7. Registrar diferencias observables y casos indistinguibles. Aprobar solo casos
   con referencias, visibilidad y controles de falsas alarmas adecuados.

Las restricciones de exportación ML actuales, de 30 segundos y 100 paquetes, son
un requisito del CSV existente. No invalidan un control silencioso ni autorizan
añadir tráfico artificial para hacerlo parecer una observación adecuada. Las
plantillas de este directorio no son el CSV de entrenamiento de la app.

## Referencias y conteo para el anexo 2

El generador y el receptor registrarán qué se programó y qué se ejecutó, con un
código de evento y tiempos UTC. Una ráfaga deliberada será un evento; sus conexiones
no se contabilizarán automáticamente como muchas amenazas. Definir esta unidad
antes de medir y mantenerla idéntica en ambos mecanismos.

La referencia se fija antes de consultar las predicciones. En el piloto,
`candidate_simulation` indica un patrón simulado y no se incluye aún como amenaza
válida en el anexo. Solo se contabiliza una referencia `confirmed_test_threat`
cuando el protocolo justifique el escenario, su ejecución y la evidencia necesaria.
Los controles usan `normal_control`, y las pruebas funcionales `functional_control`.

Para evaluar el anexo se registrará Suricata como `pretest` y WiFiPrevent como
`postest`, usando escenarios comparables, versiones y configuraciones fijadas.
Suricata no define por sí solo las amenazas totales ni el riesgo de referencia.
Una regla propia que reconoce un patrón simulado solo demuestra ese patrón; no
equivale a que Suricata haya confirmado un ataque real.

El emparejamiento de detecciones debe usar código de ejecución, intervalo y tipo
de evento; cada referencia puede tener como máximo una detección correcta por
mecanismo. Fijar la tolerancia temporal antes de la evaluación, tras el piloto.
Conservar también detecciones duplicadas, falsas alarmas y eventos omitidos.

- Detección: amenazas de referencia detectadas correctamente / amenazas de
  referencia válidas × 100. No usar el número bruto de alertas como numerador.
- Si no hay amenazas de referencia, ese porcentaje es no aplicable, no 0 % ni 100 %.
  El control sigue siendo útil para medir falsas alarmas.
- Clasificación de riesgo: eventos con nivel coincidente / eventos válidos con
  referencia evaluados × 100. Incluir resultados `unknown` como no coincidentes
  cuando exista referencia válida; no eliminarlos para aumentar el porcentaje.
- Una misma rúbrica interpreta las severidades de Suricata y los niveles de la
  propuesta. Definirla antes del conjunto final; no equiparar números sin justificarlo.

No se presupone que WiFiPrevent mejorará a Suricata. Si el resultado no muestra
mejora, se informa. La significancia y el número de ejecuciones finales requieren
el diseño estadístico del estudio, no se derivan de estas cinco repeticiones.

## Plantillas y privacidad

- [plantilla_ejecuciones.csv](plantilla_ejecuciones.csv): condiciones, variante,
  duración y validez de cada ejecución.
- [plantilla_eventos_referencia.csv](plantilla_eventos_referencia.csv): eventos
  independientes, evidencia, nivel y aprobación para medir el anexo.
- [plantilla_resultados_eventos.csv](plantilla_resultados_eventos.csv): detecciones
  emparejadas y falsas alarmas. Una falsa alarma no lleva `reference_event_id`.
- [plantilla_anexo2.csv](plantilla_anexo2.csv): resumen por ejecución y mecanismo.

Son archivos de encabezados vacíos. No hay datos fabricados ni campos completados
automáticamente. Los códigos son locales y no contienen IP, SSID, dominios, usuario,
token de instalación ni contenido. La evidencia se identifica por un código; si
el laboratorio necesita trazas o registros, se mantienen aparte con conservación
definida. La app mantiene su política actual de no guardar contenido de tráfico.

No entrenar con estas plantillas directamente. La transformación al dataset se
definirá después de fijar la unidad de observación. Mantener las ejecuciones y
variantes relacionadas en el mismo grupo de partición; nunca trasladar la etiqueta
de la app a la referencia. La validación ML actual de una etiqueta por escenario
tendrá que adaptarse si el grupo experimental incluye controles de otra clase.

## Criterio para cerrar la factibilidad

Para cada candidato se necesita: visibilidad por ambos mecanismos; referencia
independiente ejecutada; datos que distingan al menos el patrón elegido de sus
controles; unidad de conteo y rúbrica fijadas; y evaluación en ejecuciones reservadas.
Las diferencias de volumen por sí solas no acreditan intención maliciosa.

Si ningún candidato cumple estas condiciones, no activar ML ni llenar el anexo
con etiquetas derivadas de la seguridad Wi-Fi. Ampliar la evidencia o reconsiderar
el alcance antes de afirmar que se detectan amenazas.

El siguiente cambio técnico será recoger métricas temporales y por conexión con
límites de memoria y sin almacenar contenido. La elección final de candidatos
dependerá del piloto; este documento no promete su eficacia.

## Fuentes técnicas

- [Android VpnService](https://developer.android.com/reference/android/net/VpnService): interfaz virtual que entrega paquetes IP.
- [Suricata EVE JSON](https://docs.suricata.io/en/suricata-8.0.0/output/eve/eve-json-format.html): eventos y correlación mediante `flow_id`.
- [Suricata thresholding](https://docs.suricata.io/en/suricata-7.0.15/rules/thresholding.html): conteo de coincidencias en intervalos; no constituye una referencia independiente.
- [scikit-learn y fuga de datos](https://scikit-learn.org/stable/common_pitfalls.html): separación de entrenamiento y evaluación.
