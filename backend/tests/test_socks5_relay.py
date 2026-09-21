import asyncio
import socket
import threading
import socketserver

from socks5_relay import handle_client


def receive_exact(connection, size):
    data = b""
    while len(data) < size:
        part = connection.recv(size - len(data))
        if not part:
            raise ConnectionError("connection closed")
        data += part
    return data


class AsyncServerThread:
    def __init__(self, handler):
        self.handler = handler
        self.ready = threading.Event()
        self.port = None

    def __enter__(self):
        self.thread = threading.Thread(target=self.run, daemon=True)
        self.thread.start()
        assert self.ready.wait(3)
        return self

    def run(self):
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)
        self.server = self.loop.run_until_complete(
            asyncio.start_server(self.handler, "127.0.0.1", 0)
        )
        self.port = self.server.sockets[0].getsockname()[1]
        self.ready.set()
        self.loop.run_forever()
        self.server.close()
        self.loop.run_until_complete(self.server.wait_closed())
        self.loop.close()

    def __exit__(self, *_):
        self.loop.call_soon_threadsafe(self.loop.stop)
        self.thread.join(3)


async def echo(reader, writer):
    try:
        while data := await reader.read(4096):
            writer.write(data)
            await writer.drain()
    finally:
        writer.close()
        await writer.wait_closed()


def negotiate(connection):
    connection.sendall(b"\x05\x01\x00")
    assert receive_exact(connection, 2) == b"\x05\x00"


def test_connect_relays_tcp_without_inspecting_payload():
    with AsyncServerThread(echo) as destination, AsyncServerThread(handle_client) as relay:
        with socket.create_connection(("127.0.0.1", relay.port), timeout=3) as connection:
            negotiate(connection)
            request = b"\x05\x01\x00\x01" + socket.inet_aton("127.0.0.1")
            connection.sendall(request + destination.port.to_bytes(2, "big"))
            reply = receive_exact(connection, 10)
            assert reply[1] == 0
            payload = b"wifiprevent relay test"
            connection.sendall(payload)
            assert receive_exact(connection, len(payload)) == payload


def test_rejects_authentication_methods_other_than_no_auth():
    with AsyncServerThread(handle_client) as relay:
        with socket.create_connection(("127.0.0.1", relay.port), timeout=3) as connection:
            connection.sendall(b"\x05\x01\x02")
            assert receive_exact(connection, 2) == b"\x05\xff"


def test_rejects_unsupported_commands():
    with AsyncServerThread(handle_client) as relay:
        with socket.create_connection(("127.0.0.1", relay.port), timeout=3) as connection:
            negotiate(connection)
            connection.sendall(b"\x05\x02\x00\x01\x7f\x00\x00\x01\x00\x50")
            assert receive_exact(connection, 10)[1] == 7


class UdpEchoHandler(socketserver.BaseRequestHandler):
    def handle(self):
        data, connection = self.request
        connection.sendto(data, self.client_address)


def test_udp_associate_relays_datagrams_without_storing_payload():
    with socketserver.ThreadingUDPServer(("127.0.0.1", 0), UdpEchoHandler) as destination:
        destination_thread = threading.Thread(
            target=destination.serve_forever, daemon=True
        )
        destination_thread.start()
        try:
            with AsyncServerThread(handle_client) as relay:
                with socket.create_connection(
                    ("127.0.0.1", relay.port), timeout=3
                ) as control:
                    negotiate(control)
                    control.sendall(
                        b"\x05\x03\x00\x01\x00\x00\x00\x00\x00\x00"
                    )
                    reply = receive_exact(control, 10)
                    assert reply[1] == 0
                    relay_port = int.from_bytes(reply[-2:], "big")
                    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp:
                        udp.settimeout(3)
                        payload = b"wifiprevent udp test"
                        header = (
                            b"\x00\x00\x00\x01" +
                            socket.inet_aton("127.0.0.1") +
                            destination.server_address[1].to_bytes(2, "big")
                        )
                        udp.sendto(header + payload, ("127.0.0.1", relay_port))
                        response, _ = udp.recvfrom(4096)
                        assert response[10:] == payload
        finally:
            destination.shutdown()
            destination.server_close()
            destination_thread.join(3)
