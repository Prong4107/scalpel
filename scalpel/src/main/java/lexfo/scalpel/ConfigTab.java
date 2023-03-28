package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.PersistedObject;
import burp.api.montoya.persistence.Preferences;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.*;
import java.io.File;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.TitledBorder;

/**
 * Burp tab handling Scalpel configuration
 * You will need to use IntelliJ's GUI designer to edit the GUI.
 */
public class ConfigTab extends JFrame {

	private JPanel panel1;
	private JButton frameworkBrowseButton;
	private JTextField frameworkPathField;
	private JPanel browsePanel;
	private JPanel frameworkConfigPanel;
	private JTextArea frameworkPathTextArea;
	private JPanel scriptConfigPanel;
	private JButton scriptBrowseButton;
	private JTextArea scriptPathTextArea;
	private JTextField scriptPathField;
	private final ScalpelExecutor executor;
	private final MontoyaApi API;
	private final PersistedObject extensionData;
	private final Preferences preferences;
	private static final String scriptKey = Constants.PERSISTED_SCRIPT;

	public ConfigTab(
		MontoyaApi API,
		ScalpelExecutor executor,
		String frameworkPath,
		String defaultScriptPath
	) {
		this.API = API;
		this.executor = executor;
		this.extensionData = API.persistence().extensionData();
		this.preferences = API.persistence().preferences();

		final String defaultValue = defaultScriptPath == ""
			? getStoredScriptPath()
			: defaultScriptPath;
		setUserScriptPath(defaultValue);
		setFrameworkPath(frameworkPath);

		// Handle browse button click.
		scriptBrowseButton.addActionListener(e ->
			handleBrowseButtonClick(
				this::setUserScriptPath,
				() -> scriptPathField.getText()
			)
		);

		// Same as above for framework path.
		frameworkBrowseButton.addActionListener(e ->
			handleBrowseButtonClick(
				this::setFrameworkPath,
				() -> frameworkPathField.getText()
			)
		);
	}

	private void handleBrowseButtonClick(
		Consumer<String> callback,
		Supplier<String> getDefaultPath
	) {
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

		// Set default path to the path in the text field.
		fileChooser.setCurrentDirectory(new File(getDefaultPath.get()));

		int result = fileChooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			// callback.apply(fileChooser.getSelectedFile().getAbsolutePath());
			callback.accept(fileChooser.getSelectedFile().getAbsolutePath());
		}
	}

	/**
	 * Returns the value of the given key from the extension data or from the
	 * preferences. If none of them is set, returns an empty Optional.
	 * <p>
	 * Extension data is used to store data that is specific to the current project
	 * Preferences is global storage and is used to provide a previously used default value for new projects
	 *
	 * @param key the key to look for
	 * @return the value of the given key
	 */
	private Optional<String> getStoredDefaultString(String key) {
		return Optional
			.ofNullable(extensionData.getString(key))
			.or(() -> Optional.ofNullable(preferences.getString(key)));
	}

	/**
	 * Returns the path to the script stored in the extension data or in the
	 * preferences. If none of them is set, returns an empty String.
	 *
	 * @return the path to the script
	 */
	private String getStoredScriptPath() {
		return getStoredDefaultString(scriptKey).orElse("");
	}

	/**
	 * Sets the given value for the given key in the extension data and in the
	 * preferences.
	 * <p>
	 * Extension data is used to store data that is specific to the current project
	 * Preferences is global storage and is used to provide a previously used default value for new projects
	 * <p>
	 *
	 * @param key the key to set
	 * @param value the value to set
	 *
	 * @see #getStoredDefaultString(String)
	 */
	private void setStoredDefaultString(String key, String value) {
		extensionData.setString(key, value);
		preferences.setString(key, value);
	}

	/**
	 * Sets the path to the user script to execute and updates the text field.
	 *
	 * @param path the path to the script
	 */
	private void setUserScriptPath(String path) {
		// Change and load the user script that will define user callbacks.
		executor.setUserScript(path);

		// Update the path selection text field.
		scriptPathField.setText(path);

		// Store the path in the extension and preferences data.
		setStoredDefaultString(scriptKey, path);
	}

	/**
	 * Sets the path to the "framework" script to execute and updates the text field.
	 *
	 * @param path the path to the framework
	 */
	private void setFrameworkPath(String path) {
		// Change and load the framework script that will be ran by Jep. (wraps user callbacks)
		executor.setFramework(path);

		// Update the path selection text field.
		frameworkPathField.setText(path);
	}

	public ConfigTab(MontoyaApi API, ScalpelExecutor executor) {
		this(API, executor, "", "");
	}

	/**
	 * Returns the UI component to display.
	 *
	 * @return the UI component to display
	 */
	public Component uiComponent() {
		return panel1;
	}

	{
		// GUI initializer generated by IntelliJ IDEA GUI Designer
		// >>> IMPORTANT!! <<<
		// DO NOT EDIT OR ADD ANY CODE HERE!
		$$$setupUI$$$();
	}

	/**
	 * Method generated by IntelliJ IDEA GUI Designer
	 * >>> IMPORTANT!! <<<
	 * DO NOT edit this method OR call it in your code!
	 *
	 * @noinspection ALL
	 */
	private void $$$setupUI$$$() {
		panel1 = new JPanel();
		panel1.setLayout(
			new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), 10, 10)
		);
		panel1.setBorder(
			BorderFactory.createTitledBorder(
				null,
				"",
				TitledBorder.DEFAULT_JUSTIFICATION,
				TitledBorder.DEFAULT_POSITION,
				null,
				null
			)
		);
		browsePanel = new JPanel();
		browsePanel.setLayout(
			new GridLayoutManager(3, 3, new Insets(10, 10, 10, 10), 10, -1)
		);
		browsePanel.setBackground(new Color(-5198676));
		panel1.add(
			browsePanel,
			new GridConstraints(
				0,
				0,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_NONE,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_WANT_GROW,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_WANT_GROW,
				null,
				null,
				null,
				0,
				false
			)
		);
		frameworkConfigPanel = new JPanel();
		frameworkConfigPanel.setLayout(
			new GridLayoutManager(2, 3, new Insets(0, 0, 10, 10), -1, -1)
		);
		browsePanel.add(
			frameworkConfigPanel,
			new GridConstraints(
				1,
				0,
				1,
				3,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_BOTH,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
				null,
				null,
				null,
				0,
				false
			)
		);
		frameworkBrowseButton = new JButton();
		frameworkBrowseButton.setText("Browse");
		frameworkConfigPanel.add(
			frameworkBrowseButton,
			new GridConstraints(
				1,
				0,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_NONE,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
				GridConstraints.SIZEPOLICY_FIXED,
				null,
				null,
				null,
				1,
				false
			)
		);
		frameworkPathField = new JTextField();
		frameworkPathField.setText("");
		frameworkConfigPanel.add(
			frameworkPathField,
			new GridConstraints(
				1,
				1,
				1,
				1,
				GridConstraints.ANCHOR_WEST,
				GridConstraints.FILL_NONE,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				GridConstraints.SIZEPOLICY_FIXED,
				null,
				new Dimension(400, -1),
				null,
				0,
				false
			)
		);
		final Spacer spacer1 = new Spacer();
		frameworkConfigPanel.add(
			spacer1,
			new GridConstraints(
				1,
				2,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_HORIZONTAL,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				1,
				null,
				null,
				null,
				0,
				false
			)
		);
		frameworkPathTextArea = new JTextArea();
		frameworkPathTextArea.setText("framework path");
		frameworkConfigPanel.add(
			frameworkPathTextArea,
			new GridConstraints(
				0,
				1,
				1,
				1,
				GridConstraints.ANCHOR_SOUTH,
				GridConstraints.FILL_NONE,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				null,
				new Dimension(150, 10),
				null,
				0,
				false
			)
		);
		scriptConfigPanel = new JPanel();
		scriptConfigPanel.setLayout(
			new GridLayoutManager(2, 3, new Insets(0, 0, 10, 10), -1, -1)
		);
		browsePanel.add(
			scriptConfigPanel,
			new GridConstraints(
				2,
				0,
				1,
				3,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_BOTH,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
				null,
				null,
				null,
				0,
				false
			)
		);
		scriptBrowseButton = new JButton();
		scriptBrowseButton.setText("Browse");
		scriptConfigPanel.add(
			scriptBrowseButton,
			new GridConstraints(
				1,
				0,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_NONE,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
				GridConstraints.SIZEPOLICY_FIXED,
				null,
				null,
				null,
				1,
				false
			)
		);
		scriptPathField = new JTextField();
		scriptPathField.setText("");
		scriptConfigPanel.add(
			scriptPathField,
			new GridConstraints(
				1,
				1,
				1,
				1,
				GridConstraints.ANCHOR_WEST,
				GridConstraints.FILL_NONE,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				GridConstraints.SIZEPOLICY_FIXED,
				null,
				new Dimension(400, -1),
				null,
				0,
				false
			)
		);
		final Spacer spacer2 = new Spacer();
		scriptConfigPanel.add(
			spacer2,
			new GridConstraints(
				1,
				2,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_HORIZONTAL,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				1,
				null,
				null,
				null,
				0,
				false
			)
		);
		scriptPathTextArea = new JTextArea();
		scriptPathTextArea.setText("script path");
		scriptConfigPanel.add(
			scriptPathTextArea,
			new GridConstraints(
				0,
				1,
				1,
				1,
				GridConstraints.ANCHOR_SOUTH,
				GridConstraints.FILL_NONE,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				null,
				new Dimension(150, 10),
				null,
				0,
				false
			)
		);
	}

	/**
	 * @noinspection ALL
	 */
	public JComponent $$$getRootComponent$$$() {
		return panel1;
	}
}
