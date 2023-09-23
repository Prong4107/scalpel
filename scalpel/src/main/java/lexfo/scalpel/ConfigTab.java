package lexfo.scalpel;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.Theme;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.UIUtil;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
	private JLabel scriptPathTextArea;
	private JediTermWidget terminalForVenvConfig;
	private JList<String> venvListComponent;
	private JTable packagesTable;
	private JTextField addVentText;
	private JButton addVenvButton;
	private JPanel venvSelectPanel;
	private JButton editButton;
	private JButton createButton;
	private JList<String> venvScriptList;
	private JPanel listPannel;
	private JButton openFolderButton;
	private final ScalpelExecutor scalpelExecutor;
	private final Config config;
	private final Theme theme;
	private final MontoyaApi API;
	private final Frame burpFrame;

	public ConfigTab(
		MontoyaApi API,
		ScalpelExecutor executor,
		Config config,
		Theme theme
	) {
		this.config = config;
		this.scalpelExecutor = executor;
		this.theme = theme;
		this.API = API;
		this.burpFrame = API.userInterface().swingUtils().suiteFrame();

		$$$setupUI$$$();

		// Open file browser to select the script to execute.
		scriptBrowseButton.addActionListener(e ->
			handleBrowseButtonClick(
				() -> RessourcesUnpacker.DEFAULT_SCRIPT_PATH,
				this::setAndStoreScript
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

		addListDoubleClickListener(
			venvListComponent,
			this::handleVenvListSelectionEvent
		);

		venvScriptList.addListSelectionListener(
			this::handleScriptListSelectionEvent
		);

		addListDoubleClickListener(
			venvScriptList,
			__ -> {
				final var val = config
					.getSelectedWorkspacePath()
					.resolve(venvScriptList.getSelectedValue());

				openDesktopEditor(val);
				openEditorInTerminal(val);
			}
		);

		// Add a new venv when the user clicks the button.
		addVenvButton.addActionListener(e -> handleVenvButton());

		// Add a new venv when the user presses enter in the text field.
		addVentText.addActionListener(e -> handleVenvButton());

		editButton.addActionListener(e -> handleEditButton());

		createButton.addActionListener(e -> handleNewScriptButton());

		openFolderButton.addActionListener(e -> handleOpenScriptFolderButton());
	}

	/**
	 * JList doesn't natively support double click events, so we implment it ourselves.
	 *
	 * @param <T>
	 * @param list    The list to add the listener to.
	 * @param handler The listener handler callback.
	 */
	private <T> void addListDoubleClickListener(
		JList<T> list,
		Consumer<ListSelectionEvent> handler
	) {
		list.addMouseListener(
			new MouseAdapter() {
				public void mouseClicked(MouseEvent evt) {
					if (evt.getClickCount() != 2) {
						// Not a double click
						return;
					}

					// Get the selected list elem from the click coordinates
					final var selectedIndex = list.locationToIndex(
						evt.getPoint()
					);

					// Convert the MouseEvent into a corresponding ListSelectionEvent
					final var passedEvent = new ListSelectionEvent(
						evt.getSource(),
						selectedIndex,
						selectedIndex,
						false
					);

					handler.accept(passedEvent);
				}
			}
		);
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
		final File folder = config.getUserScriptPath().toFile().getParentFile();

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
		final Optional<String> selected = Optional.ofNullable(
			venvScriptList.getSelectedValue()
		);

		selected.ifPresent(s -> {
			final Path path = config
				.getSelectedWorkspacePath()
				.resolve(s)
				.toAbsolutePath();

			selectScript(path);
		});
	}

	private void updateScriptList() {
		Async.run(() -> {
			final JList<String> list = this.venvScriptList;
			final File selectedVenv = config
				.getSelectedWorkspacePath()
				.toFile();
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
		});
	}

	private void selectScript(Path path) {
		// Select the script
		config.setUserScriptPath(path);

		// Reload the executor
		Async.run(scalpelExecutor::notifyEventLoop);

		// Display the script in the terminal.
		openEditorInTerminal(path);
	}

	private void handleNewScriptButton() {
		final File venv = config.getSelectedWorkspacePath().toFile();

		// Prompt the user for a name
		String fileName = JOptionPane.showInputDialog(
			burpFrame,
			"Enter the name for the new script"
		);

		if (fileName == null || fileName.trim().isEmpty()) {
			// The user didn't enter a name
			JOptionPane.showMessageDialog(
				burpFrame,
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
				burpFrame,
				"File was successfully created!"
			);

			final Path absolutePath = destination.toAbsolutePath();

			selectScript(absolutePath);
			updateScriptList();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(
				burpFrame,
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
	private boolean openDesktopEditor(Path fileToEdit) {
		if (!Desktop.isDesktopSupported()) {
			ScalpelLogger.error("Desktop is not supported");
			return false;
		}

		final Desktop desktop = Desktop.getDesktop();
		final Desktop.Action action;
		if (!desktop.isSupported(Desktop.Action.EDIT)) {
			ScalpelLogger.error("Desktop action EDIT is not supported");
			if (!desktop.isSupported(Desktop.Action.OPEN)) {
				ScalpelLogger.error("Desktop action OPEN is not supported");
				return false;
			}
			action = Desktop.Action.OPEN;
		} else {
			action = Desktop.Action.EDIT;
		}

		try {
			// Provide the full path to the Python file
			final File file = fileToEdit.toFile();
			switch (action) {
				case OPEN:
					desktop.open(file);
					break;
				case EDIT:
					desktop.edit(file);
					break;
				default:
					break;
			}
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
	private void openEditorInTerminal(Path fileToEdit) {
		final Optional<String> envEditor = Optional.ofNullable(
			System.getenv("EDITOR")
		);

		// Set default value
		final String editor = envEditor.orElse(
			Constants.DEFAULT_TERMINAL_EDITOR
		);
		final String cmd =
			editor + " " + Terminal.escapeshellarg(fileToEdit.toString());

		final String cwd = fileToEdit.getParent().toString();

		this.updateTerminal(
				config.getSelectedWorkspacePath().toString(),
				cwd,
				cmd
			);
	}

	private void handleEditButton() {
		final Path script = config.getUserScriptPath();
		if (openDesktopEditor(script)) {
			return;
		}

		if (UIUtil.isWindows) {
			updateTerminal(
				config.getSelectedWorkspacePath().toString(),
				script.getParent().toString(),
				Constants.DEFAULT_WINDOWS_EDITOR + " " + script
			);
			return;
		}

		openEditorInTerminal(script);
	}

	private void installDefaultsAndLog(Path venv)
		throws IOException, InterruptedException {
		final Process proc = Venv.installDefaults(venv, Map.of(), false);
		final var stdout = proc.inputReader();

		while (proc.isAlive()) {
			Optional
				.ofNullable(stdout.readLine())
				.ifPresent(ScalpelLogger::all);
		}
	}

	private void handleVenvButton() {
		final String value = addVentText.getText().trim();

		if (value.isEmpty()) {
			return;
		}

		final Path path;
		try {
			if ((new File(value).isAbsolute())) {
				// The user provided an absolute path, use it as is.
				path = Path.of(value);
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
					Paths.get(
						Workspace.getWorkspacesDir().getAbsolutePath(),
						value
					);
			}
		} catch (IllegalArgumentException e) {
			JOptionPane.showMessageDialog(
				burpFrame,
				e.getMessage(),
				"Invalid venv name or absolute path",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}

		WorkingPopup.showBlockingWaitDialog(
			"Creating venv and installing required packages...",
			label -> {
				// Clear the text field.
				addVentText.setEditable(false);
				addVentText.setText("Please wait ...");

				// Create the venv and installed required packages. (i.e. mitmproxy)
				try {
					Workspace.createAndInitWorkspace(
						path,
						Optional.of(config.getJdkPath()),
						Optional.of(terminalForVenvConfig.getTerminal())
					);

					// Add the venv to the config.
					config.addVenvPath(path);

					// Clear the text field.
					addVentText.setText("");

					// Display the venv in the list.
					venvListComponent.setListData(config.getVenvPaths());

					venvListComponent.setSelectedIndex(
						config.getVenvPaths().length - 1
					);
				} catch (RuntimeException e) {
					final String msg =
						"Failed to create venv: \n" + e.getMessage();
					ScalpelLogger.error(msg);
					ScalpelLogger.logStackTrace(e);
					JOptionPane.showMessageDialog(
						burpFrame,
						msg,
						"Failed to create venv",
						JOptionPane.ERROR_MESSAGE
					);
				}
				addVentText.setEditable(true);
			}
		);
	}

	private synchronized void updateTerminal(
		String selectedVenvPath,
		String cwd,
		String cmd
	) {
		final var termWidget = this.terminalForVenvConfig;
		final var oldConnector = termWidget.getTtyConnector();

		// Close asynchronously to avoid losing time.
		termWidget.stop();
		// Kill the old process.
		oldConnector.close();

		final var term = termWidget.getTerminal();
		final int width = term.getTerminalWidth();
		final int height = term.getTerminalHeight();
		final Dimension dimension = new Dimension(width, height);

		// Start the process while the terminal is closing
		final var connector = Terminal.createTtyConnector(
			selectedVenvPath,
			Optional.of(dimension),
			Optional.ofNullable(cwd),
			Optional.ofNullable(cmd)
		);

		// Connect the terminal to the new process in the new venv.
		termWidget.setTtyConnector(connector);

		term.reset();
		term.cursorPosition(0, 0);

		// Start the terminal.
		termWidget.start();
	}

	private void updateTerminal(String selectedVenvPath) {
		updateTerminal(selectedVenvPath, null, null);
	}

	private void handleVenvListSelectionEvent(ListSelectionEvent e) {
		// Ignore intermediate events.
		if (e.getValueIsAdjusting()) {
			return;
		}

		// Get the selected venv path.
		final String selectedVenvPath = venvListComponent.getSelectedValue();

		if (selectedVenvPath == null) {
			return;
		}

		config.setSelectedVenvPath(Path.of(selectedVenvPath));

		Async.run(scalpelExecutor::notifyEventLoop);

		// Update the package table.
		Async.run(this::updatePackagesTable);
		Async.run(() -> updateTerminal(selectedVenvPath));
		Async.run(this::updateScriptList);
	}

	private CompletableFuture<Void> handleBrowseButtonClick(
		Supplier<Path> getter,
		Consumer<Path> setter
	) {
		return Async.run(() -> {
			final JFileChooser fileChooser = new JFileChooser();

			// Allow the user to only select files.
			fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

			// Set default path to the path in the text field.
			fileChooser.setCurrentDirectory(getter.get().toFile());

			final int result = fileChooser.showOpenDialog(this);

			// When the user selects a file, set the text field to the selected file.
			if (result == JFileChooser.APPROVE_OPTION) {
				setter.accept(
					fileChooser.getSelectedFile().toPath().toAbsolutePath()
				);
			}
		});
	}

	private CompletableFuture<Void> updatePackagesTable(
		Consumer<JTable> onSuccess,
		Runnable onFail
	) {
		return Async.run(() -> {
			final PackageInfo[] installedPackages;
			try {
				installedPackages =
					Venv.getInstalledPackages(
						config.getSelectedWorkspacePath()
					);
			} catch (IOException e) {
				JOptionPane.showMessageDialog(
					burpFrame,
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
		});
	}

	private void updatePackagesTable(Consumer<JTable> onSuccess) {
		updatePackagesTable(onSuccess, () -> {});
	}

	private void updatePackagesTable() {
		updatePackagesTable(__ -> {});
	}

	private void setAndStoreScript(final Path path) {
		final Path copied;
		try {
			copied =
				Workspace.copyScriptToWorkspace(
					config.getSelectedWorkspacePath(),
					path
				);
		} catch (RuntimeException e) {
			// Error popup
			JOptionPane.showMessageDialog(
				burpFrame,
				e.getMessage(),
				"Could not copy script to venv.",
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}

		// Store the path in the config. (writes to disk)
		config.setUserScriptPath(copied);
		Async.run(scalpelExecutor::notifyEventLoop);

		Async.run(this::updateScriptList);
		Async.run(() -> selectScript(copied));
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
			Terminal.createTerminal(
				theme,
				config.getSelectedWorkspacePath().toString()
			);
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
			new GridLayoutManager(7, 3, new Insets(3, 3, 3, 3), 0, -1)
		);
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
				new Dimension(300, -1),
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
			new GridLayoutManager(2, 1, new Insets(0, 0, 10, 10), -1, -1)
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
		scriptPathTextArea = new JLabel();
		scriptPathTextArea.setText("Load script file");
		scriptConfigPanel.add(
			scriptPathTextArea,
			new GridConstraints(
				0,
				0,
				1,
				1,
				GridConstraints.ANCHOR_SOUTH,
				GridConstraints.FILL_NONE,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				GridConstraints.SIZEPOLICY_WANT_GROW,
				null,
				new Dimension(-1, 10),
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
		final Spacer spacer2 = new Spacer();
		panel4.add(
			spacer2,
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
		editButton.setText("Open selected script");
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
		final Spacer spacer3 = new Spacer();
		panel5.add(
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
		label1.setText("  Scripts available for this venv:  ");
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
		final Spacer spacer4 = new Spacer();
		panel6.add(
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
	}

	/**
	 * @noinspection ALL
	 */
	public JComponent $$$getRootComponent$$$() {
		return rootPanel;
	}
}
