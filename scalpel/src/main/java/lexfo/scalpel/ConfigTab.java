package lexfo.scalpel;

import burp.api.montoya.ui.Theme;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DefaultCaret;
import lexfo.scalpel.Venv.PackageInfo;

/**
 * Burp tab handling Scalpel configuration
 * You will need to use IntelliJ's GUI designer to edit the GUI.
 */
public class ConfigTab extends JFrame {

	private JPanel rootPanel;
	private JButton frameworkBrowseButton;
	private JTextField frameworkPathField;
	private JPanel browsePanel;
	private JPanel frameworkConfigPanel;
	private JTextArea frameworkPathTextArea;
	private JPanel scriptConfigPanel;
	private JButton scriptBrowseButton;
	private JTextArea scriptPathTextArea;
	private JTextField scriptPathField;
	private JediTermWidget terminalForVenvConfig;
	private JList<String> venvListComponent;
	private JTable packagesTable;
	private JTextField addVentText;
	private JButton addVenvButton;
	private JPanel venvSelectPanel;
	private final ScalpelExecutor executor;
	private final Config config;
	private final Theme theme;

	public ConfigTab(ScalpelExecutor executor, Config config, Theme theme) {
		this.config = config;
		this.executor = executor;
		this.theme = theme;

		$$$setupUI$$$();

		// Make the text fields automatically scroll to the right when selected so that the file basename is visible.
		autoScroll(frameworkPathField);
		autoScroll(scriptPathField);

		// Scroll to the right on focus
		setUserScriptPath(config.getUserScriptPath());
		setFrameworkPath(config.getFrameworkPath());

		// Open file browser to select the script to execute.
		scriptBrowseButton.addActionListener(e ->
			handleBrowseButtonClick(
				scriptPathField::getText,
				this::setAndStoreScriptPath
			)
		);

		// Same as above for framework path.
		frameworkBrowseButton.addActionListener(e ->
			handleBrowseButtonClick(
				frameworkPathField::getText,
				this::setAndStoreFrameworkPath
			)
		);

		// Fill the venv list component.
		venvListComponent.setListData(config.getVenvPaths());

		// Update the displayed packages;
		updatePackagesTable();

		// Change the venv, terminal and package table when the user selects a venv.
		venvListComponent.addListSelectionListener(
			this::handleListSelectionEvent
		);

		// Add a new venv when the user clicks the button.
		addVenvButton.addActionListener(e -> handleVenvButton());

		// Add a new venv when the user presses enter in the text field.
		addVentText.addActionListener(e -> handleVenvButton());
	}

	private static void autoScroll(JTextField field) {
		DefaultCaret caret = (DefaultCaret) field.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		// Scroll to the right when the field is selected.
		field.addFocusListener(
			new FocusAdapter() {
				@Override
				public void focusGained(FocusEvent e) {
					field.setCaretPosition(field.getText().length());
				}
			}
		);
	}

	private void handleVenvButton() {
		final String value = addVentText.getText().trim();

		if (value.isEmpty()) {
			return;
		}

		final String path;
		try {
			if ((new File(value).isAbsolute())) {
				// The user provided an absolute path, use it as is.
				path = value;
			} else if (value.contains(File.separator)) {
				// The user provided a relative path, forbid it.
				throw new IllegalArgumentException(
					"Venv name cannot contain " +
					File.separator +
					"\n" +
					"Please provide a venv name or an absolute path."
				);
			} else {
				// The user provided a name, use it to create a venv in the default venvs dir.
				path =
					Paths
						.get(
							Config.getDefaultVenvsDir().getAbsolutePath(),
							value
						)
						.toString();
			}
		} catch (IllegalArgumentException e) {
			JOptionPane.showMessageDialog(
				this,
				e.getMessage(),
				"Invalid venv name or absolute path",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}

		WorkingPopup.showBlockingWaitDialog(() -> {
			// Create the venv and installed required packages. (i.e. mitmproxy)
			try {
				Venv.createAndInstallDefaults(path);
			} catch (IOException | InterruptedException e) {
				JOptionPane.showMessageDialog(
					this,
					"Failed to create venv: \n" + e.getMessage(),
					"Failed to create venv",
					JOptionPane.ERROR_MESSAGE
				);
				return;
			}

			// Add the venv to the config.
			config.addVenvPath(path);

			// Display the venv in the list.
			venvListComponent.setListData(config.getVenvPaths());

			// Clear the text field.
			addVentText.setText("");
		});
	}

	private void handleListSelectionEvent(ListSelectionEvent e) {
		// Ignore intermediate events.
		if (e.getValueIsAdjusting()) return;

		// Get the selected venv path.
		String selectedVenvPath = venvListComponent.getSelectedValue();
		config.setSelectedVenvPath(selectedVenvPath);

		// Update the package table.
		updatePackagesTable(__ -> {
			// Stop the terminal whiile we update it.
			this.terminalForVenvConfig.stop();

			// Close the terminal to ensure the process is killed.
			this.terminalForVenvConfig.getTtyConnector().close();

			// Connect the terminal to the new process in the new venv.
			this.terminalForVenvConfig.createTerminalSession(
					Terminal.createTtyConnector(selectedVenvPath)
				);

			// Start the terminal.
			this.terminalForVenvConfig.start();
		});
	}

	private void handleBrowseButtonClick(
		Supplier<String> getter,
		Consumer<String> setter
	) {
		final JFileChooser fileChooser = new JFileChooser();

		// Allow the user to only select files.
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

		// Set default path to the path in the text field.
		fileChooser.setCurrentDirectory(new File(getter.get()));

		int result = fileChooser.showOpenDialog(this);

		// When the user selects a file, set the text field to the selected file.
		if (result == JFileChooser.APPROVE_OPTION) {
			setter.accept(fileChooser.getSelectedFile().getAbsolutePath());
		}
	}

	private void updatePackagesTable(
		Consumer<JTable> onSuccess,
		Runnable onFail
	) {
		final PackageInfo[] installedPackages;
		try {
			installedPackages =
				Venv.getInstalledPackages(config.getSelectedVenvPath());
		} catch (IOException e) {
			JOptionPane.showMessageDialog(
				this,
				"Failed to get installed packages: \n" + e.getMessage(),
				"Failed to get installed packages",
				JOptionPane.ERROR_MESSAGE
			);
			onFail.run();
			return;
		}

		// Create a table model with the appropriate column names
		DefaultTableModel tableModel = new DefaultTableModel(
			new Object[] { "Name", "Version" },
			0
		);

		// Parse with jackson and add to the table model
		Arrays
			.stream(installedPackages)
			.map(p -> new Object[] { p.name, p.version })
			.forEach(tableModel::addRow);

		// Set the table model
		packagesTable.setModel(tableModel);

		// make the table uneditable
		packagesTable.setDefaultEditor(Object.class, null);

		onSuccess.accept(packagesTable);
	}

	private void updatePackagesTable(Consumer<JTable> onSuccess) {
		updatePackagesTable(onSuccess, () -> {});
	}

	private void updatePackagesTable() {
		updatePackagesTable(__ -> {});
	}

	private static void scrollToRight(JTextField textField) {
		textField.requestFocusInWindow();
		textField.setCaretPosition(textField.getText().length());
	}

	/**
	 * Sets the path to the user script to execute and updates the text field.
	 *
	 * @param path the path to the script
	 */
	private void setUserScriptPath(String path) {
		// Update the path selection text field.
		scriptPathField.setText(path);

		// Scroll to the right
		scrollToRight(scriptPathField);
	}

	private void setAndStoreFrameworkPath(String path) {
		setFrameworkPath(path);

		// Store the path in the config. (writes to disk)
		config.setFrameworkPath(path);
	}

	private void setAndStoreScriptPath(String path) {
		setUserScriptPath(path);

		// Store the path in the config. (writes to disk)
		config.setUserScriptPath(path);
	}

	/**
	 * Sets the path to the "framework" script to execute and updates the text field.
	 *
	 * @param path the path to the framework
	 */
	private void setFrameworkPath(String path) {
		// Update the path selection text field.
		frameworkPathField.setText(path);

		// Scroll to the right
		scrollToRight(frameworkPathField);
	}

	/**
	 * Returns the UI component to display.
	 *
	 * @return the UI component to display
	 */
	public Component uiComponent() {
		return rootPanel;
	}

	private void createUIComponents() {
		rootPanel = new JPanel();

		// Create the TtyConnector
		terminalForVenvConfig =
			Terminal.createTerminal(theme, config.getSelectedVenvPath());
	}

	/**
	 * Method generated by IntelliJ IDEA GUI Designer
	 * >>> IMPORTANT!! <<<
	 * DO NOT edit this method OR call it in your code!
	 *
	 * @noinspection ALL
	 */
	private void $$$setupUI$$$() {
		createUIComponents();
		rootPanel.setLayout(
			new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1)
		);
		rootPanel.setBackground(new Color(-65296));
		rootPanel.setBorder(
			BorderFactory.createTitledBorder(
				null,
				"",
				TitledBorder.DEFAULT_JUSTIFICATION,
				TitledBorder.DEFAULT_POSITION,
				null,
				null
			)
		);
		venvSelectPanel = new JPanel();
		venvSelectPanel.setLayout(
			new GridLayoutManager(4, 2, new Insets(5, 5, 5, 0), -1, -1)
		);
		venvSelectPanel.setBackground(new Color(-4915176));
		rootPanel.add(
			venvSelectPanel,
			new GridConstraints(
				0,
				0,
				1,
				2,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_BOTH,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_WANT_GROW,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
				null,
				null,
				null,
				0,
				false
			)
		);
		venvSelectPanel.setBorder(
			BorderFactory.createTitledBorder(
				null,
				"Manage virtualenvs",
				TitledBorder.DEFAULT_JUSTIFICATION,
				TitledBorder.DEFAULT_POSITION,
				null,
				null
			)
		);
		final JPanel panel1 = new JPanel();
		panel1.setLayout(
			new FormLayout(
				"fill:d:grow,left:4dlu:noGrow,fill:max(d;4px):noGrow",
				"center:d:grow"
			)
		);
		venvSelectPanel.add(
			panel1,
			new GridConstraints(
				0,
				0,
				1,
				1,
				GridConstraints.ANCHOR_SOUTH,
				GridConstraints.FILL_HORIZONTAL,
				1,
				1,
				null,
				null,
				null,
				0,
				false
			)
		);
		addVenvButton = new JButton();
		addVenvButton.setText("+");
		CellConstraints cc = new CellConstraints();
		panel1.add(addVenvButton, cc.xy(3, 1));
		addVentText = new JTextField();
		addVentText.setToolTipText("");
		panel1.add(
			addVentText,
			cc.xy(1, 1, CellConstraints.FILL, CellConstraints.FILL)
		);
		terminalForVenvConfig.setBackground(new Color(-15678720));
		venvSelectPanel.add(
			terminalForVenvConfig,
			new GridConstraints(
				0,
				1,
				4,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_BOTH,
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
		final JPanel panel2 = new JPanel();
		panel2.setLayout(
			new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1)
		);
		venvSelectPanel.add(
			panel2,
			new GridConstraints(
				1,
				0,
				3,
				1,
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
		final JScrollPane scrollPane1 = new JScrollPane();
		panel2.add(
			scrollPane1,
			new GridConstraints(
				0,
				0,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_BOTH,
				1,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
				null,
				null,
				null,
				0,
				false
			)
		);
		venvListComponent = new JList();
		final DefaultListModel defaultListModel1 = new DefaultListModel();
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement(
			"loremaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatumaucupatum"
		);
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatiolorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatiolorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatiolorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatiolorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatiolorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		defaultListModel1.addElement("lorem");
		defaultListModel1.addElement("ipsum");
		defaultListModel1.addElement("aucupatum");
		defaultListModel1.addElement("versatio");
		venvListComponent.setModel(defaultListModel1);
		scrollPane1.setViewportView(venvListComponent);
		final JScrollPane scrollPane2 = new JScrollPane();
		panel2.add(
			scrollPane2,
			new GridConstraints(
				1,
				0,
				2,
				1,
				GridConstraints.ANCHOR_WEST,
				GridConstraints.FILL_VERTICAL,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_WANT_GROW,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
				null,
				null,
				null,
				0,
				false
			)
		);
		scrollPane2.setBorder(
			BorderFactory.createTitledBorder(
				null,
				"",
				TitledBorder.DEFAULT_JUSTIFICATION,
				TitledBorder.DEFAULT_POSITION,
				null,
				null
			)
		);
		packagesTable = new JTable();
		scrollPane2.setViewportView(packagesTable);
		final JPanel panel3 = new JPanel();
		panel3.setLayout(
			new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1)
		);
		rootPanel.add(
			panel3,
			new GridConstraints(
				0,
				2,
				1,
				1,
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
		browsePanel = new JPanel();
		browsePanel.setLayout(
			new GridLayoutManager(3, 3, new Insets(10, 10, 10, 10), 0, -1)
		);
		browsePanel.setBackground(new Color(-5198676));
		panel3.add(
			browsePanel,
			new GridConstraints(
				0,
				0,
				1,
				1,
				GridConstraints.ANCHOR_NORTH,
				GridConstraints.FILL_HORIZONTAL,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
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
		frameworkPathField = new JTextField();
		frameworkPathField.setHorizontalAlignment(4);
		frameworkPathField.setText("");
		frameworkConfigPanel.add(
			frameworkPathField,
			new GridConstraints(
				1,
				1,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_BOTH,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_CAN_GROW,
				null,
				new Dimension(50, 10),
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
		scriptPathField = new JTextField();
		scriptPathField.setText("");
		scriptConfigPanel.add(
			scriptPathField,
			new GridConstraints(
				1,
				1,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_BOTH,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_WANT_GROW,
				GridConstraints.SIZEPOLICY_CAN_SHRINK |
				GridConstraints.SIZEPOLICY_WANT_GROW,
				null,
				new Dimension(50, -1),
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
		return rootPanel;
	}
}
