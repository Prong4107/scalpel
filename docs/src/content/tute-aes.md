---
title: "Decrypting custom encryption."
menu:
    tutes:
        weight: 2
---

# Decrypting custom encryption.

## Problem

An IOT appliance adds an obfuscation layer to its HTTP communications by encrypting the body of its requests and responses with a key.

On every HTTP request, the program sends two POST parameters, secret (the encryption key) and encrypted the ciphertext.

We will use scalpel to provide an additional tab in the repeater which displays the plaintext for each request and response.

Additionally, we'll be able to edit the plaintext, and scalpel will automatically encrypt it when we hit "Send".

We added a mock API to test this case in the Scalpel repository at `test/server.js`

## Taking a look at the target:

Lets take a first look at our API code:

```ts
const { urlencoded } = require("express");

const app = require("express")();

app.use(urlencoded({ extended: true }));

const crypto = require("crypto");

const derive = (secret) => {
	const hasher = crypto.createHash("sha256");
	hasher.update(secret);
	const derived_aes_key = hasher.digest().slice(0, 32);
	return derived_aes_key;
};

const get_cipher_decrypt = (secret, iv = Buffer.alloc(16, 0)) => {
	const derived_aes_key = derive(secret);
	const cipher = crypto.createDecipheriv("aes-256-cbc", derived_aes_key, iv);
	return cipher;
};

const get_cipher_encrypt = (secret, iv = Buffer.alloc(16, 0)) => {
	const derived_aes_key = derive(secret);
	const cipher = crypto.createCipheriv("aes-256-cbc", derived_aes_key, iv);
	return cipher;
};

const decrypt = (secret, data) => {
	const decipher = get_cipher_decrypt(secret);
	let decrypted = decipher.update(data, "base64", "utf8");
	decrypted += decipher.final("utf8");
	return decrypted;
};

const encrypt = (secret, data) => {
	const cipher = get_cipher_encrypt(secret);
	let encrypted = cipher.update(data, "utf8", "base64");
	encrypted += cipher.final("base64");
	return encrypted;
};

app.post("/encrypt", (req, res) => {
	const secret = req.body["secret"];
	const data = req.body["encrypted"];

	if (data === undefined) {
		res.send("No content");
		return;
	}

	const decrypted = decrypt(secret, data);
	const resContent = `You have sent "${decrypted}" using secret "${secret}"`;
	const encrypted = encrypt(secret, resContent);

	res.send(encrypted);
});

app.listen(3000, ["localhost"]);
```

As we can see, every request content is encrypted using AES using a secret passed alongside the content and the response is encrypted using the same provided secret.

With Burp vanilla, it would make editing the request very tedious (using "copy to file"), and when faced against a case like this, people will either work with custom scripts outside of Burp, use tools like [mitmproxy](https://docs.mitmproxy.org/stable/), write their own Burp Extension in Java for this specific use case (which is slow) or give up.

Scalpel's main goal and reason to exist is to make working around such cases trivial.

## Reimplementing the encryption / decryption.

To use Scalpel for handling this API's encryption, we first have to reimplement the encryption process in Python.

### Installing Python dependencies

To work with AES in Python, we need the `pycryptodome` module, which isn't installed by default.
All Scalpel python scripts run in a virtual env, and Scalpel provides a way to switch venvs and install packages through Burp GUI.

Let's jump to the "Scalpel" tab:
{{< figure src="/screenshots/terminal.png" >}}

On the left, you can see UI you can use to create and select new venvs.
{{< figure src="/screenshots/venv.png" >}}

Let's create a venv for this use case by entering a name and pressing enter:
{{< figure src="/screenshots/aes-venv.png" >}}
{{< figure src="/screenshots/venv-installing.png" >}}

We can now select our venv by clicking on it:
{{< figure src="/screenshots/select-venv.png" >}}

The central terminal is now activated in the selected venv and can be used to install packages using pip in the usual way:
{{< figure src="/screenshots/venv-pycryptodome.png" >}}

We now have pycryptodome installed and we're ready to code our scalpel script.

---

With pycryptodome, we can reimplement the encryption in Python like this:

```python
from Crypto.Cipher import AES
from Crypto.Hash import SHA256
from Crypto.Util.Padding import pad, unpad
from base64 import b64encode, b64decode

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
```

## Creating custom editors

We can now use our above code to automatically decrypt our content to plaintext and re-encrypt a modified plaintext.

As you should have seen in [Usage]({{< relref "overview-usage" >}}), request editors are created by declaring the `req_edit_in` hook:

```python
def req_edit_in_encrypted(req: Request) -> bytes | None:
    ...
```

Here, we added the "\_encrypted" suffix to the hook name to give the title "encrypted" to the tab.

This hook is called when Burp opens the request in an editor, it receives the request to edit and returns the bytes to display in the editor.

-   We want to display the plain text, so we:

    -   Get the secret and the encrypted content from the body
    -   Decrypt the content using the secret
    -   Return the decrypted bytes.

    ```python
    from pyscalpel.http import Request, Response, Flow

    def req_edit_in_encrypted(req: Request) -> bytes | None:
        secret = req.form[b"secret"]
        encrypted = req.form[b"encrypted"]
        if not encrypted:
            return b""

        return decrypt(secret, encrypted)
    ```

Now, after loading this script with Scalpel and opening such an encrypted request in Burp, you should see a "Scalpel" tab along the "Pretty", "Raw", and "Hex" tabs:
{{< figure src="/screenshots/encrypty-scalpel-tab.png" >}}
{{< figure src="/screenshots/encrypt-tab-selected.png" >}}

Right-now, the additional tab is uneditable as it has no way to encrypt the content back. To do so, we need to implement the `req_edit_out` hook.

-   The `req_edit_out` simply has to implement the inverse effect of `req_edit_in`, which means:
    -   Encrypt the plain text using the secret
    -   Replace the old encrypted content in the request
    -   Return the new request.
    ```python
    def req_edit_out_encrypted(req: Request, text: bytes) -> Request:
      secret = req.form[b"secret"]
      req.form[b"encrypted"] = encrypt(secret, text)
      return req
    ```
    > When present, the req_edit_out suffix has to match the req_edit_in suffix (here: `_encrypted`)

By adding this hook, you should now be able to edit the plain text and it will automatically be encrypted using the hook you just implemented.
{{< figure src="/screenshots/encrypt-edited.png" >}}

Now, we would like to be able to decrypt the response to see if our changes were reflected.

The process is basically the same:

```python
def res_edit_in_encrypted(res: Response) -> bytes | None:
    secret = res.request.form[b"secret"]
    encrypted = res.content

    if not encrypted:
        return b""

    return decrypt(secret, encrypted)

# This is used to edit the response received by the browser in the proxy, but is useless in Repeater/Logger.
def res_edit_out_encrypted(res: Response, text: bytes) -> Response:
    secret = res.request.form[b"secret"]
    res.content = encrypt(secret, text)
    return res
```

    {{< figure src="/screenshots/decrypted-response.png" >}}

You can now edit the responses received by the browser as well.

### Filtering requests/responses sent to hooks.

Scalpel provides a [match()]({{< relref "addons-api#match" >}}) hook to filter unwanted requests from being treated by your hooks.

In this case, the encrypted requests are only sent to the `/encrypt` path and contains a `secret`, so we shouldn't try to decrypt traffic that do not match this.

```python
from pyscalpel.http import Request, Response, Flow

def match(flow: Flow) -> bool:
    return flow.path_is("/encrypt*") and flow.request.form.get(b"secret") is not None
```

# Conclusion

In this tutorial, we've taken a look at how to decrypt a custom encryption in IoT appliance communications using Scalpel.
This involved understanding the existing API encryption code, recreating the encryption process in Python, installing necessary Python dependencies, and creating custom editors to handle decryption and re-encryption of modified content.

We implemented this process for both request and response flows, allowing us to view and manipulate the plaintext communication, then re-encrypt it before sending. This approach considerably simplifies the process of analyzing and interacting with encrypted data, reducing the need for cumbersome workarounds or additional external tools.

Remember, although we've used a specific case of AES-256-CBC encryption in this tutorial, the general principle and steps can be applied to various other encryption techniques as well. The main requirement is to understand the encryption process and be able to reproduce it in Python.

Scalpel is meant to be a versatile tool in scenarios where we encounter custom encryption, making it easier to analyze and modify such encrypted data for security testing purposes.
