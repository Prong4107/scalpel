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
    Callback prefix for HttpMessage-to-bytes convertion.
  */
	public static final String IN_PREFIX = "in_";

	public static final String IN_SUFFIX = "in";

	/**
    Callback prefix for bytes to HttpMessage convertion.
  */
	public static final String OUT_PREFIX = "out_";

	public static final String OUT_SUFFIX = "out";

	/**
    Callback prefix for request intercepters.
  */
	public static final String REQ_CB_NAME = "_request";

	/**
    Callback prefix for response intercepters.
  */
	public static final String RES_CB_NAME = "_response";

	public static final String PERSISTENCE_KEY = "scalpelScript";
}
