package lexfo.scalpel;

/**
  Contains constants used by the extension.
*/
public class Constants {

	/**
    	Callback prefix for request editors.
	*/
	public static final String REQ_EDIT_PREFIX = "_req_edit_";

	/**
    	Callback prefix for response editors.
	*/
	public static final String RES_EDIT_PREFIX = "_res_edit_";

	/**
    	Callback suffix for HttpMessage-to-bytes convertion.
	*/
	public static final String IN_SUFFIX = "in";

	/**
    	Callback suffix for bytes to HttpMessage convertion.
	*/
	public static final String OUT_SUFFIX = "out";

	/**
    	Callback prefix for request intercepters.
	*/
	public static final String REQ_CB_NAME = "_request";

	/**
    	Callback prefix for response intercepters.
	*/
	public static final String RES_CB_NAME = "_response";

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
}
