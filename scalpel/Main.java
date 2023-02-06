package scalpel;
import jep.Interpreter;
import jep.SharedInterpreter;

public class Main {
    public static void main(String[] args) {
        try (Interpreter interp = new SharedInterpreter()) {
            interp.exec("from java.lang import System");
            interp.exec("s = 'Hello World'");
            interp.exec("System.out.println(s)");
            interp.exec("print(s)");
            interp.exec("print(s[1:-1])");
        }
    }
}