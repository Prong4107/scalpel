package lexfo.scalpel;

import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.message.HttpMessage;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.InterceptedResponse;

/**
  Utility class for HttpMessage objects.
*/
public class HttpMsgUtils {

  /**
    Returns the real class name of the specified HttpMessage object.

    @param <T> The type of the HttpMessage object.
    @param msg The HttpMessage object to get the class name of.
    @return The class name of the specified HttpMessage object.
  */
  public static <T extends HttpMessage> String getClassName(T msg) {
    // Retrieve real type.
    // We cannot naively use msg.getClass() so we have no choice but to use an instanceof if forest.
    if (msg instanceof InterceptedRequest) return "InterceptedRequest";
    if (msg instanceof InterceptedResponse) return "InterceptedResponse";
    if (msg instanceof HttpRequestToBeSent) return "HttpRequestToBeSent";
    if (msg instanceof HttpResponseReceived) return "HttpResponseReceived";
    if (msg instanceof HttpRequest) return "HttpRequest";
    if (msg instanceof HttpResponse) return "HttpResponse";

    // This exception shouldn't happen as all concrete instances of an HttpMessage is one of the above.
    throw new RuntimeException(
      "Wrong type " +
      msg.getClass().getSimpleName() +
      " was passed to getClassName()"
    );
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
    String methName = msg
        .headers()
        .stream()
        .filter(header -> header.name() == name)
        .findAny()
        .isPresent()
      ? "withUpdatedHeader"
      : "withAddedHeader";

    try {
      var meth = msg.getClass().getMethod(methName, String.class, String.class);

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
