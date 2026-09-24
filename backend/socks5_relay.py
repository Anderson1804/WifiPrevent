"""Local SOCKS5 TCP/UDP relay used by the WiFiPrevent development tunnel."""

import argparse
import asyncio
from dataclasses import dataclass
from functools import partial
import hashlib
import hmac
import ipaddress
import json
import logging
import secrets
import socket
from uuid import UUID


LOGGER = logging.getLogger("wifiprevent.socks5")
SOCKS_VERSION = 5


@dataclass(frozen=True)
class RelayMetricsSnapshot:
    tcp_connections: int
    udp_datagrams: int
    dns_observations: int
    http_observations: int
    tls_or_quic_observations: int
    other_observations: int
    unique_destinations: int


class RelayTrafficMetrics:
    """Keep aggregate forwarding observations without retaining destinations."""

    def __init__(self, fingerprint_key: bytes | None = None):
        self._fingerprint_key = fingerprint_key or secrets.token_bytes(32)
        self._tcp_connections = 0
        self._udp_datagrams = 0
        self._dns_observations = 0
        self._http_observations = 0
        self._tls_or_quic_observations = 0
        self._other_observations = 0
        self._destination_fingerprints: set[bytes] = set()
        self._session_id: str | None = None

    def start_session(self, session_id: str) -> None:
        self._session_id = str(UUID(session_id))
        self._tcp_connections = 0
        self._udp_datagrams = 0
        self._dns_observations = 0
        self._http_observations = 0
        self._tls_or_quic_observations = 0
        self._other_observations = 0
        self._destination_fingerprints.clear()

    def observe(self, transport: str, host: str, port: int) -> None:
        if transport == "tcp":
            self._tcp_connections += 1
        elif transport == "udp":
            self._udp_datagrams += 1
        else:
            raise ValueError("unsupported transport")

        if port == 53:
            self._dns_observations += 1
        elif transport == "tcp" and port == 80:
            self._http_observations += 1
        elif port == 443:
            self._tls_or_quic_observations += 1
        else:
            self._other_observations += 1

        normalized_host = host.rstrip(".").lower()
        fingerprint = hmac.new(
            self._fingerprint_key,
            normalized_host.encode("utf-8", errors="replace"),
            hashlib.sha256,
        ).digest()
        self._destination_fingerprints.add(fingerprint)

    def snapshot(self) -> RelayMetricsSnapshot:
        return RelayMetricsSnapshot(
            tcp_connections=self._tcp_connections,
            udp_datagrams=self._udp_datagrams,
            dns_observations=self._dns_observations,
            http_observations=self._http_observations,
            tls_or_quic_observations=self._tls_or_quic_observations,
            other_observations=self._other_observations,
            unique_destinations=len(self._destination_fingerprints),
        )

    def session_snapshot(self, session_id: str) -> RelayMetricsSnapshot | None:
        normalized = str(UUID(session_id))
        return self.snapshot() if normalized == self._session_id else None


RELAY_METRICS = RelayTrafficMetrics()
CONTROL_HOST = "127.0.0.1"
CONTROL_PORT = 1081
UDP_RELAY_PORT = 1081


async def send_http_json(
        writer: asyncio.StreamWriter,
        status: int,
        payload: dict,
) -> None:
    body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    reason = {200: "OK", 201: "Created", 400: "Bad Request", 404: "Not Found"}[status]
    writer.write(
        f"HTTP/1.1 {status} {reason}\r\n"
        f"Content-Type: application/json\r\n"
        f"Content-Length: {len(body)}\r\n"
        "Connection: close\r\n\r\n".encode("ascii") + body
    )
    await writer.drain()


async def handle_control_client(
        reader: asyncio.StreamReader,
        writer: asyncio.StreamWriter,
        metrics: RelayTrafficMetrics = RELAY_METRICS,
) -> None:
    try:
        header = await reader.readuntil(b"\r\n\r\n")
        if len(header) > 4096:
            await send_http_json(writer, 400, {"detail": "invalid request"})
            return
        request_line = header.split(b"\r\n", 1)[0].decode("ascii")
        method, path, version = request_line.split(" ")
        if version != "HTTP/1.1" or not path.startswith("/sessions/"):
            await send_http_json(writer, 404, {"detail": "not found"})
            return
        parts = path.strip("/").split("/")
        if len(parts) not in (2, 3) or parts[0] != "sessions":
            await send_http_json(writer, 404, {"detail": "not found"})
            return
        session_id = str(UUID(parts[1]))
        if method == "POST" and parts[2:] == ["start"]:
            metrics.start_session(session_id)
            await send_http_json(writer, 201, {"session_id": session_id, "status": "ready"})
            return
        if method == "GET" and len(parts) == 2:
            snapshot = metrics.session_snapshot(session_id)
            if snapshot is None:
                await send_http_json(writer, 404, {"detail": "session not active"})
                return
            await send_http_json(
                writer,
                200,
                {"session_id": session_id, **snapshot.__dict__},
            )
            return
        await send_http_json(writer, 404, {"detail": "not found"})
    except (ValueError, UnicodeError, asyncio.IncompleteReadError, asyncio.LimitOverrunError):
        await send_http_json(writer, 400, {"detail": "invalid request"})
    finally:
        writer.close()
        try:
            await writer.wait_closed()
        except ConnectionError:
            pass


async def read_destination(reader: asyncio.StreamReader, address_type: int) -> str:
    if address_type == 1:
        return str(ipaddress.IPv4Address(await reader.readexactly(4)))
    if address_type == 3:
        length = (await reader.readexactly(1))[0]
        return (await reader.readexactly(length)).decode("idna")
    if address_type == 4:
        return str(ipaddress.IPv6Address(await reader.readexactly(16)))
    raise ValueError("unsupported address type")


async def send_reply(writer: asyncio.StreamWriter, status: int, bound=None) -> None:
    host, port = ("0.0.0.0", 0) if bound is None else bound[:2]
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        address = ipaddress.ip_address("0.0.0.0")
    address_type = 1 if address.version == 4 else 4
    writer.write(bytes((SOCKS_VERSION, status, 0, address_type)))
    writer.write(address.packed)
    writer.write(int(port).to_bytes(2, "big"))
    await writer.drain()


async def copy_stream(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    try:
        while data := await reader.read(64 * 1024):
            writer.write(data)
            await writer.drain()
    except (ConnectionError, asyncio.CancelledError):
        pass
    finally:
        try:
            writer.write_eof()
        except (AttributeError, ConnectionError, OSError):
            pass


def encode_udp_address(host: str, port: int) -> bytes:
    address = ipaddress.ip_address(host)
    address_type = 1 if address.version == 4 else 4
    return bytes((0, 0, 0, address_type)) + address.packed + port.to_bytes(2, "big")


def decode_udp_request(data: bytes):
    if len(data) < 4 or data[:2] != b"\x00\x00" or data[2] != 0:
        return None
    address_type = data[3]
    offset = 4
    try:
        if address_type == 1:
            if len(data) < offset + 6:
                return None
            host = str(ipaddress.IPv4Address(data[offset:offset + 4]))
            offset += 4
        elif address_type == 4:
            if len(data) < offset + 18:
                return None
            host = str(ipaddress.IPv6Address(data[offset:offset + 16]))
            offset += 16
        elif address_type == 3:
            if len(data) < offset + 1:
                return None
            length = data[offset]
            offset += 1
            if len(data) < offset + length + 2:
                return None
            host = data[offset:offset + length].decode("idna")
            offset += length
        else:
            return None
        port = int.from_bytes(data[offset:offset + 2], "big")
        return host, port, data[offset + 2:]
    except (ValueError, UnicodeError):
        return None


class UdpAssociation(asyncio.DatagramProtocol):
    def __init__(self, allowed_client_host: str, metrics: RelayTrafficMetrics):
        self.allowed_client_host = allowed_client_host
        self.metrics = metrics
        self.client = None
        self.transport = None

    def connection_made(self, transport):
        self.transport = transport

    def datagram_received(self, data: bytes, address):
        if address == self.client or (
            self.client is None and address[0] == self.allowed_client_host
        ):
            request = decode_udp_request(data)
            if request is None:
                return
            self.client = address
            host, port, payload = request
            self.metrics.observe("udp", host, port)
            self.transport.sendto(payload, (host, port))
            return
        if self.client is not None:
            try:
                response = encode_udp_address(address[0], address[1]) + data
            except ValueError:
                return
            self.transport.sendto(response, self.client)


async def handle_client(
        reader: asyncio.StreamReader,
        writer: asyncio.StreamWriter,
        metrics: RelayTrafficMetrics = RELAY_METRICS,
        allowed_client_hosts: frozenset[str] | None = None,
) -> None:
    upstream_writer = None
    try:
        peer_host = writer.get_extra_info("peername")[0]
        if allowed_client_hosts is not None and peer_host not in allowed_client_hosts:
            return
        version, method_count = await reader.readexactly(2)
        methods = await reader.readexactly(method_count)
        if version != SOCKS_VERSION or 0 not in methods:
            writer.write(bytes((SOCKS_VERSION, 0xFF)))
            await writer.drain()
            return
        writer.write(bytes((SOCKS_VERSION, 0)))
        await writer.drain()

        version, command, reserved, address_type = await reader.readexactly(4)
        if version != SOCKS_VERSION or reserved != 0:
            await send_reply(writer, 1)
            return
        if command not in (1, 3):
            await send_reply(writer, 7)
            return
        try:
            host = await read_destination(reader, address_type)
        except (ValueError, UnicodeError):
            await send_reply(writer, 8)
            return
        port = int.from_bytes(await reader.readexactly(2), "big")

        if command == 3:
            loop = asyncio.get_running_loop()
            client_host = writer.get_extra_info("peername")[0]
            local_host = writer.get_extra_info("sockname")[0]
            transport, _ = await loop.create_datagram_endpoint(
                lambda: UdpAssociation(client_host, metrics),
                local_addr=(local_host, UDP_RELAY_PORT),
            )
            try:
                await send_reply(writer, 0, transport.get_extra_info("sockname"))
                LOGGER.info("Asociación UDP iniciada")
                await reader.read()
            finally:
                transport.close()
            return

        try:
            upstream_reader, upstream_writer = await asyncio.wait_for(
                asyncio.open_connection(host, port), timeout=10
            )
        except (OSError, asyncio.TimeoutError):
            await send_reply(writer, 5)
            return

        await send_reply(writer, 0, upstream_writer.get_extra_info("sockname"))
        metrics.observe("tcp", host, port)
        LOGGER.info("Conexión TCP reenviada")
        tasks = {
            asyncio.create_task(copy_stream(reader, upstream_writer)),
            asyncio.create_task(copy_stream(upstream_reader, writer)),
        }
        _, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
        for task in pending:
            task.cancel()
        await asyncio.gather(*pending, return_exceptions=True)
    except (asyncio.IncompleteReadError, ConnectionError, OSError):
        pass
    finally:
        if upstream_writer is not None:
            upstream_writer.close()
            await upstream_writer.wait_closed()
        writer.close()
        try:
            await writer.wait_closed()
        except ConnectionError:
            pass


async def serve(
        host: str,
        port: int,
        allowed_client_hosts: frozenset[str] | None = None,
) -> None:
    handler = partial(handle_client, allowed_client_hosts=allowed_client_hosts)
    server = await asyncio.start_server(handler, host, port)
    control_server = await asyncio.start_server(
        handle_control_client, CONTROL_HOST, CONTROL_PORT
    )
    addresses = ", ".join(str(sock.getsockname()) for sock in server.sockets or [])
    LOGGER.info("Relé SOCKS5 activo en %s", addresses)
    LOGGER.info("Control de métricas activo en %s:%s", CONTROL_HOST, CONTROL_PORT)
    async with server, control_server:
        await asyncio.gather(server.serve_forever(), control_server.serve_forever())


def main() -> None:
    parser = argparse.ArgumentParser(description="WiFiPrevent local SOCKS5 relay")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=1080)
    parser.add_argument("--allowed-client", action="append", default=[])
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    try:
        allowed_clients = frozenset({"127.0.0.1", "::1", *args.allowed_client})
        asyncio.run(serve(args.host, args.port, allowed_clients))
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
