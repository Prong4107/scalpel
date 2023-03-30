package lexfo.scalpel;

import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.message.HttpMessage;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.InterceptedResponse;
import java.util.stream.IntStream;

/**
  Utility class for Python scripts.
*/
public class PythonUtils {

	/**
	 * Convert Java signed bytes to corresponding unsigned values
	 * Convertions issues occur when passing Java bytes to Python because Java's are signed and Python's are unsigned.
	 * Passing an unsigned int array solves this problem.
	 *
	 *
	 * @param javaBytes the bytes to convert
	 * @return the corresponding unsigned values as int
	 */
	public static int[] toPythonBytes(byte[] javaBytes) {
		return IntStream
			.range(0, javaBytes.length)
			.map(i -> Byte.toUnsignedInt(javaBytes[i]))
			.toArray();
	}

	/**
	 * Convert Python bytes to Java bytes
	 *
	 * It is not possible to convert to Java bytes Python side without a Java helper like this one,
	 * 	because Jep doesn't natively support the convertion:
	 * 	https://github.com/ninia/jep/wiki/How-Jep-Works#objects
	 *
	 * When passing byte[],
	 * 	Python receives a PyJArray of integer-like objects which will be mapped back to byte[] by Jep.
	 *
	 * This can be used to avoid type errors by avoding Jep's conversion by passing a native Java object.
	 *
	 * @param pythonBytes the unsigned values to convert
	 * @return the corresponding signed bytes
	 */
	public static byte[] toJavaBytes(byte[] pythonBytes) {
		return pythonBytes;
	}

	/**
    Updates the specified HttpMessage object's header with the specified name and value.
    Creates the header when it doesn't exist.
    <p> (Burp's withUpdatedHeader() method does not create the header.)

    @param <T> The type of the HttpMessage object.
    @param msg The HttpMessage object to update.
    @param name The name of the header to update.
    @param value The value of the header to update.
    @return The updated HttpMessage object.
  */
	@SuppressWarnings({ "unchecked" })
	public static <T extends HttpMessage> T updateHeader(
		T msg,
		String name,
		String value
	) {
		final String methName = msg
				.headers()
				.stream()
				.filter(header -> header.name().equalsIgnoreCase(name))
				.findAny()
				.isPresent()
			? "withUpdatedHeader"
			: "withAddedHeader";

		try {
			final var meth = msg
				.getClass()
				.getMethod(methName, String.class, String.class);

			return (T) meth.invoke(msg, name, value);
		} catch (Exception e) {
			e.printStackTrace();
		}

		throw new RuntimeException(
			"Wrong type " +
			msg.getClass().getSimpleName() +
			" was passed to updateHeader()"
		);
	}
}
