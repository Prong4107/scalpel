---
title: "FAQ"
menu: "overview"
menu:
    overview:
        weight: 3
---

# FAQ

### Why does Scalpel depend on JDK whereas Burp comes with its own JRE?
-   Scalpel uses a project called [jep](https://github.com/ninia/jep/wiki/) to call Python from Java. jep needs a JDK to function.
-   If you are curious or need more technichal information on Scalpel's implementation, look at [How scalpel works]({{< relref "concepts-howscalpelworks" >}}).

### Once the .jar is loaded, no additional request shows up in the editor!
-   When first installing Scalpel, the installation of all its dependencies may take a while. Look at the "Output" logs in the Burp "Extension" tab to ensure that the extension has completed.
-   Examine the "Errors" logs in the Burp "Extension" tab. There should be an explicit error message with some tips to solve the problem.
-   Make sure you followed the [installation guidelines](../install.md). In case you didn't, remove the `~/.scalpel` directory and retry the install.
-   If the error message doesn't help, please open a GitHub issue including the "Output" and "Errors" logs, and your system information (OS / Distribution version, CPU architecture, JDK and Python version and installation path, environment variables which Burp runs with, and so forth).

### Why using Java with Jep to execute Python whereas Burp already supports Python extensions with [Jython](https://www.jython.org/)?
-   Jython supports up to Python 2.7, and no support at all for Python 3. Python 2.7 is basically a dead language and nobody should still be using it.
-   Burp's developers released a [new API](https://portswigger.net/burp/documentation/desktop/extensions/creating) for extensions and deprecated the former one. The new version only supports Java. That's why the most appropriate choice was to reimplement a partial Python scripting support for Burp.

### Scalpel requires python >=3.10 but my distribution is outdated and I can't install such recent Python versions using the package manager.
-   Try updating your distribution.
-   If that is not possible, you must setup a separate Python >=3.10 installation and run Burp with the appropriate environment so this separate installation is used.
    -   Tip: Use [pyenv](https://github.com/pyenv/pyenv) to easily install Python versions and switch between them.

### Why does Scalpel installs mitmproxy?
-   Scalpel relies on utilities from the mitmproxy package. Thus, in the future, these utilities may be dropped or rewritten to avoid using such a heavy package.

### I installed Python using the Microsoft Store and Scalpel doesn't work.
-   The Microsoft Store Python is a sandboxed version designed for educational purposes. It has multiple different behaviours that are incompatible with Scalpel. To use Scalpel on Windows, it is required to install Python from the [official source](https://www.python.org/downloads/windows/).
