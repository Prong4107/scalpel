---
title: "Decrypting custom encryption"
menu:
    tutes:
        weight: 2
---

# Decrypting custom encryption

## Context

An IOT appliance adds an obfuscation layer to its HTTP communications by encrypting the body of its requests and responses with a key.

On every HTTP request, the program sends two POST parameters, `secret` (the encryption key) and `encrypted` (the ciphertext).

Let's solve this problem by using Scalpel. It will provide an additional tab in the Repeater which displays the plaintext for every request and response.

Also, the plaintext can be edited. Scalpel will automatically encrypt it when the "Send" button is hit.

Find a mock API to test this case in the GitHub repository: [`test/server.js`](https://github.com/Prong4107/scalpel/blob/4b935cb29b496f3627a319d963a009dda79a1aa7/test/server.js#L117C1-L118C1).
<!-- ^^ TODO: Add link to test -->

## Table of content

1. [Take a look at the target](#1-take-a-look-at-the-target)
2. [Reimplement the encryption / decryption](#2-reimplement-the-encryption--decryption)
3. [Create the script using Scalpel](#3-create-the-script-using-scalpel)
4. [Implement the encryption algorithm](#4-implement-the-encryption-algorithm)


## 1. Take a look at the target

Take the time to get familiar with the API code:

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

As shown above, every request content is encrypted using AES, using a secret passed alongside the content, that also encrypt the response.

In vanilla Burp, editing the request would be very tedious (using `copy to file`). When faced against a case like this, users will either work with custom scripts outside of Burp, use tools like [mitmproxy](https://docs.mitmproxy.org/stable/), write their own Burp Java extension, or give up.

Scalpel's main objective is to make working around such cases trivial.

## 2. Reimplement the encryption / decryption

Before using Scalpel for handling this API's encryption, the first thing to do is to implement the encryption process in Python.

### Installing Python dependencies

To work with AES in Python, the `pycryptodome` module is required but not installed by default.
All Scalpel Python scripts run in a virtual environment. Fortunately, Scalpel provides a way to switch between venvs and install packages through Burp GUI.

Let's jump to the `Scalpel` tab:
{{< figure src="/screenshots/terminal.png" >}}

Focus on the left part. You can use this interface to create and select new venvs.
{{< figure src="/screenshots/venv.png" >}}

Let's create a venv for this use case. Enter a name and press enter:
{{< figure src="/screenshots/aes-venv.png" >}}
{{< figure src="/screenshots/venv-installing.png" >}}

It is now possible to select it by clicking on its path:
{{< figure src="/screenshots/select-venv.png" >}}

The central terminal is now activated in the selected venv and can be used to install packages using `pip` in the usual way:
{{< figure src="/screenshots/venv-pycryptodome.png" >}}

`pycryptodome` is now installed. Let's create the Scalpel script!

## 3. Create the script using Scalpel

You can create a new script for Scalpel using the GUI:

-   Click the `Create new script` button (underlined in red below).
    -   {{< figure src="/screenshots/create-script.png" >}}
-   Enter the desired filename.
    -   {{< figure src="/screenshots/create-script-prompt.png" >}}
-   Once the file is created, this message will show up:
    -   {{< figure src="/screenshots/create-script-success.png" >}}

After following this steps, the script should either be opened in your OS graphical editor or in the terminal provided by Scalpel:
{{< figure src="/screenshots/create-script-edit.png" >}}

It contains commented hooks declarations. Remove them, as you will rewrite them further in this tutorial.

## 4. Implement the encryption algorithm

With `pycryptodome`, the encryption can be written in Python like this:

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

## 5. Create custom editors

The above code can now be used to automatically decrypt your content to plaintext and re-encrypt a modified plaintext.

As explained in [Usage]({{< relref "overview-usage" >}}), request editors are created by declaring the `req_edit_in` hook:

```python
def req_edit_in_encrypted(req: Request) -> bytes | None:
    ...
```

Here, the `_encrypted` suffix was added to the hook name, creating a tab named "encrypted".

This hook is called when Burp opens the request in an editor. It receives the request to edit and returns the bytes to display in the editor.

-   In order to display the plain text, the following must be done:

    -   Get the secret and the encrypted content from the body.
    -   Decrypt the content using the secret.
    -   Return the decrypted bytes.

    ```python
    from pyscalpel import Request, Response, Flow

    def req_edit_in_encrypted(req: Request) -> bytes | None:
        secret = req.form[b"secret"]
        encrypted = req.form[b"encrypted"]
        if not encrypted:
            return b""

        return decrypt(secret, encrypted)
    ```

Once this script is loaded with Scalpel, if you open an encrypted request in Burp, you will see a `Scalpel` tab along the `Pretty`, `Raw`, and `Hex` tabs:
{{< figure src="/screenshots/encrypty-scalpel-tab.png" >}}
{{< figure src="/screenshots/encrypt-tab-selected.png" >}}

But there is an issue. Right now, the additional tab cannot be edited since it has no way to encrypt the content back. To do so, the `req_edit_out` hook will be handful.

-   The `req_edit_out` hook has to implement the opposite behavior of `req_edit_in`, which means:
    -   Encrypt the plain text using the secret.
    -   Replace the old encrypted content in the request.
    -   Return the new request.
    ```python
    def req_edit_out_encrypted(req: Request, text: bytes) -> Request:
      secret = req.form[b"secret"]
      req.form[b"encrypted"] = encrypt(secret, text)
      return req
    ```
    > When present, the `req_edit_out` suffix **must match** the `req_edit_in` suffix.
    > In this tutorial example, the suffix is: `_encrypted`

Add the hook. You should now be able to edit the plaintext. It will automatically be encrypted using `req_edit_out_encrypted`.
{{< figure src="/screenshots/encrypt-edited.png" >}}

After that, it would be nice to decrypt the response to see if the changes were reflected.

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

In this case, the encrypted requests are only sent to the `/encrypt` path and contain a `secret`. Thus, better not try to decrypt traffic that don't match these conditions.

```python
from pyscalpel import Request, Response, Flow

def match(flow: Flow) -> bool:
    return flow.path_is("/encrypt*") and flow.request.form.get(b"secret") is not None
```

The above `match` hook receives a [Flow](/api/pyscalpel/http.html#Flow) object. It contains a request. When treating a response, it contains both the response and its initiating request.

It ensures the initiating request contained a `secret` field and was sent to a path matching `/encrypt*`

# Conclusion

In this tutorial, you saw how to decrypt a custom encryption in IoT appliance communications using Scalpel.
This involved: understanding the existing API encryption code, recreating the encryption process in Python, installing necessary Python dependencies, and creating custom editors to handle decryption and re-encryption of modified content.

This process was implemented for both request and response flows, allowing to view and manipulate the plaintext communication, then encrypt it again before sending. This approach greatly simplifies the process of analyzing and interacting with encrypted data, reducing the need for cumbersome work arounds or additional external tools.

While this tutorial covers a specific case of AES-256-CBC encryption, have in mind that the main concept and steps can be applied to various other encryption techniques as well. The only requirement is to understand the encryption process and be able to reproduce it in Python.

Scalpel is meant to be a versatile tool in scenarios where custom encryption is encountered. It aims to make data easier to analyze and modify for security testing purposes.
