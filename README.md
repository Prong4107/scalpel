# Scalpel

Python scripting Burp extension.

## Install:

### Requirements:

-   openjdk >= 17
-   python >= 3.10
-   pip
-   [Visual C++ >=14.0 build tools](https://visualstudio.microsoft.com/fr/visual-cpp-build-tools/) (Windows only)
-   [Xcode](https://github.com/ninia/jep/wiki/OS-X) (OS X only)

---

Simply import the Scalpel jar in Burp and it should automatically install it's Python dependencies in a venv.

## Build:

### Requirements:

-   \> JDK 17

---

```bash
./gradlew build
```

Will generate `scalpel/build/libs/scalpel-1.0.0.jar` which can be imported in Burp.

---

## Testing:

You can run the unit tests with run_tests.sh
