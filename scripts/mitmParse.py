
rawReq = """HTTP/1.1 200 OK
Access-Control-Allow-Origin: *
Content-Type: text/plain; charset=utf-8
Content-Length: 116
Date: Fri, 10 Feb 2023 17:11:38 GMT
X-Envoy-Upstream-Service-Time: 3
Strict-Transport-Security: max-age=2592000; includeSubDomains
Server: istio-envoy
Via: 1.1 google
Alt-Svc: h3=":443"; ma=2592000,h3-29=":443"; ma=2592000
"""

from mitmproxy.net.http.http1.read import read_response_head


lines = [bytes(x, "utf-8") for x in rawReq.splitlines()]
var = read_response_head(lines)

var.set_text("lol")
print(var)

print(var.headers)
print(var.content)

print("---------------")

print(str(var))