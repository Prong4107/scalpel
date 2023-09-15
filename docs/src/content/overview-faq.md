---
title: "FAQ"
menu: "overview"
menu:
    overview:
        weight: 3
---

# FAQ

## Table of Contents

1. [Why does Scalpel depend on JDK whereas Burp comes with its own JRE?](#why-does-scalpel-depend-on-jdk-whereas-burp-comes-with-its-own-jre)
2. [Why using Java with Jep to execute Python whereas Burp already supports Python extensions with Jython?](#why-using-java-with-jep-to-execute-python-whereas-burp-already-supports-python-extensions-with-jython)
3. [Once the .jar is loaded, no additional request shows up in the editor](#once-the-jar-is-loaded-no-additional-request-shows-up-in-the-editor)
4. [My distribution/OS comes with an outdated python.](#scalpel-requires-python-310-but-my-distribution-is-outdated-and-i-cant-install-such-recent-python-versions-using-the-package-manager)
5. [Why does Scalpel installs mitmproxy?](#why-does-scalpel-installs-mitmproxy)
6. [I installed Python using the Microsoft Store and Scalpel doesn't work.](#i-installed-python-using-the-microsoft-store-and-scalpel-doesnt-work)

---

### Why does Scalpel depend on JDK whereas Burp comes with its own JRE?
-   Scalpel uses a project called [`jep`](https://github.com/ninia/jep/wiki/) to call Python from Java. `jep` needs a JDK to function.
-   If you are curious or need more technical information about Scalpel's implementation, read [How scalpel works]({{< relref "concepts-howscalpelworks" >}}).

### Why using Java with Jep to execute Python whereas Burp already supports Python extensions with [Jython](https://www.jython.org/)?
-   Jython supports up to Python 2.7. Unfortunately, Python 3 is not handled at all. Python 2.7 is basically a dead language and nobody should still be using it.
-   Burp's developers released a [new API](https://portswigger.net/burp/documentation/desktop/extensions/creating) for extensions and deprecated the former one. The new version only supports Java. That's why the most appropriate choice was to reimplement a partial Python scripting support for Burp.

### Once the .jar is loaded, no additional request shows up in the editor!
-   When first installing Scalpel, the installation of all its dependencies may take a while. Look at the "Output" logs in the Burp "Extension" tab to ensure that the extension has completed.
-   Examine the "Errors" logs in the Burp "Extension" tab. There should be an explicit error message with some tips to solve the problem.
-   Make sure you followed the [installation guidelines](../install.md). In case you didn't, remove the `~/.scalpel` directory and try one more time.
-   If the error message doesn't help, please open a GitHub issue including the "Output" and "Errors" logs, and your system information (OS / Distribution version, CPU architecture, JDK and Python version and installation path, environment variables which Burp runs with, and so forth).

### Scalpel requires python >=3.10 but my distribution is outdated and I can't install such recent Python versions using the package manager.
-   Try updating your distribution.
-   If that is not possible, you must setup a separate Python >=3.10 installation and run Burp with the appropriate environment so this separate installation is used.
    > 💡 Tip: Use [`pyenv`](https://github.com/pyenv/pyenv) to easily install different Python versions and switch between them.

### Why does Scalpel installs mitmproxy?
-   Scalpel relies on utilities from the [mitmproxy](https://mitmproxy.org/) package. Thus, in the future, **these utilities may be dropped or rewritten to avoid using such a heavy package**.

### I installed Python using the Microsoft Store and Scalpel doesn't work.
-   The Microsoft Store Python is a sandboxed version designed for educational purposes. Many of its behaviors are incompatible with Scalpel. To use Scalpel on Windows, it is required to install Python from the [official source](https://www.python.org/downloads/windows/).