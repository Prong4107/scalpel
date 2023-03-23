package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.*;
import java.io.File;
import javax.swing.*;
import javax.swing.border.TitledBorder;

/**
 * Burp tab handling Scalpel configuration (script path)
 */
public class ConfigTab extends JFrame {

	private JPanel panel1;
	private JButton browseButton;
	private JTextField textField1;
	private JPanel browsePanel;
	private ScalpelExecutor executor;
	private MontoyaApi API;

	public ConfigTab(
		MontoyaApi API,
		ScalpelExecutor executor,
		String defaultPath
	) {
		this.API = API;
		this.executor = executor;
		final String storedValue = API
			.persistence()
			.preferences()
			.getString("scalpelScript");

		setScriptPath(storedValue != null ? storedValue : "");

		// Handle browse button click.
		browseButton.addActionListener(e -> {
			final JFileChooser fileChooser = new JFileChooser();
			fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

			// Set default path to the path in the text field.
			fileChooser.setCurrentDirectory(new File(textField1.getText()));

			int result = fileChooser.showOpenDialog(this);
			if (result == JFileChooser.APPROVE_OPTION) {
				setScriptPath(fileChooser.getSelectedFile().getAbsolutePath());
			}
		});
	}

	private void setScriptPath(String path) {
		executor.setScript(path);
		textField1.setText(path);
		API.persistence().preferences().setString("scalpelScript", path);
	}

	public ConfigTab(MontoyaApi API, ScalpelExecutor executor) {
		this(API, executor, "");
	}

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
			new GridLayoutManager(1, 3, new Insets(10, 10, 10, 10), 10, -1)
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
		browseButton = new JButton();
		browseButton.setText("Browse");
		browsePanel.add(
			browseButton,
			new GridConstraints(
				0,
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
		textField1 = new JTextField();
		textField1.setText("");
		browsePanel.add(
			textField1,
			new GridConstraints(
				0,
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
		browsePanel.add(
			spacer1,
			new GridConstraints(
				0,
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
	}

	/**
	 * @noinspection ALL
	 */
	public JComponent $$$getRootComponent$$$() {
		return panel1;
	}
}
