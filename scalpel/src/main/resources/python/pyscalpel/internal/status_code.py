# https://github.com/mitmproxy/mitmproxy/blob/main/mitmproxy/net/http/status_codes.py

CONTINUE = 100
SWITCHING = 101
PROCESSING = 102
EARLY_HINTS = 103

OK = 200
CREATED = 201
ACCEPTED = 202
NON_AUTHORITATIVE_INFORMATION = 203
NO_CONTENT = 204
RESET_CONTENT = 205
PARTIAL_CONTENT = 206
MULTI_STATUS = 207

MULTIPLE_CHOICE = 300
MOVED_PERMANENTLY = 301
FOUND = 302
SEE_OTHER = 303
NOT_MODIFIED = 304
USE_PROXY = 305
TEMPORARY_REDIRECT = 307

BAD_REQUEST = 400
UNAUTHORIZED = 401
PAYMENT_REQUIRED = 402
FORBIDDEN = 403
NOT_FOUND = 404
NOT_ALLOWED = 405
NOT_ACCEPTABLE = 406
PROXY_AUTH_REQUIRED = 407
REQUEST_TIMEOUT = 408
CONFLICT = 409
GONE = 410
LENGTH_REQUIRED = 411
PRECONDITION_FAILED = 412
PAYLOAD_TOO_LARGE = 413
REQUEST_URI_TOO_LONG = 414
UNSUPPORTED_MEDIA_TYPE = 415
REQUESTED_RANGE_NOT_SATISFIABLE = 416
EXPECTATION_FAILED = 417
IM_A_TEAPOT = 418
NO_RESPONSE = 444
CLIENT_CLOSED_REQUEST = 499

INTERNAL_SERVER_ERROR = 500
NOT_IMPLEMENTED = 501
BAD_GATEWAY = 502
SERVICE_UNAVAILABLE = 503
GATEWAY_TIMEOUT = 504
HTTP_VERSION_NOT_SUPPORTED = 505
INSUFFICIENT_STORAGE_SPACE = 507
NOT_EXTENDED = 510

RESPONSES = {
    # 100
    CONTINUE: "Continue",
    SWITCHING: "Switching Protocols",
    PROCESSING: "Processing",
    EARLY_HINTS: "Early Hints",
    # 200
    OK: "OK",
    CREATED: "Created",
    ACCEPTED: "Accepted",
    NON_AUTHORITATIVE_INFORMATION: "Non-Authoritative Information",
    NO_CONTENT: "No Content",
    RESET_CONTENT: "Reset Content.",
    PARTIAL_CONTENT: "Partial Content",
    MULTI_STATUS: "Multi-Status",
    # 300
    MULTIPLE_CHOICE: "Multiple Choices",
    MOVED_PERMANENTLY: "Moved Permanently",
    FOUND: "Found",
    SEE_OTHER: "See Other",
    NOT_MODIFIED: "Not Modified",
    USE_PROXY: "Use Proxy",
    # 306 not defined??
    TEMPORARY_REDIRECT: "Temporary Redirect",
    # 400
    BAD_REQUEST: "Bad Request",
    UNAUTHORIZED: "Unauthorized",
    PAYMENT_REQUIRED: "Payment Required",
    FORBIDDEN: "Forbidden",
    NOT_FOUND: "Not Found",
    NOT_ALLOWED: "Method Not Allowed",
    NOT_ACCEPTABLE: "Not Acceptable",
    PROXY_AUTH_REQUIRED: "Proxy Authentication Required",
    REQUEST_TIMEOUT: "Request Time-out",
    CONFLICT: "Conflict",
    GONE: "Gone",
    LENGTH_REQUIRED: "Length Required",
    PRECONDITION_FAILED: "Precondition Failed",
    PAYLOAD_TOO_LARGE: "Payload Too Large",
    REQUEST_URI_TOO_LONG: "Request-URI Too Long",
    UNSUPPORTED_MEDIA_TYPE: "Unsupported Media Type",
    REQUESTED_RANGE_NOT_SATISFIABLE: "Requested Range not satisfiable",
    EXPECTATION_FAILED: "Expectation Failed",
    IM_A_TEAPOT: "I'm a teapot",
    NO_RESPONSE: "No Response",
    CLIENT_CLOSED_REQUEST: "Client Closed Request",
    # 500
    INTERNAL_SERVER_ERROR: "Internal Server Error",
    NOT_IMPLEMENTED: "Not Implemented",
    BAD_GATEWAY: "Bad Gateway",
    SERVICE_UNAVAILABLE: "Service Unavailable",
    GATEWAY_TIMEOUT: "Gateway Time-out",
    HTTP_VERSION_NOT_SUPPORTED: "HTTP Version not supported",
    INSUFFICIENT_STORAGE_SPACE: "Insufficient Storage Space",
    NOT_EXTENDED: "Not Extended",
}
