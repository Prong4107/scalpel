package lexfo.scalpel;

import com.google.common.collect.ImmutableSet;
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

	public static final ImmutableSet<String> VALID_HOOK_PREFIXES = ImmutableSet.of(
		REQ_EDIT_PREFIX,
		RES_EDIT_PREFIX,
		REQ_CB_NAME,
		RES_CB_NAME
	);

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
	public static final String[] DEFAULT_VENV_DEPENDENCIES = new String[] {
		"jep",
	};

	// TODO: use a requirements.txt
	/**
	 * Required python packages
	 *
	 * Note: 99% of this dependencies come from mitmproxy.
	 */
	public static final String[] PYTHON_DEPENDENCIES = new String[] {
		"asgiref==3.5.2",
		"Brotli==1.0.9",
		"certifi==2023.7.22",
		"cffi==1.15.1",
		"charset-normalizer==3.2.0",
		"click==8.1.7",
		"cryptography==38.0.4",
		"Flask==2.2.5",
		"h11==0.14.0",
		"h2==4.1.0",
		"hpack==4.0.0",
		"hyperframe==6.0.1",
		"idna==3.4",
		"itsdangerous==2.1.2",
		"jep==4.1.1",
		"Jinja2==3.1.2",
		"kaitaistruct==0.10",
		"ldap3==2.9.1",
		"MarkupSafe==2.1.3",
		"mitmproxy==9.0.0",
		"mitmproxy_wireguard==0.1.23",
		"msgpack==1.0.6",
		"passlib==1.7.4",
		"protobuf==4.24.3",
		"publicsuffix2==2.20191221",
		"pyasn1==0.5.0",
		"pycparser==2.21",
		"pyOpenSSL==22.1.0",
		"pyparsing==3.0.9",
		"pyperclip==1.8.2",
		"requests==2.31.0",
		"requests-toolbelt==1.0.0",
		"ruamel.yaml==0.17.32",
		"ruamel.yaml.clib==0.2.7",
		"six==1.16.0",
		"sortedcontainers==2.4.0",
		"tornado==6.3.3",
		"urllib3==2.0.5",
		"urwid==2.1.2",
		"Werkzeug==2.3.7",
		"wsproto==1.2.0",
		"zstandard==0.18.0",
	};

	/**
	 * Venv dir containing site-packages
	 */
	public static final String VENV_LIB_DIR = UIUtil.isWindows ? "Lib" : "lib";

	/**
	 * JEP native library filename
	 */
	public static final String NATIVE_LIBJEP_FILE = UIUtil.isWindows
		? "jep.dll"
		: UIUtil.isMac ? "libjep.jnilib" : "libjep.so";

	/**
	 * Python 3 executable filename
	 */
	public static final String PYTHON_BIN = UIUtil.isWindows
		? "python.exe"
		: "python3";

	public static final String PIP_BIN = UIUtil.isWindows ? "pip.exe" : "pip";
	public static final String VENV_BIN_DIR = UIUtil.isWindows
		? "Scripts"
		: "bin";

	public static final String DEFAULT_TERMINAL_EDITOR = "vi";

	public static final String DEFAULT_WINDOWS_EDITOR = "notepad.exe";

	public static final String EDITOR_MODE_ANNOTATION_KEY =
		"scalpel_editor_mode";
	public static final String HEX_EDITOR_MODE = "hex";
	public static final String RAW_EDITOR_MODE = "raw";
	public static final String DEFAULT_EDITOR_MODE = RAW_EDITOR_MODE;
}
