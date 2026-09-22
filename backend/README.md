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
`UDP ASSOCIATE`, necesario para reenviar consultas DNS cuando se conecte el motor.
Todavía falta conectar el túnel VPN de Android
al relé, por lo que su presencia no significa que la captura completa esté activa.
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
la persistencia. Las consultas nuevas incluyen `risk_level`, `risk_reasons` y
`analysis_performed=true`. La clasificación actual usa reglas explícitas sobre el
tipo de seguridad informado por Android; todavía no utiliza aprendizaje automático.

## Pruebas

Dentro de backend:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

Las pruebas usan exclusivamente wifiprevent_test; nunca la base de datos del aplicativo.
Validan persistencia entre conexiones, aislamiento, paginación, reintentos, datos
inválidos y errores de base de datos. Las migraciones se gestionan con Alembic.
