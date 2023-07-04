# Scalpel

Python scripting Burp extension.

## Install:

-   openjdk >= 17
-   python >= 3.10
-   pip

1):

```bash
pip install jep
```

2): Import scalpel .jar in Burp.

## Build:

### Requirements:

-   \> JDK 17
-   \> python3.10
-   JEP (`pip install jep`)
-   [Visual C++ >=14.0 build tools](https://visualstudio.microsoft.com/fr/visual-cpp-build-tools/) (Windows only)

---

```bash
./gradlew build
```

Will generate `scalpel/build/libs/scalpel-1.0.0.jar` which can be imported in Burp.

---

## Testing:

You can use the ExpressJS test server to get back your request:

```Bash
cd test/
npm i
node server.js # Listens on port 3000
```
