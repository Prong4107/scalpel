package lexfo.scalpel;

import burp.api.montoya.ui.Theme;
import java.awt.*;

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
}
