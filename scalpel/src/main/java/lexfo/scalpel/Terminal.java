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
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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
		String venvPath
	) {
		JediTermWidget widget = new JediTermWidget(
			createSettingsProvider(theme)
		);
		widget.setTtyConnector(createTtyConnector(venvPath));
		widget.start();
		return widget;
	}

	private static String escapeshellarg(String str) {
		return "'" + str.replace("'", "'\\''") + "'";
	}

	/**
	 * Creates a TtyConnector that will run a shell in the virtualenv.
	 *
	 * @param venvPath The path to the virtualenv.
	 * @return The TtyConnector.
	 */
	public static TtyConnector createTtyConnector(String venvPath) {
		Map<String, String> envs = System.getenv();
		String[] command;

		final String sep = File.separator;
		final String binDir = UIUtil.isWindows ? "Scripts" : "bin";
		final String activatePath = venvPath + sep + binDir + sep + "activate";

		if (UIUtil.isWindows) {
			command = new String[] { "cmd.exe", "/K", activatePath };
		} else {
			// Override the default bash load order to ensure that the virtualenv activate script is correctly loaded
			// and we don't lose any interactive functionality.
			// Also reset the terminal to clear any previous state.
			final String tmpInitFileContent =
				"""
			reset
			 2>/dev/null . /etc/bashrc.bashrc
			 2>/dev/null . ~/.bashrc
			 2>/dev/null . /etc/profile
			 2>/dev/null . ~/.bash_profile
			 2>/dev/null . ~/.profile
			 2>/dev/null . \t """ +
				escapeshellarg(activatePath);

			// Create a temporary file to store the init script.
			final File initFile = IO.ioWrap(() ->
				File.createTempFile("scalpel-term", ".sh")
			);

			final String initFilePath = initFile.getAbsolutePath();

			IO.writeFile(initFilePath, tmpInitFileContent);

			command =
				new String[] { "/bin/bash", "--init-file", initFilePath, "-i" };

			// Tell the shell the terminal is xterm like.
			envs = new HashMap<>(envs);
			envs.put("TERM", "xterm-256color");
		}

		try {
			// Start the process in the virtualenv directory.
			final PtyProcess process = new PtyProcessBuilder()
				.setCommand(command)
				.setEnvironment(envs)
				.setDirectory(venvPath)
				.start();
			return new PtyProcessTtyConnector(process, StandardCharsets.UTF_8);
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
		return createTerminalWidget(theme, venvPath);
	}
}
