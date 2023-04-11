// https://raw.githubusercontent.com/JetBrains/jediterm/7e42fc1261ffd0b593557afa71851f1d1df76804/JediTerm/src/main/java/com/jediterm/example/BasicTerminalShellExample.java
package lexfo.scalpel;

import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.UIUtil;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Terminal {

	private static JediTermWidget createTerminalWidget() {
		JediTermWidget widget = new JediTermWidget(
			80,
			24,
			new DefaultSettingsProvider()
		);
		widget.setTtyConnector(createTtyConnector());
		widget.start();
		return widget;
	}

	private static TtyConnector createTtyConnector() {
		try {
			Map<String, String> envs = System.getenv();
			String[] command;
			if (UIUtil.isWindows) {
				command = new String[] { "cmd.exe" };
			} else {
				command = new String[] { "/bin/bash", "--login" };
				envs = new HashMap<>(System.getenv());
				envs.put("TERM", "xterm-256color");
			}

			PtyProcess process = new PtyProcessBuilder()
				.setCommand(command)
				.setEnvironment(envs)
				.start();
			return new PtyProcessTtyConnector(process, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static JediTermWidget createTerminal() {
		return createTerminalWidget();
	}
}
