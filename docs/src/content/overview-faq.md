---
title: "FAQ"
menu: "overview"
menu:
    overview:
        weight: 3
---

# FAQ:

-   Why does Scalpel depends on JDK when Burp comes with it' own JRE ?
    -   Scalpel uses a project called [jep](https://github.com/ninia/jep/wiki/) to call Python from Java. jep needs a JDK to function.
    -   If you are curious or need more technichal information on Scalpel's implementation, look at [How scalpel works]({{< relref "concepts-howscalpelworks" >}}).
-   After loading the .jar, nothing appears in the request editor!
    -   On the first install, Scalpel may take a while to install it's dependencies Look at the "Output" logs in the Burp "Extension" tab and ensure that the extension has finished installing.
    -   Please look at the "Errors" logs in the Burp "Extension" tab, there should be an explicit error message and some tips to solve the problem.
    -   Make sure you followed the [install](install.md) page, if not, remove the `~/.scalpel` directory and retry the install.
    -   If the error message doesn't help you solve the issue, please file an issue with "Output" and "Errors" logs and your system information (OS / Distribution version, CPU architecture, jdk and python version and installation path, environnement variables which Burp runs with, etc...)
-   Why using Java with Jep to execute Python when Burp already supports Python extensions with [Jython](https://www.jython.org/) ?
    -   Jython supports up to Python 2.7, and no support at all for Python 3. Python 2.7 is basically a dead language and nobody should still be using it.
    -   Burp's developers have released a [new API](https://portswigger.net/burp/documentation/desktop/extensions/creating) for extensions and deprecated the old one. The new one only supports Java, so we had no choice but reimplementing a partial Python scripting support for Burp ourself.
-   Scalpel requires python >=3.10 but my distribution is outdated and doesn't allow installing such recent Python versions using the package manager.
    -   You can try updating your distribution.
    -   If you cannot update your distribution, you must setup a separate Python >=3.10 installation and run Burp with the appropriate environnement so that your separate installation will be used.
        -   Tip: You can use [pyenv](https://github.com/pyenv/pyenv) to easily install and switch Python versions.
-   Why does scalpel installs mitmproxy ?
    - Scalpel uses utilities from the mitmproxy package, we may eventually stop using or re-implement this utilities to drop the need to install such a heavy package.