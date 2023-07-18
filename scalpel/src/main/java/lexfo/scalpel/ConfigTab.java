package lexfo.scalpel;

import burp.api.montoya.ui.Theme;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.UIUtil;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;
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
	private JButton editButton;
	private JButton createButton;
	private JList venvScriptList;
	private JPanel listPannel;
	private JButton openFolderButton;
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
		updateScriptList();

		// Change the venv, terminal and package table when the user selects a venv.
		venvListComponent.addListSelectionListener(
			this::handleVenvListSelectionEvent
		);

		venvScriptList.addListSelectionListener(
			this::handleScriptListSelectionEvent
		);

		// Add a new venv when the user clicks the button.
		addVenvButton.addActionListener(e -> handleVenvButton());

		// Add a new venv when the user presses enter in the text field.
		addVentText.addActionListener(e -> handleVenvButton());

		editButton.addActionListener(e -> handleEditButton());

		createButton.addActionListener(e -> handleNewScriptButton());

		openFolderButton.addActionListener(e -> handleOpenScriptFolderButton());
	}

	private static void autoScroll(JTextField field) {
		final DefaultCaret caret = (DefaultCaret) field.getCaret();
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

	private void handleOpenScriptFolderButton() {
		final File folder = new File(config.getUserScriptPath())
			.getParentFile();

		ScalpelLogger.debug("Opening " + folder.getAbsolutePath());

		// Check if Desktop is supported
		if (!Desktop.isDesktopSupported()) {
			System.err.println("Desktop is not supported.");
			return;
		}

		Desktop desktop = Desktop.getDesktop();

		if (folder.exists()) {
			try {
				desktop.open(folder);
			} catch (IOException e) {
				System.err.println("Error opening folder: " + e.getMessage());
			}
		} else {
			System.err.println(
				"The folder does not exist: " + folder.getAbsolutePath()
			);
		}
	}

	private void handleScriptListSelectionEvent(ListSelectionEvent event) {
		if (event.getValueIsAdjusting()) {
			return;
		}

		// Get the selected script name.
		final Optional<String> selected = Optional
			.ofNullable(venvScriptList.getSelectedValue())
			.map(s -> s.toString());

		selected.ifPresent(s -> {
			final Path path = Path
				.of(config.getSelectedVenv(), s)
				.toAbsolutePath();

			selectScript(path.toString());
		});
	}

	private void updateScriptList() {
		final JList<String> list = this.venvScriptList;
		final File selectedVenv = new File(config.getSelectedVenv());
		final File[] files = selectedVenv.listFiles(f ->
			f.getName().endsWith(".py")
		);

		final DefaultListModel<String> listModel = new DefaultListModel<>();

		// Fill the model with the file names
		if (files != null) {
			for (File file : files) {
				listModel.addElement(file.getName());
			}
		}

		list.setModel(listModel);
	}

	private void selectScript(String path) {
		// Select the script
		config.setUserScriptPath(path);

		// Update displayed selection.
		this.scriptPathField.setText(path);

		// Open the script in an editor.
		handleEditButton();
	}

	private void handleNewScriptButton() {
		final File venv = new File(config.getSelectedVenv());

		// Prompt the user for a name
		String fileName = JOptionPane.showInputDialog(
			"Enter the name for the new script"
		);

		if (fileName == null || fileName.trim().isEmpty()) {
			// The user didn't enter a name
			JOptionPane.showMessageDialog(
				null,
				"You must provide a name for the file."
			);
			return;
		}

		// Append .py extension if it's not there
		if (!fileName.endsWith(".py")) {
			fileName += ".py";
		}

		// Define the source file
		Path source = Path.of(
			System.getProperty("user.home"),
			".scalpel",
			"extracted",
			"templates",
			"default.py"
		);

		// Define the destination file
		Path destination = venv.toPath().resolve(fileName);

		// Copy the file
		try {
			Files.copy(
				source,
				destination,
				StandardCopyOption.REPLACE_EXISTING
			);
			JOptionPane.showMessageDialog(
				null,
				"File was successfully created!"
			);

			final String absolutePath = destination.toAbsolutePath().toString();

			selectScript(absolutePath);
			updateScriptList();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(
				null,
				"Error copying file: " + e.getMessage()
			);
		}
	}

	/**
	 * Opens the script in the OS' configured editor
	 * <p>
	 * Mostly useful for Windows.
	 *
	 * @param fileToEdit
	 * @return Whether the editor was successfully opened.
	 */
	private boolean openDesktopEditor(String fileToEdit) {
		if (!Desktop.isDesktopSupported()) {
			ScalpelLogger.error("Desktop is not supported");
			return false;
		}

		final Desktop desktop = Desktop.getDesktop();
		if (!desktop.isSupported(Desktop.Action.EDIT)) {
			ScalpelLogger.error("EDIT is not supported");

			return false;
		}

		try {
			// Provide the full path to the Python file
			final File file = new File(fileToEdit);
			desktop.edit(file);
		} catch (IOException ex) {
			ScalpelLogger.logStackTrace(ex);
			return false;
		}

		return true;
	}

	/**
	 * Opens the script in a terminal editor
	 * <p>
	 * Tries to use the EDITOR env var
	 * Falls back to vi if EDITOR is missing
	 *
	 * @param fileToEdit
	 */
	private void openEditorInTerminal(String fileToEdit) {
		final Optional<String> envEditor = Optional.ofNullable(
			System.getenv("EDITOR")
		);

		// Set default value
		final String editor = envEditor.orElse(
			Constants.DEFAULT_TERMINAL_EDITOR
		);

		final String cmd = editor + " " + fileToEdit;

		final String cwd = Path.of(fileToEdit).getParent().toString();

		this.updateTerminal(config.getSelectedVenv(), cwd, cmd);
	}

	private void handleEditButton() {
		final String script = config.getUserScriptPath();
		if (openDesktopEditor(script)) {
			return;
		}

		if (UIUtil.isWindows) {
			updateTerminal(
				config.getSelectedVenv(),
				Path.of(script).getParent().toString(),
				Constants.DEFAULT_WINDOWS_EDITOR + " " + script
			);
			return;
		}

		openEditorInTerminal(script);
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

		WorkingPopup.showBlockingWaitDialog(
			"Creating venv and installing required packages...",
			label -> {
				// Create the venv and installed required packages. (i.e. mitmproxy)
				try {
					Venv.create(path + File.separator + Config.VENV_DIR);
					// Add the venv to the config.
					config.addVenvPath(path);

					// Display the venv in the list.
					venvListComponent.setListData(config.getVenvPaths());

					// Clear the text field.
					addVentText.setText("");

					final Process proc = Venv.installDefaults(path);
					final var stdout = proc.inputReader();
					String displayed = "";

					while (proc.isAlive()) {
						displayed += stdout.readLine();
						label.setText(displayed);
					}
				} catch (IOException | InterruptedException e) {
					final String msg =
						"Failed to create venv: \n" + e.getMessage();
					ScalpelLogger.error(msg);
					ScalpelLogger.logStackTrace(e);
					JOptionPane.showMessageDialog(
						this,
						msg,
						"Failed to create venv",
						JOptionPane.ERROR_MESSAGE
					);
					return;
				}
			}
		);
	}

	private void updateTerminal(
		String selectedVenvPath,
		String cwd,
		String cmd
	) {
		// Stop the terminal whiile we update it.
		this.terminalForVenvConfig.stop();

		// Close the terminal to ensure the process is killed.
		this.terminalForVenvConfig.getTtyConnector().close();

		final var connector = Terminal.createTtyConnector(
			selectedVenvPath,
			Optional.ofNullable(cwd),
			Optional.ofNullable(cmd)
		);

		// Connect the terminal to the new process in the new venv.
		this.terminalForVenvConfig.setTtyConnector(connector);

		final int width =
			this.terminalForVenvConfig.getTerminal().getTerminalWidth();

		final int height =
			this.terminalForVenvConfig.getTerminal().getTerminalHeight();

		// Tty needs to be resized.
		final Dimension dimension = new Dimension(width, height);
		connector.resize(dimension);

		// Start the terminal.
		this.terminalForVenvConfig.start();
	}

	private void updateTerminal(String selectedVenvPath) {
		updateTerminal(selectedVenvPath, null, null);
	}

	private void handleVenvListSelectionEvent(ListSelectionEvent e) {
		// Ignore intermediate events.
		if (e.getValueIsAdjusting()) return;

		// Get the selected venv path.
		final String selectedVenvPath = venvListComponent.getSelectedValue();
		config.setSelectedVenvPath(selectedVenvPath);

		// Update the package table.
		updatePackagesTable(__ -> updateTerminal(selectedVenvPath));
		updateScriptList();
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

		final int result = fileChooser.showOpenDialog(this);

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
				Venv.getInstalledPackages(config.getSelectedVenv());
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
		final DefaultTableModel tableModel = new DefaultTableModel(
			new Object[] { "Package", "Version" },
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
			Terminal.createTerminal(theme, config.getSelectedVenv());
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
		panel1.setLayout(new BorderLayout(0, 0));
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
				new Dimension(100, -1),
				null,
				0,
				false
			)
		);
		addVentText = new JTextField();
		addVentText.setText("");
		addVentText.setToolTipText("");
		panel1.add(addVentText, BorderLayout.CENTER);
		addVenvButton = new JButton();
		addVenvButton.setText("+");
		panel1.add(addVenvButton, BorderLayout.EAST);
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
		defaultListModel1.addElement("loremaucupatum");
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
			new GridLayoutManager(7, 3, new Insets(10, 10, 10, 10), 0, -1)
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
		frameworkConfigPanel.setEnabled(false);
		frameworkConfigPanel.setVisible(false);
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
		scriptPathTextArea.setText("Load script file");
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
		final JPanel panel4 = new JPanel();
		panel4.setLayout(
			new GridLayoutManager(2, 1, new Insets(0, 0, 10, 10), -1, -1)
		);
		browsePanel.add(
			panel4,
			new GridConstraints(
				4,
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
		createButton = new JButton();
		createButton.setText("Create new script");
		panel4.add(
			createButton,
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
		final Spacer spacer3 = new Spacer();
		panel4.add(
			spacer3,
			new GridConstraints(
				0,
				0,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_VERTICAL,
				1,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				null,
				new Dimension(-1, 10),
				null,
				0,
				false
			)
		);
		final JPanel panel5 = new JPanel();
		panel5.setLayout(
			new GridLayoutManager(2, 1, new Insets(0, 0, 10, 10), -1, -1)
		);
		browsePanel.add(
			panel5,
			new GridConstraints(
				3,
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
		editButton = new JButton();
		editButton.setText("Edit selected script");
		panel5.add(
			editButton,
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
		final Spacer spacer4 = new Spacer();
		panel5.add(
			spacer4,
			new GridConstraints(
				0,
				0,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_VERTICAL,
				1,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				null,
				new Dimension(-1, 10),
				null,
				0,
				false
			)
		);
		listPannel = new JPanel();
		listPannel.setLayout(
			new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1)
		);
		browsePanel.add(
			listPannel,
			new GridConstraints(
				6,
				0,
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
		final JScrollPane scrollPane3 = new JScrollPane();
		listPannel.add(
			scrollPane3,
			new GridConstraints(
				1,
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
		venvScriptList = new JList();
		final DefaultListModel defaultListModel2 = new DefaultListModel();
		defaultListModel2.addElement("default.py");
		defaultListModel2.addElement("crypto.py");
		defaultListModel2.addElement("recon.py");
		venvScriptList.setModel(defaultListModel2);
		venvScriptList.setToolTipText("");
		venvScriptList.putClientProperty("List.isFileList", Boolean.FALSE);
		scrollPane3.setViewportView(venvScriptList);
		final JLabel label1 = new JLabel();
		label1.setHorizontalAlignment(0);
		label1.setHorizontalTextPosition(0);
		label1.setText("Scripts available for this venv:");
		listPannel.add(
			label1,
			new GridConstraints(
				0,
				0,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_NONE,
				GridConstraints.SIZEPOLICY_FIXED,
				GridConstraints.SIZEPOLICY_FIXED,
				null,
				null,
				null,
				0,
				false
			)
		);
		final JPanel panel6 = new JPanel();
		panel6.setLayout(
			new GridLayoutManager(2, 1, new Insets(0, 0, 10, 10), -1, -1)
		);
		browsePanel.add(
			panel6,
			new GridConstraints(
				5,
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
		openFolderButton = new JButton();
		openFolderButton.setText("Open script folder");
		panel6.add(
			openFolderButton,
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
		final Spacer spacer5 = new Spacer();
		panel6.add(
			spacer5,
			new GridConstraints(
				0,
				0,
				1,
				1,
				GridConstraints.ANCHOR_CENTER,
				GridConstraints.FILL_VERTICAL,
				1,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				null,
				new Dimension(-1, 10),
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
