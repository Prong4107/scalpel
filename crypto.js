const crypto = require('crypto');

const get_cipher = (secret, iv = Buffer.alloc(16, 0)) => {
    const hasher = crypto.createHash('sha256');
    hasher.update(secret);
    const derived_aes_key = hasher.digest().slice(0, 32);
    const cipher = crypto.createDecipheriv('aes-256-cbc', derived_aes_key, iv);
    return cipher;
}

const decrypt = (secret, data) => {
    const decipher = get_cipher(secret);
    let decrypted = decipher.update(data, 'base64', 'utf8');
    decrypted += decipher.final('utf8');
    return decrypted;
}

const secret = "MySecretKey";
const encryptedString = "DNTXPKEjAadeLnKNveSP5Q==";

console.log(decrypt(secret, encryptedString));
