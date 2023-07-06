# How Scalpel works

-   Scalpel uses the Java [Burp Montoya API](https://portswigger.net/burp/documentation/desktop/extensions) to interact with Burp
-   Scalpel uses Java to handle the dependencies install, HTTP and GUI for Burp and communication with Python
-   Scalpel uses [Jep](https://github.com/ninia/jep/) to execute Python from Java.
-   Python execution is handled through a task queue in a dedicated thread that will execute one Python task at a time in a thread-safe way.
-   Scalpel uses a single shared interpreter, so if any global variables are changed in a hook, it's value will remain changed on next hook calls.
-   All Python hooks are executed through a `_framework.py` file that will activate the selected venv, load the user script file, look for callable objects matching the hooks names (`match, request, response, req_edit_in, res_edit_in, req_edit_out, res_edit_out, req_edit_in_<tab_name>, res_edit_in_<tab_name>, req_edit_out_<tab_name>, res_edit_out_<tab_name>`)
-   The `_framework.py` declared callbacks that receives Java objects, convert it to a custom easy to use Python object, passes the Python object to the corresponding user hook, gets back the modified Python objects and converts them back to a Java object.
-   Java code receives the hook result and interact with Burp to apply the effects
-   At each tasks, Scalpel will check if the user script file has changed and if so, will reload and restart the interpreter.
-   Scripts are executed in a venv the user can select in the "Scalpel" tab.
-   Scalpel will always use a venv for every action, hence it will never modify your global Python installation.
-   Scalpel provides a terminal with a shell running in the selected venv to allow you to easily install packages.
-   You can add existing venvs or create new ones using the dedicated GUI.
