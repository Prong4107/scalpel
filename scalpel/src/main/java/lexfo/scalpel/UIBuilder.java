package lexfo.scalpel;

import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.Theme;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import javax.swing.*;

/**
	Provides methods for constructing the Burp Suite UI.
*/
public class UIBuilder {

	/**
		Constructs the configuration Burp tab.

		@param executor The ScalpelExecutor object to use.
		@param defaultScriptPath The default text content
		@return The constructed tab.
	 */
	public static final Component constructConfigTab(
		ScalpelExecutor executor,
		Config config,
		Theme theme
	) {
		return new ConfigTab(executor, config, theme).uiComponent();
	}

	/**
		Constructs the debug Python testing Burp tab.
		@param executor The ScalpelExecutor object to use.
		@param logger The Logging object to use.
		@return The constructed tab.
	*/
	public static final Component constructScalpelInterpreterTab(
		ScalpelExecutor executor,
		Logging logger
	) {
		// Split pane
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		JSplitPane scriptingPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		JTextArea outputArea = new JTextArea();
		JEditorPane editorPane = new JEditorPane();
		JButton button = new JButton("Run script.");

		button.addActionListener((ActionEvent e) -> {
			logger.logToOutput("Clicked button");
			final String scriptContent = editorPane.getText();
			try {
				final String[] scriptOutput = executor.evalAndCaptureOutput(
					scriptContent
				);

				var txt = String.format(
					"stdout:\n------------------\n%s\n------------------\n\nstderr:\n------------------\n%s",
					scriptOutput[0],
					scriptOutput[1]
				);

				outputArea.setText(txt);
			} catch (Exception exception) {
				outputArea.setText(exception.getMessage());
				outputArea.append("\n\n");

				final String stackTrace = Arrays
					.stream(exception.getStackTrace())
					.map(StackTraceElement::toString)
					.reduce((a, b) -> a + "\n" + b)
					.orElse("No stack trace.");

				outputArea.append(stackTrace);
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
		scriptingPane.setLeftComponent(new JScrollPane(editorPane));
		scriptingPane.setRightComponent(new JScrollPane(outputArea));
		scriptingPane.setResizeWeight(0.5);

		splitPane.setResizeWeight(1);
		splitPane.setLeftComponent(scriptingPane);
		splitPane.setRightComponent(button);

		return splitPane;
	}
}
