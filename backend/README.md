# WiFiPrevent local con PostgreSQL

## Iniciar después de reiniciar la PC

Desde la terminal de Android Studio, en la raíz del proyecto:

```powershell
.\backend\.venv\Scripts\python.exe .\backend\local_services.py
```

El comando acepta `start`, `stop`, `restart` y `status`. Por ejemplo:

```powershell
.\backend\.venv\Scripts\python.exe .\backend\local_services.py restart
.\backend\.venv\Scripts\python.exe .\backend\local_services.py stop
```

Los procesos se ejecutan sin ventanas adicionales. El estado muestra por separado
`Backend activo` y `SOCKS5 activo`. PostgreSQL continúa limitado a
127.0.0.1:55432. FastAPI escucha en el puerto 8001 de la PC para permitir pruebas
desde el emulador y desde un teléfono conectado a la misma red privada. El emulador
usa 10.0.2.2 y el teléfono usa la dirección configurada como `LOCAL_BACKEND_HOST`
en `app/build.gradle.kts`. Esta conexión HTTP solo está habilitada en compilaciones debug.

El relé SOCKS5 de desarrollo escucha únicamente en `127.0.0.1:1080`; el emulador
puede alcanzarlo mediante `10.0.2.2:1080`. En esta etapa admite conexiones TCP y
no almacena el contenido reenviado ni registra los destinos. La pantalla de análisis
permite comprobar la negociación SOCKS5 desde el emulador. El relé acepta TCP y
`UDP ASSOCIATE`, necesario para reenviar consultas DNS del túnel. Su disponibilidad
confirma el transporte local, pero la captura completa solo empieza después de que
el usuario la autoriza desde la aplicación.
La aplicación incluye hev-socks5-tunnel 2.17.1 para `x86_64` y `arm64-v8a`.
La comprobación de transporte valida tanto la negociación con el relé como la carga
JNI del motor. La configuración preparada usa el TUN `10.77.0.2`,
el relé `10.0.2.2:1080`, UDP directo y registros nativos deshabilitados. El archivo
se generará en el almacenamiento privado de la aplicación y no contendrá credenciales.

En Android 13 o superior, la pantalla ofrece una captura completa experimental
después de comprobar el transporte. En esta etapa el túnel enruta IPv4, configura DNS y
excluye `10.0.2.2/32` para que la conexión con el relé del emulador no vuelva a
entrar a la propia VPN. IPv6 permanece fuera del túnel hasta incorporar un relé UDP
de doble pila. Cada sesión conserva `capture_mode` como `controlled`
o `full`. En el modo completo actual se validan reenvío y volumen; la clasificación
detallada de protocolos todavía no está conectada al motor nativo.
Las sesiones `full` muestran paquetes y bytes leídos y escritos por el motor en
la interfaz TUN; se muestrean cada segundo y se conserva una lectura final antes
de detener el motor. Las sesiones `controlled` mantienen los contadores agregados
de Android utilizados para la validación.
En `full`, el servidor genera observaciones técnicas a partir de la duración,
los paquetes y los bytes reenviados. Puede advertir una captura vacía o un volumen
de salida claramente predominante; estas señales requieren revisión y no confirman
por sí solas una amenaza. La clasificación detallada de protocolos permanece
pendiente y el nivel de riesgo todavía se calcula a partir de los metadatos de
conexión Wi-Fi.

Para habilitar el acceso del teléfono, abrir PowerShell como administrador y ejecutar:

```powershell
.\backend\configure_phone_access.ps1 Add
```

La regla se limita al ejecutable Python de este proyecto, al puerto TCP 8001 y al
perfil de red privada. Puede comprobarse con `Status` y retirarse con `Remove`.

## Probar en Android

Ejecutar con Run. Pulsar Guardar consulta, esperar confirmación y abrir Ver historial.
Cada registro muestra fecha local, red, señal, frecuencia, velocidad, seguridad,
conectividad y evaluación de riesgo. Las consultas nuevas guardan el nivel y sus
razones; los registros anteriores a esta función permanecen como no evaluados.
Cerrar y volver a abrir conserva el historial.

Las consultas se separan por una clave aleatoria de instalación, guardada en las
preferencias privadas de Android y excluida de copias de seguridad. Al borrar los
datos o reinstalar, se pierde el acceso a ese historial desde la app. No es un
sistema de cuentas; antes de desplegar fuera de la PC se requiere autenticación
de usuarios, HTTPS y una política de conservación/eliminación de registros.

## Datos y configuración

La tabla connection_checks guarda UUID del recibo, hash de la clave de instalación,
identificador de envío, fecha UTC, SSID y métricas de conexión. No captura ni almacena
paquetes, coordenadas, contraseñas Wi-Fi ni contenido de comunicaciones.
La app conserva temporalmente el último envío sin confirmar para poder reintentarlo.

PostgreSQL 17.11 se distribuye por EDB: https://www.enterprisedb.com/download-postgresql-binaries
Binarios y datos locales: .local/pgsql y .local/pgdata. Configuración y contraseñas
aleatorias: .local/database.json y .local/app-config.json; están excluidas de Git.
No borrar .local/pgdata: contiene los registros. DATABASE_URL permite configurar
otro PostgreSQL, incluido RDS posteriormente. Nunca imprimir ni publicar esa URL.

En otro equipo: crear un entorno Python, instalar requirements.txt, preparar
PostgreSQL y configurar DATABASE_URL. Ejecutar `python -m alembic upgrade head`.
El lanzador local es específico del entorno portátil preparado en esta PC.

## API

GET /health comprueba la base de datos. Documentación: http://127.0.0.1:8001/docs
POST /api/v1/connection-checks valida y guarda; responde únicamente tras commit.
GET /api/v1/connection-checks?limit=20 devuelve la página más reciente.
`next_before` es el cursor de la página siguiente (`before=UUID`).
Ambas operaciones requieren Authorization: Bearer seguido de 64 caracteres hexadecimales.
POST requiere también X-Request-ID: UUID. Repetir la misma solicitud devuelve el
mismo recibo; reutilizar el identificador con otros datos devuelve 409.
No se registran cuerpos ni claves en los logs HTTP. El estado `received` confirma
la persistencia. Las consultas nuevas incluyen `risk_level`, `risk_reasons` y el
alcance de la evaluación. En modo `full`, el riesgo combina la seguridad informada
por Android con señales agregadas del túnel; un volumen de salida predominante puede
elevar una evaluación baja a media para revisión. La clasificación actual usa reglas
explícitas y todavía no utiliza aprendizaje automático.
GET /api/v1/analysis-sessions/summary devuelve los totales de sesiones por nivel
de riesgo y modo de captura para la instalación autenticada. Incluye por separado
los registros sin información suficiente y los registros históricos no evaluados.
GET /api/v1/analysis-sessions acepta los filtros opcionales `risk_level` (`low`,
`medium`, `high` o `unknown`) y `capture_mode` (`controlled` o `full`). Los filtros
se mantienen durante la paginación y solo consultan la instalación autenticada.
DELETE /api/v1/analysis-sessions/{session_id} elimina una sesión únicamente cuando
pertenece a la instalación autenticada. Devuelve 204 sin contenido; para evitar
confirmar la existencia de datos ajenos, una sesión inexistente o de otra instalación
devuelve 404.
Cada sesión guarda `assessment_version`, que identifica la versión de reglas utilizada
para producir su evaluación. La migración marca como `legacy` los resultados calculados
antes de incorporar este versionado; no vuelve a calcular ni altera su nivel original.
Las sesiones que incorporan la señal de portal cautivo usan `rules-aggregate-v2`.
Las sesiones `v1` permanecen sin cambios.
Las sesiones nuevas también guardan `captive_portal`, tomado del estado de red que
Android informa al iniciar la captura. La evaluación lo describe como una condición
que requiere autenticación y no como prueba de que la red sea maliciosa. Los registros
anteriores conservan `null` para distinguirlos de una detección negativa real.

## Pruebas

Dentro de backend:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

Las pruebas usan exclusivamente wifiprevent_test; nunca la base de datos del aplicativo.
Validan persistencia entre conexiones, aislamiento, paginación, reintentos, datos
inválidos y errores de base de datos. Las migraciones se gestionan con Alembic.
