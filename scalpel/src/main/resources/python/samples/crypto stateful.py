from pyscalpel.http import Request, Response, Flow
from Crypto.Cipher import AES
from Crypto.Hash import SHA256
from Crypto.Util.Padding import pad, unpad
from base64 import b64encode, b64decode


session: bytes = b""


def match(flow: Flow) -> bool:
    return flow.path_is("/encrypt-session*") and bool(
        flow.request.method != "POST" or session
    )


def get_cipher(secret: bytes, iv=bytes(16)):
    hasher = SHA256.new()
    hasher.update(secret)
    derived_aes_key = hasher.digest()[:32]
    cipher = AES.new(derived_aes_key, AES.MODE_CBC, iv)
    return cipher


def decrypt(secret: bytes, data: bytes) -> bytes:
    data = b64decode(data)
    cipher = get_cipher(secret)
    decrypted = cipher.decrypt(data)
    return unpad(decrypted, AES.block_size)


def encrypt(secret: bytes, data: bytes) -> bytes:
    cipher = get_cipher(secret)
    padded_data = pad(data, AES.block_size)
    encrypted = cipher.encrypt(padded_data)
    return b64encode(encrypted)


def response(res: Response) -> Response | None:
    if res.request.method == "GET":
        global session
        session = res.content or b""
        return

    res.content = decrypt(session, res.content)
    return res


def req_edit_in_encrypted(req: Request) -> bytes | None:
    secret = session
    encrypted = req.form[b"encrypted"]
    if not encrypted:
        return b""

    return decrypt(secret, encrypted)


def req_edit_out_encrypted(req: Request, text: bytes) -> Request:
    secret = session
    req.form[b"encrypted"] = encrypt(secret, text)
    return req


def res_edit_in_encrypted(res: Response) -> bytes | None:
    secret = session
    encrypted = res.content

    if not encrypted:
        return b""

    return decrypt(secret, encrypted)


def res_edit_out_encrypted(res: Response, text: bytes) -> Response:
    secret = session
    res.content = encrypt(secret, text)
    return res
