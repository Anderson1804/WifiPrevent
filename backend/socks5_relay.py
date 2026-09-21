"""Local SOCKS5 TCP relay used by the WiFiPrevent development tunnel."""

import argparse
import asyncio
import ipaddress
import logging
import socket


LOGGER = logging.getLogger("wifiprevent.socks5")
SOCKS_VERSION = 5


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


async def handle_client(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    upstream_writer = None
    try:
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
        if command != 1:
            await send_reply(writer, 7)
            return
        try:
            host = await read_destination(reader, address_type)
        except (ValueError, UnicodeError):
            await send_reply(writer, 8)
            return
        port = int.from_bytes(await reader.readexactly(2), "big")

        try:
            upstream_reader, upstream_writer = await asyncio.wait_for(
                asyncio.open_connection(host, port), timeout=10
            )
        except (OSError, asyncio.TimeoutError):
            await send_reply(writer, 5)
            return

        await send_reply(writer, 0, upstream_writer.get_extra_info("sockname"))
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


async def serve(host: str, port: int) -> None:
    server = await asyncio.start_server(handle_client, host, port)
    addresses = ", ".join(str(sock.getsockname()) for sock in server.sockets or [])
    LOGGER.info("Relé SOCKS5 activo en %s", addresses)
    async with server:
        await server.serve_forever()


def main() -> None:
    parser = argparse.ArgumentParser(description="WiFiPrevent local SOCKS5 relay")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=1080)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    try:
        asyncio.run(serve(args.host, args.port))
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
