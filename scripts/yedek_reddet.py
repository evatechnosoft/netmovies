"""Standby laptop: accept-and-close on :3310 so clients fail fast.

Windows firewall drops SYNs to a closed port silently (stealth mode); the TV
then waits a full timeout per request against the old server and looks frozen
after a switch. Accepting and closing yields an immediate IOException, which
BaseUrlInterceptor treats as "rediscover the server".
"""
import socket
import sys

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 3310

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as srv:
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", PORT))
    srv.listen(64)
    while True:
        conn, _ = srv.accept()
        conn.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, b"\x01\x00\x00\x00\x00\x00\x00\x00")  # RST, not FIN
        conn.close()
