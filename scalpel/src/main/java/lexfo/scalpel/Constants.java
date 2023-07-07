package lexfo.scalpel;

import com.jediterm.terminal.ui.UIUtil;

/**
  Contains constants used by the extension.
*/
public class Constants {

	public static final String REQ_EDIT_PREFIX = "req_edit_";

	/**
    	Callback prefix for request editors.
	*/
	public static final String FRAMEWORK_REQ_EDIT_PREFIX =
		"_" + REQ_EDIT_PREFIX;

	public static final String RES_EDIT_PREFIX = "res_edit_";

	/**
    	Callback prefix for response editors.
	*/
	public static final String FRAMEWORK_RES_EDIT_PREFIX =
		"_" + RES_EDIT_PREFIX;

	/**
    	Callback suffix for HttpMessage-to-bytes convertion.
	*/
	public static final String IN_SUFFIX = "in";

	/**
    	Callback suffix for bytes to HttpMessage convertion.
	*/
	public static final String OUT_SUFFIX = "out";

	public static final String REQ_CB_NAME = "request";

	/**
    	Callback prefix for request intercepters.
	*/
	public static final String FRAMEWORK_REQ_CB_NAME = "_" + REQ_CB_NAME;

	public static final String RES_CB_NAME = "response";

	/**
    	Callback prefix for response intercepters.
	*/
	public static final String FRAMEWORK_RES_CB_NAME = "_" + RES_CB_NAME;

	/**
		Scalpel prefix for the persistence databases.

		@see burp.api.montoya.persistence.Persistence
	*/
	public static final String PERSISTENCE_PREFIX = "scalpel:";

	/**
		Persistence key for the cached user script path.
	*/
	public static final String PERSISTED_SCRIPT =
		PERSISTENCE_PREFIX + "script_path";

	/**
		Persistence key for the cached framework path.
	*/
	public static final String PERSISTED_FRAMEWORK =
		PERSISTENCE_PREFIX + "framework_path";

	public static final String GET_CB_NAME = "_get_callables";

	/**
	 * Required python packages
	 */
	public static final String[] PYTHON_DEPENDENCIES = new String[] {
		"jep",
		"requests",
		"requests-toolbelt",
		"mitmproxy",
	};

	/**
	 * Venv dir containing site-packages
	 */
	public static final String VENV_LIB_DIR = Config.isWindows()
		? "Lib"
		: "lib";

	/**
	 * JEP native library filename
	 */
	public static final String NATIVE_LIBJEP_FILE = Config.isWindows()
		? "jep.dll"
		: UIUtil.isMac ? "libjep.jnilib": "libjep.so";

	/**
	 * Python 3 executable filename
	 */
	public static final String PYTHON_BIN = Config.isWindows()
		? "python.exe"
		: "python3";
}
