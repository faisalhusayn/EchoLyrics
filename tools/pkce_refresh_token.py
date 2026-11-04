#!/usr/bin/env python3
"""Complete Spotify Authorization Code + PKCE on a computer and print a refresh token."""
import base64, hashlib, http.server, json, secrets, socketserver, urllib.parse, urllib.request, webbrowser

CLIENT_ID = input("Spotify client ID: ").strip()
REDIRECT = "http://127.0.0.1:8765/callback"
verifier = base64.urlsafe_b64encode(secrets.token_bytes(32)).rstrip(b"=").decode()
challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
query = urllib.parse.urlencode({"client_id": CLIENT_ID, "response_type": "code", "redirect_uri": REDIRECT, "code_challenge_method": "S256", "code_challenge": challenge, "scope": "user-read-currently-playing user-read-playback-state"})
result = {}
class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        result.update(urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)); self.send_response(200); self.end_headers(); self.wfile.write(b"You may close this window.")
    def log_message(self, *_): pass
print("Register http://127.0.0.1:8765/callback temporarily in the Spotify dashboard.")
webbrowser.open("https://accounts.spotify.com/authorize?" + query)
with socketserver.TCPServer(("127.0.0.1", 8765), Handler) as server: server.handle_request()
code = result["code"][0]
data = urllib.parse.urlencode({"client_id": CLIENT_ID, "grant_type": "authorization_code", "code": code, "redirect_uri": REDIRECT, "code_verifier": verifier}).encode()
request = urllib.request.Request("https://accounts.spotify.com/api/token", data=data, method="POST")
with urllib.request.urlopen(request) as response: token = json.load(response)["refresh_token"]
print("\nRefresh token (push with adb):\n" + token)
