---
title: "How scalpel works"
menu:
    concepts:
        weight: 1
---

# How Scalpel works

## Dependencies

-   Scalpel embeds it's Python library in it's .jar file and unzips it when Burp loads the extension.
-   Scalpel requires external dependencies and will install them using `pip` when needed.
-   Scalpel will always use a venv for every action, hence it will never modify your global Python installation.
-   Scalpel uses a project calls Jep to communicate with Python, which requires to have a JDK installed on your machine.
-   User Scripts are executed in a venv the user can select in the "Scalpel" tab.
-   Scalpel provides a terminal with a shell running in the selected venv to allow you to easily install packages.
-   You can add existing venvs or create new ones using the dedicated GUI.
-   All data is stored in the `~/.scalpel` directory.

## Behaviour

-   Scalpel uses the Java [Burp Montoya API](https://portswigger.net/burp/documentation/desktop/extensions) to interact with Burp.
-   Scalpel uses Java to handle the dependencies install, HTTP and GUI for Burp and communication with Python.
-   Scalpel uses [Jep](https://github.com/ninia/jep/) to execute Python from Java.
-   Python execution is handled through a task queue in a dedicated thread that will execute one Python task at a time in a thread-safe way.
-   All Python hooks are executed through a `_framework.py` file that will activate the selected venv, load the user script file, look for callable objects matching the hooks names (`match, request, response, req_edit_in, res_edit_in, req_edit_out, res_edit_out, req_edit_in_<tab_name>, res_edit_in_<tab_name>, req_edit_out_<tab_name>, res_edit_out_<tab_name>`).
-   The `_framework.py` declares callbacks that receives Java objects, convert it to a custom easy to use Python object, passes the Python object to the corresponding user hook, gets back the modified Python objects and converts them back to a Java object.
-   Java code receives the hook result and interact with Burp to apply the effects.
-   At each task, Scalpel will check if the user script file has changed and if so, will reload and restart the interpreter.

## Python scripting

-   Scalpel uses a single shared interpreter, so if any global variables are changed in a hook, it's value will remain changed on next hook calls.
-   For easy scripting in Python, scalpel provides many utilities described in the Event Hooks & API section.

---

Here is a diagram illustating the points above:
{{< figure src="/schematics/scalpel-diagram.svg" >}}
