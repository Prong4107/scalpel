package lexfo.scalpel;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import burp.api.montoya.logging.Logging;

public class UIBuilder {

  // Constructs the debug Python testing Burp tab.
  public final static Component constructScalpelInterpreterTab(ScalpelExecutor executor, Logging logger) {
    // Split pane
    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    JSplitPane scriptingPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    JTextArea outputArea = new JTextArea();
    var editorPane = new JEditorPane();
    JButton button = new JButton("Run script.");

    button.addActionListener((ActionEvent e) -> {
      logger.logToOutput("Clicked button");
      String scriptContent = editorPane.getText();
      try {
        String[] scriptOutput = executor.evalAndCaptureOutput(scriptContent);

        var txt = String.format(
          "stdout:\n------------------\n%s\n------------------\n\nstderr:\n------------------\n%s",
          scriptOutput[0],
          scriptOutput[1]
        );

        outputArea.setText(txt);
      } catch (Exception exception) {
        outputArea.setText(exception.toString());
      }
      logger.logToOutput("Handled action.");
    });

    editorPane.setText(
      """
print('This goes in stdout')
print('This goes in stderr', file=sys.stderr)
"""
    );
    outputArea.setEditable(false);
    scriptingPane.setLeftComponent(editorPane);
    scriptingPane.setRightComponent(outputArea);
    scriptingPane.setResizeWeight(0.5);

    splitPane.setResizeWeight(1);
    splitPane.setLeftComponent(scriptingPane);
    splitPane.setRightComponent(button);

    return splitPane;
  }
}
