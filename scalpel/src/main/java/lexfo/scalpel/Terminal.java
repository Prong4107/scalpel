// https://raw.githubusercontent.com/JetBrains/jediterm/7e42fc1261ffd0b593557afa71851f1d1df76804/JediTerm/src/main/java/com/jediterm/example/BasicTerminalShellExample.java
package lexfo.scalpel;

import burp.api.montoya.ui.Theme;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.UIUtil;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.awt.Dimension;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Terminal {

	private static SettingsProvider createSettingsProvider(Theme theme) {
		SettingsProvider userSettingsProvider = new DefaultSettingsProvider() {
			@Override
			public TextStyle getDefaultStyle() {
				return theme == Theme.DARK
					? new TextStyle(TerminalColor.WHITE, TerminalColor.BLACK)
					: new TextStyle(TerminalColor.BLACK, TerminalColor.WHITE);
			}

			@Override
			public ColorPalette getTerminalColorPalette() {
				return theme == Theme.DARK
					? Palette.DARK_PALETTE
					: Palette.LIGHT_PALETTE;
			}
		};

		return userSettingsProvider;
	}

	private static JediTermWidget createTerminalWidget(
		Theme theme,
		String venvPath,
		Optional<String> cwd,
		Optional<String> cmd
	) {
		JediTermWidget widget = new JediTermWidget(
			createSettingsProvider(theme)
		);
		widget.setTtyConnector(
			createTtyConnector(venvPath, Optional.empty(), cwd, cmd)
		);
		widget.start();
		return widget;
	}

	public static String escapeshellarg(String str) {
		if (UIUtil.isWindows) {
			// Handle cmd.exe
			final String specialChars = "&|<>^";

			return Stream
				.of(specialChars.split(""))
				.reduce(str, (s, ch) -> s.replace(ch, "^" + ch));
		}
		// Handle posix-y shell
		return "'" + str.replace("'", "'\\''") + "'";
	}

	/**
	 * Creates a TtyConnector that will run a shell in the virtualenv.
	 *
	 * @param venvPath The path to the virtualenv.
	 * @return The TtyConnector.
	 */
	public static TtyConnector createTtyConnector(String venvPath) {
		return createTtyConnector(
			venvPath,
			Optional.empty(),
			Optional.empty(),
			Optional.empty()
		);
	}

	/**
	 * Creates a TtyConnector that will run a shell in the virtualenv.
	 *
	 * @param workspacePath The path to the virtualenv.
	 * @return The TtyConnector.
	 */
	protected static TtyConnector createTtyConnector(
		String workspacePath,
		Optional<Dimension> ttyDimension,
		Optional<String> cwd,
		Optional<String> cmd
	) {
		Map<String, String> env = System.getenv();
		final String[] commandToRun;

		final String sep = File.separator;
		final String binDir = UIUtil.isWindows ? "Scripts" : "bin";
		final String activatePath =
			workspacePath +
			sep +
			Workspace.VENV_DIR +
			sep +
			binDir +
			sep +
			"activate";

		ScalpelLogger.debug("Activating terminal with " + activatePath);

		if (UIUtil.isWindows) {
			commandToRun = new String[] { "cmd.exe", "/K", activatePath };
		} else {
			// Override the default bash load order to ensure that the virtualenv activate script is correctly loaded
			// and we don't lose any interactive functionality.
			// Also reset the terminal to clear any previous state.

			final String initFilePath = RessourcesUnpacker.BASH_INIT_FILE_PATH.toString();

			final String shell = Optional
				.ofNullable(System.getenv("SHELL"))
				.orElse("/bin/bash");

			cmd = cmd.map(c -> c + ";" + escapeshellarg(shell));

			if (cmd.isPresent()) {
				commandToRun =
					new String[] {
						"/bin/bash",
						"--init-file",
						initFilePath,
						"-c",
						cmd.get(),
					};
			} else {
				commandToRun =
					new String[] { "/bin/bash", "--init-file", initFilePath };
			}

			// Tell the shell the terminal is xterm like.
			env = new HashMap<>(env);
			env.put("TERM", "xterm-256color");
			env.put("SCALPEL_VENV_ACTIVATE", activatePath);
		}

		try {
			// Start the process in the virtualenv directory.
			final var builder = new PtyProcessBuilder()
				.setCommand(commandToRun)
				.setEnvironment(env)
				.setDirectory(cwd.orElse(workspacePath));

			ttyDimension.ifPresent(d ->
				builder
					.setInitialRows((int) d.getHeight())
					.setInitialColumns((int) d.getWidth())
			);

			final var processs = builder.start();

			return new PtyProcessTtyConnector(processs, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Creates a JediTermWidget that will run a shell in the virtualenv.
	 *
	 * @param theme The theme to use. (Dark or Light)
	 * @param venvPath The path to the virtualenv.
	 * @return The JediTermWidget.
	 */
	public static JediTermWidget createTerminal(Theme theme, String venvPath) {
		return createTerminalWidget(
			theme,
			venvPath,
			Optional.empty(),
			Optional.empty()
		);
	}

	/**
	 * Creates a JediTermWidget that will run a shell in the virtualenv.
	 *
	 * @param theme The theme to use. (Dark or Light)
	 * @param venvPath The path to the virtualenv.
	 * @param cmd The command to run
	 * @return The JediTermWidget.
	 */
	public static JediTermWidget createTerminal(
		Theme theme,
		String venvPath,
		String cwd,
		String cmd
	) {
		return createTerminalWidget(
			theme,
			venvPath,
			Optional.of(cwd),
			Optional.of(cmd)
		);
	}
}
