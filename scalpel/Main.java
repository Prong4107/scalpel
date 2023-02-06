package scalpel;

import jep.Interpreter;
import jep.SharedInterpreter;
import java.lang.System;

public class Main {

    public static void main(String[] args) {
        // Python script path.
        var script = "scripts/myscript.py";

        try (Interpreter interp = new SharedInterpreter()) {
            // Running Python instructions on the fly.
            // https://github.com/t2y/jep-samples/blob/master/src/HelloWorld.java
            System.out.println("------- Running \"on the fly\" Python -------");

            interp.exec("from java.lang import System");
            interp.exec("s = 'Hello World'");
            interp.exec("System.out.println(s)");
            interp.exec("print(f'F strings are working: {s + \"_World\"}')");
            interp.exec("print(s[1:-1])");

            // Running a python script file.
            //https://github.com/t2y/jep-samples/blob/master/src/RunScript.java
            System.out.println(String.format("------- Running %s ---------------", script));
            interp.runScript(script);
        }
    }
}