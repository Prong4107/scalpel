# Scalpel
Python scripting Burp extension.

## Build:

### Requirements:
- Gradle
- JDK 17
- python3.10
- JEP (install via pip)

```bash
gradle build
```
Will generate `scalpel/build/libs/scalpel-1.0.0.jar` which can be imported in Burp.

## Runtime requirements:
- Python 3.10
- JEP

JEP uses your user's global Python environnement

Virtual envs are not yet supported :'(
