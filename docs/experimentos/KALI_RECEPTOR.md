# Receptor del laboratorio en Kali Linux

Preparación del 2 de octubre de 2026. Kali recibe las acciones sintéticas del
generador Android y registra su recepción. PostgreSQL y el relé siguen en Windows.
Este registro no convierte una acción en amenaza ni reemplaza las referencias
independientes del estudio. Todavía falta ejecutar esta conexión en la VM real.

## Red antes de arrancar el receptor

En esta revisión, Windows tiene dos interfaces activas:

- Ethernet física: `192.168.18.207`, usada por la app para el backend y el relé.
- VirtualBox Host-Only Ethernet Adapter: `192.168.56.1`.

Son direcciones observadas, no direcciones que deban asignarse manualmente.
Pueden cambiar. La IP de Kali todavía debe comprobarse con:

```bash
ip -4 -br addr
ip -4 route
```

Si Kali ya usa la red host-only de VirtualBox, Windows debe poder alcanzarla por
esa interfaz. El Honor no necesita acceder directamente a la red host-only:
la VPN entrega el tráfico al relé y este establece la conexión con Kali.

Recorrido previsto:

```text
Honor -> VPN -> relé Windows 192.168.18.207 -> interfaz 192.168.56.1 -> Kali:8765
```

Si Kali solo muestra una dirección de NAT, como `10.0.2.15`, no asumir que Windows
puede conectarse directamente a ella. Si se usa VirtualBox, apagar Kali de forma
normal y agregar un segundo adaptador Host-only / Solo-anfitrión conectado al
adaptador existente. Conservar el adaptador NAT para Internet. Luego encender y
consultar las direcciones otra vez. No es necesario cambiar toda la VM a puente.

Si ya tiene una IP accesible en la LAN mediante puente, ese recorrido también
puede servir. Comprobar el origen elegido por Windows; no confundir la IP del
celular con la IP de origen del relé hacia el receptor.

Con la IP real de Kali, consultar en PowerShell:

```powershell
Find-NetRoute -RemoteIPAddress IP_KALI | Format-List IPAddress,InterfaceAlias
```

`IP_KALI` es un marcador que debe sustituirse. La dirección local que aparece es
la que se utilizará en `--allowed-client`. Para la ruta host-only prevista sería
`192.168.56.1`; para una ruta LAN podría ser `192.168.18.207`.

## Copiar solo el receptor

Copiar mediante una carpeta compartida o transferencia de archivos este archivo:

```text
backend/app/services/lab_receiver.py
```

Guardarlo en una carpeta de trabajo de Kali. Es autónomo y usa solamente la
biblioteca estándar de Python 3.10 o posterior: no necesita el backend, PostgreSQL,
FastAPI, pip ni las credenciales del proyecto. Comprobar Python:

```bash
python3 --version
python3 lab_receiver.py --help
```

Para una ruta host-only, este es un ejemplo. `192.168.56.101` debe sustituirse por
la dirección que Kali realmente tenga; nunca asignarla solo porque está en el ejemplo.

```bash
python3 lab_receiver.py --bind 192.168.56.101 --allowed-client 192.168.56.1 --log receptor-piloto-01.jsonl
```

El receptor escucha en 8765/TCP. En una IP privada exige el origen permitido.
No usar `sudo` para este puerto. Mantener esa terminal abierta; Ctrl+C lo detiene.
El log debe ser nuevo por lote: si ya existe, elegir otro nombre para no mezclar
ni sobrescribir registros. Guarda códigos, tiempos UTC y bytes sintéticos;
descarta el cuerpo y no registra direcciones ni contenido.

Comprobar también la sincronización horaria de Windows y Kali antes de correlacionar
las mediciones. En Kali, `timedatectl status` muestra el estado del reloj. La
exportación de la app y el log del receptor usan UTC, incluso si las zonas horarias
visibles son distintas.

## Verificar el acceso desde Windows

En PowerShell, sustituyendo `IP_KALI`:

```powershell
Test-NetConnection -ComputerName IP_KALI -Port 8765 -InformationLevel Detailed
```

Revisar `TcpTestSucceeded: True` y `SourceAddress`. Esta prueba verifica el puerto;
no genera una acción del laboratorio ni valida el recorrido por la VPN.
Si el origen difiere de `--allowed-client`, corregirlo y reiniciar el receptor con
un log nuevo. Si falla, comprobar dirección, adaptador y que el receptor esté activo.
Si hay firewall en Kali, revisar sus reglas y limitar cualquier apertura necesaria
a 8765/TCP desde la IP de origen del relé. No desactivar todo el firewall.

## Primera acción desde el Honor

1. Mantener Kali y el receptor activos, y los servicios de Windows funcionando.
2. Iniciar una captura completa en la app y desplegar Laboratorio de pruebas.
3. Escribir la IP del receptor en Kali, distinta de la IP del relé excluida del túnel.
4. Elegir Ráfaga acotada y pulsar Generar tráfico de prueba. Mantener la pantalla abierta.
5. Anotar el código mostrado y las acciones recibidas. El perfil programa 20 acciones;
   comprobar cuántas llegaron realmente, sin asumir que deben ser 20.
6. Completar unos 60 segundos de captura, finalizar, guardar y exportar CSV y JSON.
7. Comparar el código con el archivo `receptor-piloto-01.jsonl` de Kali.

La primera ejecución es un control de recepción y medición. Una ráfaga legítima
puede activar el mismo patrón que una simulación de abuso. No asignarle riesgo
alto ni declararla amenaza confirmada por el solo hecho de haberla generado.

Si el archivo tiene exactamente las 20 acciones de ese código, sin otras acciones,
eso confirma su recepción. El total general de conexiones de la app puede ser mayor
porque incluye tráfico de fondo. Los 20 registros tampoco equivalen a 20 amenazas.

Después comprobar Conexiones distribuidas, Periódicas y Subida legítima en capturas
separadas. Conservar los registros y exports para preparar las referencias. Suricata
y el entrenamiento de ML se preparan después de verificar esta ruta.

## Verificación del receptor autónomo

Se probó en Windows arrancando este archivo con Python aislado y sin paquetes de
terceros, enviando una acción sintética y comprobando su registro. También se
comprobó el rechazo de configuración inválida o de enlace privado sin origen
permitido. La conectividad y ejecución en Kali siguen pendientes de comprobación.

[Documentación oficial de redes de VirtualBox](https://docs.oracle.com/en/virtualization/virtualbox/7.2/user/networkingdetails.html)
describe host-only, NAT y puente.

[Volver a la implementación experimental](IMPLEMENTACION.md).
