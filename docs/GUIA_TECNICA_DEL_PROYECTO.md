# Guía técnica de WiFiPrevent

## 1. Propósito del aplicativo

WiFiPrevent es un prototipo de ciberseguridad preventiva para observar una conexión
Wi-Fi desde un teléfono Android y generar una evaluación preliminar basada en
metadatos. Su objetivo es advertir condiciones que merecen atención sin leer mensajes,
contraseñas, archivos ni el contenido de las comunicaciones.

El proyecto separa tres conceptos:

- **Condición de la red:** seguridad Wi-Fi informada por Android, acceso validado y
  presencia de portal cautivo.
- **Comportamiento agregado:** duración, paquetes y bytes que atraviesan el túnel.
- **Calidad de la muestra:** indica si hubo datos suficientes para considerar la
  evaluación preliminar representativa.

Un indicador técnico no se presenta como prueba concluyente de un ataque. Por
ejemplo, mucho tráfico de salida puede corresponder a una copia de seguridad legítima.

## 2. Arquitectura general

```text
Teléfono o emulador Android
  ├─ Interfaz Jetpack Compose
  ├─ Lectura del estado Wi-Fi de Android
  ├─ VpnService autorizado por el usuario
  ├─ hev-socks5-tunnel (motor nativo)
  └─ Cliente HTTP
          │
          ├── FastAPI :8001 ── SQLAlchemy ── PostgreSQL
          │                    └─ reglas de evaluación
          │
          └── Relé SOCKS5 :1080 ── Internet
```

La aplicación Android es el punto de interacción. FastAPI valida, evalúa y guarda los
resultados. PostgreSQL mantiene el historial. El relé SOCKS5 permite que el tráfico
IPv4 capturado por la VPN experimental continúe hacia Internet durante el desarrollo.

## 3. Tecnologías utilizadas

### Android y Kotlin

- **Kotlin 2.2.10:** lenguaje principal de la aplicación. Reduce código repetitivo,
  distingue tipos que pueden ser nulos y ofrece corrutinas para operaciones de red.
- **Android SDK:** entrega el estado de la conexión, permisos y el servicio VPN.
- **Jetpack Compose y Material 3:** construyen las pantallas mediante funciones Kotlin.
  La interfaz se vuelve a dibujar cuando cambia el estado.
- **VpnService:** crea una interfaz TUN después de que Android muestra su diálogo de
  autorización. Permite contar tráfico del dispositivo sin acceso root.
- **SharedPreferences:** conserva la identidad aleatoria de la instalación, los datos
  locales necesarios para reintentar un guardado y el estado de la sesión. El historial
  definitivo está en PostgreSQL.
- **HttpURLConnection:** envía JSON al backend local. En el emulador usa `10.0.2.2`,
  que representa a la PC anfitriona.
- **hev-socks5-tunnel 2.17.1:** biblioteca nativa incluida para `x86_64` y `arm64-v8a`.
  Reenvía el tráfico de la interfaz TUN hacia SOCKS5 y entrega contadores agregados.
- **Gradle:** resuelve dependencias, compila Kotlin/Java, ejecuta pruebas y genera APK.
- **JUnit:** verifica los componentes Kotlin que no necesitan un dispositivo real.

### Backend y Python

- **Python:** lenguaje del servidor y de las herramientas locales.
- **FastAPI:** define endpoints HTTP, transforma JSON y genera documentación OpenAPI.
- **Pydantic:** valida tipos, límites y campos permitidos antes de usar los datos.
- **SQLAlchemy:** representa tablas como clases y construye consultas seguras.
- **PostgreSQL:** guarda conexiones y sesiones con persistencia, índices y restricciones.
- **Alembic:** versiona cambios de la estructura de PostgreSQL mediante migraciones.
- **Uvicorn:** ejecuta la aplicación FastAPI en el puerto 8001.
- **psycopg:** controlador que comunica Python con PostgreSQL.
- **pytest:** ejecuta pruebas de API, aislamiento, persistencia y reglas.
- **Relé SOCKS5 en Python:** acepta `CONNECT` para TCP y `UDP ASSOCIATE` para UDP.
  No registra cuerpos ni destinos en la etapa actual.

## 4. Organización de carpetas

```text
WifiPrevent/
├─ app/
│  └─ src/main/
│     ├─ java/com/anderson/wifiprevent/
│     │  ├─ data/
│     │  │  ├─ local/       estado temporal e identidad
│     │  │  ├─ network/     observación de la conexión Android
│     │  │  ├─ remote/      cliente del backend
│     │  │  ├─ repository/  entrada única a los datos
│     │  │  └─ vpn/         VPN, relé y motor nativo
│     │  ├─ domain/
│     │  │  ├─ model/       modelos usados por la aplicación
│     │  │  └─ traffic/     interpretación de cabeceras y acumulación
│     │  └─ ui/             pantallas, formatos y tema
│     ├─ java/hev/htproxy/  contrato Java/JNI con la biblioteca nativa
│     └─ jniLibs/           bibliotecas nativas por arquitectura
├─ backend/
│  ├─ app/
│  │  ├─ api/               rutas y dependencias HTTP
│  │  ├─ core/              identidad y reglas comunes
│  │  ├─ db/                conexión y modelos SQLAlchemy
│  │  ├─ schemas/           contratos Pydantic
│  │  └─ services/          reglas, ML opcional, indicadores y recomendaciones
│  ├─ migrations/           historial de cambios de base de datos
│  ├─ tests/                pruebas automatizadas
│  ├─ data/templates/       esquema CSV sin datos personales
│  ├─ data/models/          artefacto ML local (ignorado por Git)
│  ├─ train_risk_model.py   compara, valida y entrena clasificadores
│  ├─ local_services.py     inicia y detiene servicios locales
│  └─ socks5_relay.py       transporte de desarrollo
└─ docs/                    documentación del proyecto
```

Esta separación evita que una pantalla conozca detalles de PostgreSQL o que el
backend dependa de elementos visuales de Android.

## 5. Proceso implementado hasta ahora

### Etapa 1: consulta de conexión

Android obtiene SSID, señal RSSI, frecuencia, velocidad de enlace, seguridad, acceso
validado y portal cautivo. La velocidad de enlace es la negociación con el punto de
acceso y no equivale a la velocidad real de Internet.

La app envía los datos a `/api/v1/connection-checks`. El backend valida el cuerpo,
calcula una evaluación basada en reglas, guarda el registro y devuelve un UUID. El
encabezado `X-Request-ID` permite repetir un envío después de un corte sin duplicarlo.

### Etapa 2: identidad e historial

Cada instalación genera un token aleatorio de 64 caracteres hexadecimales. La app lo
envía como `Bearer`; el backend almacena su hash. Esto separa historiales sin crear
cuentas. Al borrar los datos o reinstalar se genera una identidad nueva.

El historial usa paginación por cursor. Los registros se ordenan por fecha y UUID; el
cliente pide la siguiente página con `before`. También se añadieron filtros por riesgo
y modo de captura, resumen estadístico y eliminación aislada por instalación.

### Etapa 3: validación controlada

La aplicación crea una VPN aislada y genera paquetes de prueba conocidos. El parser
lee solamente cabeceras IP y puertos para comprobar IPv4/IPv6, TCP/UDP/ICMP y pistas
DNS, HTTP y TLS/QUIC. Las direcciones únicas se convierten en huellas SHA-256 en memoria;
no se envían las direcciones al servidor.

Esta modalidad demuestra que el clasificador funciona, pero sus paquetes son
sintéticos y no representan toda la navegación.

### Etapa 4: captura completa experimental

Después de la autorización de Android, `TrafficAnalysisService` crea una interfaz TUN.
El motor nativo toma su descriptor, encapsula el tráfico y lo dirige al relé SOCKS5 de
la PC. La ruta del relé se excluye de la VPN para evitar un bucle en el que el tráfico
del túnel vuelva a entrar al mismo túnel.

El servicio se ejecuta en primer plano con una notificación permanente y un botón para
detenerlo. Cada segundo consulta al motor nativo y guarda contadores de paquetes y
bytes. Al detenerse toma una lectura final. El backend consulta la instantánea
agregada del relé y Android la conserva localmente antes de enviar la sesión.

El relé clasifica conexiones TCP y datagramas UDP por puerto de destino en DNS, HTTP,
TLS/QUIC u otros. Es una inferencia por puerto: no inspecciona la carga útil ni confirma
el protocolo dentro del contenido.

La activación de una VPN puede causar una interrupción muy breve mientras Android
cambia las rutas. El comportamiento observado en el emulador dura menos de un segundo.

### Etapa 5: evaluación preventiva

El backend aplica reglas explícitas y versionadas. Actualmente considera:

- tipo de seguridad Wi-Fi;
- portal cautivo como condición explicativa;
- mínimo de cinco segundos y diez paquetes;
- captura completa sin tráfico;
- volumen enviado superior a 1 MiB y a tres veces el recibido;
- uso observado del puerto 80 cuando existen metadatos clasificados.

Las capturas completas con observaciones del relé identifican el método como
`rules-relay-v3`; las sesiones sin ellas continúan con `rules-aggregate-v2`. Los registros
anteriores conservan `legacy`, `rules-aggregate-v1` o `rules-aggregate-v2` para no
reescribir resultados ya generados.

### Etapa 6: calidad de muestra

La calidad se calcula sin alterar el riesgo:

- **Insuficiente:** menos de 5 segundos o menos de 10 paquetes.
- **Limitada:** cumple el mínimo, pero dura menos de 30 segundos o tiene menos de
  100 paquetes.
- **Adecuada:** al menos 30 segundos y 100 paquetes.

Se calcula al guardar y al consultar el historial. Por eso funciona también con filas
antiguas sin agregar una columna a PostgreSQL.

### Etapa 7: observaciones, recuperación e historial

Las métricas del relé se guardan junto con la sesión cuando la instantánea está
disponible. Si falla el envío, Android conserva la sesión y reutiliza el mismo UUID al
reintentar. Si el relé perdió su instantánea porque se reiniciaron los servicios antes
de recuperarla, la app puede guardar los contadores del túnel con
`relay_metrics_collected=false`; esa ausencia no se presenta como valores observados en
cero.

El historial resume resultados por instalación, permite filtrar por nivel y modo, y
muestra recomendaciones basadas en reglas. Cada tarjeta permite compartir un texto con
los datos agregados, sin direcciones de destino ni contenido del tráfico.

## 6. Cómo fluye una sesión completa

1. El usuario prepara una sesión y la app genera su UUID.
2. Para captura completa, la app prepara en el relé el acumulador asociado a ese UUID.
3. Android muestra la autorización de VPN si aún no se había concedido.
4. La app registra SSID, seguridad y portal cautivo disponibles al comenzar.
5. `TrafficAnalysisService` crea el TUN y arranca el motor nativo.
6. El motor conduce IPv4 al relé SOCKS5 y actualiza contadores agregados; la pantalla
   consulta el estado local cada segundo.
7. El usuario detiene la sesión y se congelan los valores finales.
8. Android recupera y persiste localmente la instantánea del relé cuando sigue
   disponible.
9. `BackendClient` construye JSON y lo envía por HTTP.
10. Pydantic valida el contrato.
11. El backend usa un modelo supervisado local solo si el artefacto coincide con su
    manifiesto y la captura completa tiene métricas válidas y una muestra adecuada;
    en cualquier otro caso conserva la evaluación por reglas.
12. Los servicios Python preparan indicadores, recomendaciones y calidad de muestra.
13. SQLAlchemy inserta la sesión y PostgreSQL confirma la transacción.
14. FastAPI devuelve el recibo y la app muestra el resultado. Si el envío falla, se
    puede reintentar con el mismo UUID para evitar duplicados.

## 7. Kotlin explicado desde la base

Kotlin es un lenguaje compilado que Android recomienda para aplicaciones modernas. El
código se transforma en bytecode compatible con Android. Algunas construcciones usadas
en el proyecto son:

```kotlin
data class TrafficMetrics(
    val durationSeconds: Long,
    val receivedBytes: Long
)
```

Una `data class` representa datos. Kotlin genera igualdad, copia y texto descriptivo.
`val` significa referencia que no se reasigna; `var` permite cambiarla.

```kotlin
val ssid: String?
```

El signo `?` indica que el valor puede ser `null`. Kotlin obliga a tratar ese caso y
reduce fallos comunes. `ssid ?: "Nombre no disponible"` usa el texto alternativo cuando
el SSID es nulo. `wifi?.ssid` accede solo si `wifi` existe.

```kotlin
when (state) {
    READY -> prepare()
    ANALYZING -> showMetrics()
    else -> Unit
}
```

`when` reemplaza cadenas extensas de condiciones. Los `enum class` limitan un valor a
opciones conocidas, como `CONTROLLED` y `FULL`.

```kotlin
suspend fun saveAnalysis(...) = withContext(Dispatchers.IO) { ... }
```

Una función `suspend` puede esperar una operación sin bloquear la interfaz. Las
corrutinas se ejecutan desde `lifecycleScope`; `Dispatchers.IO` reserva hilos para red y
archivos. Esto evita que Android muestre “la aplicación no responde”.

Jetpack Compose usa funciones marcadas con `@Composable`. La pantalla depende de
variables de estado creadas con `mutableStateOf`. Cuando cambia el estado, Compose
vuelve a ejecutar solo las partes necesarias de la interfaz.

Las funciones de extensión agregan una operación cómoda a un tipo existente. Por
ejemplo, `JSONObject.nullableString(...)` centraliza la lectura segura de un campo JSON.

`runCatching { ... }` captura una excepción y permite convertirla en un resultado o un
valor alternativo. Se usa alrededor del motor nativo, pero los errores relevantes se
traducen a estados visibles para el usuario.

## 8. Python explicado en este proyecto

Python interpreta módulos `.py`. La indentación define los bloques; por eso no usa
llaves como Kotlin. Las anotaciones ayudan a documentar y validar tipos:

```python
def evaluate_sample_quality(
        duration_seconds: int,
        received_packets: int,
        transmitted_packets: int,
) -> Literal["insufficient", "limited", "adequate"]:
```

FastAPI lee esas definiciones junto con modelos Pydantic. Un modelo `BaseModel` describe
el JSON permitido. `Field(ge=0)` rechaza números negativos y `extra="forbid"` rechaza
campos desconocidos.

Las funciones decoradas con `@router.get` o `@router.post` son endpoints. FastAPI crea
una sesión de base de datos, obtiene la identidad de instalación y llama a la función.
Si se lanza `HTTPException`, responde con el código correspondiente.

SQLAlchemy permite escribir `select(...)` e `insert(...)` con objetos Python. La
transacción termina con `commit`; ante una incompatibilidad se ejecuta `rollback`.
Los parámetros se envían separados de la consulta para reducir riesgos de inyección.

Las pruebas con pytest comienzan con `test_`. Los fixtures preparan una base exclusiva
de pruebas y un cliente HTTP. Así se comprueba el flujo completo sin tocar los datos de
uso del aplicativo.

## 9. Privacidad y límites actuales

El sistema no guarda contenido de paquetes, contraseñas Wi-Fi ni coordenadas. El SSID
puede guardarse cuando Android lo entrega. El backend local usa HTTP solamente en
compilaciones de desarrollo. Antes de publicar se requieren HTTPS, cuentas o
autenticación robusta, política de conservación y un backend desplegado de manera
segura.

La evaluación usa reglas explícitas y versionadas mientras no exista un artefacto
supervisado compatible. El repositorio ya incluye la validación, comparación y
evaluación separada necesarias para entrenarlo, pero todavía no hay observaciones
autorizadas etiquetadas ni un modelo activo. La captura completa enruta IPv4; IPv6 queda
fuera de ese túnel. Las categorías de protocolo del relé son inferencias por puerto de
destino y no equivalen a inspección del contenido. El relé cuenta destinos únicos mediante
HMAC con una clave aleatoria que desaparece al reiniciar el proceso; solo se conserva el
total. Si Android recuperó la instantánea, guarda sus métricas localmente para permitir
el reintento aunque se reinicien los servicios. La comunicación de control del relé usa
`127.0.0.1:1081`; PostgreSQL conserva por separado conexiones TCP, datagramas UDP y
paquetes del túnel.

El proyecto de investigación plantea observar pasivamente la red Wi-Fi pública de
MegaPlaza, pero la app actual analiza el tráfico del propio teléfono que Android enruta
por `VpnService`; no captura tramas 802.11 ni observa a los demás usuarios del punto de
acceso. Esa diferencia debe resolverse con el asesor antes de presentar una prueba de
campo como validación del objetivo original.

## 10. Próximas etapas

1. Completar en el teléfono la verificación del informe compartible, la persistencia
   local y el reintento después de reiniciar los servicios.
2. Probar sesiones largas, cambios de Wi-Fi, estabilidad, batería y memoria en el Honor
   400 Lite.
3. Preparar el conjunto supervisado desde escenarios autorizados: fijar los niveles de
   referencia, registrar capturas adecuadas, separar escenarios completos en training y
   test, y revisar las métricas del clasificador.
4. Acordar con el asesor cómo resolver la diferencia entre analizar el tráfico del
   teléfono y capturar pasivamente la red pública completa. El backend actual requiere
   que el teléfono alcance la PC y no observa al resto de usuarios.
5. Acordar cómo estará disponible el backend durante la evaluación; el servicio actual
   no está preparado para exponerse directamente en una red pública.
6. Obtener los permisos del centro comercial y las aprobaciones institucionales antes
   de capturar datos reales.
7. Preparar el protocolo de prueba: escenarios, repeticiones, controles y criterios de
   referencia, sin generar tráfico fuera de una red controlada.
8. Ejecutar pruebas prolongadas en el Honor 400 Lite y documentar fallos, batería,
   cambios de red, IPv6 y la interrupción breve que puede causar el túnel VPN.
9. Comparar el pretest con Suricata y el postest con la app; analizar los resultados con
   pruebas estadísticas acordes con la distribución de las mediciones.
10. Preparar HTTPS, autenticación y despliegue seguro únicamente si el alcance final lo
   requiere.
11. Documentar el protocolo de evaluación, resultados, limitaciones y evidencias para el
   informe y la sustentación.

## 11. Comandos habituales

Desde la raíz del proyecto:

```powershell
.\backend\.venv\Scripts\python.exe .\backend\local_services.py restart
.\backend\.venv\Scripts\python.exe .\backend\local_services.py status
```

Pruebas del backend:

```powershell
cd backend
.\.venv\Scripts\python.exe -m pytest -q
```

Pruebas y compilación Android desde Android Studio o una terminal con Java configurado:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```
