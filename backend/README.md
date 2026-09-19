# WiFiPrevent local con PostgreSQL

## Iniciar después de reiniciar la PC

Desde la terminal de Android Studio, en la raíz del proyecto:

```powershell
.\backend\.venv\Scripts\python.exe .\backend\local_services.py
```

Los procesos se ejecutan sin ventanas adicionales. PostgreSQL escucha solo en
127.0.0.1:55432; FastAPI solo en 127.0.0.1:8001. El emulador accede por 10.0.2.2:8001.
Esta versión usa el puerto 8001; el prototipo anterior usaba 8000.
No se crean servicios de Windows ni reglas de firewall.

## Probar en Android

Ejecutar con Run. Pulsar Guardar consulta, esperar confirmación y abrir Ver historial.
Cada registro muestra fecha local, red, señal, frecuencia, velocidad de enlace y
conectividad. Todos muestran Riesgo no evaluado. Cerrar y volver a abrir conserva
el historial. Los recibos de la versión anterior no se guardaban y no se recuperan.

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
No se registran cuerpos ni claves en los logs HTTP.
El estado recibido sigue siendo `received` por compatibilidad, pero ahora confirma
persistencia. risk_level=null y analysis_performed=false.

## Pruebas

Dentro de backend:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
```

Las pruebas usan exclusivamente wifiprevent_test; nunca la base de datos del aplicativo.
Validan persistencia entre conexiones, aislamiento, paginación, reintentos, datos
inválidos y errores de base de datos. Las migraciones se gestionan con Alembic.
