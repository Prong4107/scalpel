from abc import abstractmethod
from .http_message import IHttpMessage
from .byte_array import IByteArray
from burp.api.montoya.http.message.responses import HttpResponse as _BurpHttpResponse

#  * Burp HTTP response able to retrieve and modify details about an HTTP response.
#


class IHttpResponse(IHttpMessage):
    """ generated source for interface HttpResponse """
    #
    #      * Obtain the HTTP status code contained in the response.
    #      *
    #      * @return HTTP status code.
    #
    @abstractmethod
    def statusCode(self) -> int:
        """ generated source for method statusCode """

    #
    #      * Obtain the HTTP reason phrase contained in the response for HTTP 1 messages.
    #      * HTTP 2 messages will return a mapped phrase based on the status code.
    #      *
    #      * @return HTTP Reason phrase.
    #
    @abstractmethod
    def reasonPhrase(self) -> str:
        """ generated source for method reasonPhrase """

    #
    #      * Return the HTTP Version text parsed from the response line for HTTP 1 messages.
    #      * HTTP 2 messages will return "HTTP/2"
    #      *
    #      * @return Version string
    #
    @abstractmethod
    def httpVersion(self) -> str:
        """ generated source for method httpVersion """

    #
    #      * {@inheritDoc}
    #
    @abstractmethod
    def headers(self):
        """ generated source for method headers """

    #
    #      * {@inheritDoc}
    #
    @abstractmethod
    def body(self) -> IByteArray:
        """ generated source for method body """

    #
    #      * {@inheritDoc}
    #
    @abstractmethod
    def bodyToString(self) -> str:
        """ generated source for method bodyToString """

    #
    #      * {@inheritDoc}
    #
    @abstractmethod
    def bodyOffset(self) -> int:
        """ generated source for method bodyOffset """

    #
    #      * {@inheritDoc}
    #
    @abstractmethod
    def markers(self):
        """ generated source for method markers """

    #
    #      * Obtain details of the HTTP cookies set in the response.
    #      *
    #      * @return A list of {@link Cookie} objects representing the cookies set in the response, if any.
    #
    @abstractmethod
    def cookies(self):
        """ generated source for method cookies """

    #
    #      * Obtain the MIME type of the response, as stated in the HTTP headers.
    #      *
    #      * @return The stated MIME type.
    #
    @abstractmethod
    def statedMimeType(self):
        """ generated source for method statedMimeType """

    #
    #      * Obtain the MIME type of the response, as inferred from the contents of the HTTP message body.
    #      *
    #      * @return The inferred MIME type.
    #
    @abstractmethod
    def inferredMimeType(self):
        """ generated source for method inferredMimeType """

    #
    #      * Retrieve the number of types given keywords appear in the response.
    #      *
    #      * @param keywords Keywords to count.
    #      *
    #      * @return List of keyword counts in the order they were provided.
    #
    @abstractmethod
    def keywordCounts(self, *keywords):
        """ generated source for method keywordCounts """

    #
    #      * Retrieve the values of response attributes.
    #      *
    #      * @param types Response attributes to retrieve values for.
    #      *
    #      * @return List of {@link Attribute} objects.
    #
    @abstractmethod
    def attributes(self, *types):
        """ generated source for method attributes """

    #
    #      * {@inheritDoc}
    #
    @abstractmethod
    def toByteArray(self) -> IByteArray:
        """ generated source for method toByteArray """

    #
    #      * {@inheritDoc}
    #
    @abstractmethod
    def __str__(self) -> str:
        """ generated source for method toString """

    #
    #      * Create a copy of the {@code HttpResponse} in temporary file.<br>
    #      * This method is used to save the {@code HttpResponse} object to a temporary file,
    #      * so that it is no longer held in memory. Extensions can use this method to convert
    #      * {@code HttpResponse} objects into a form suitable for long-term usage.
    #      *
    #      * @return A new {@code HttpResponse} instance stored in temporary file.
    #
    @abstractmethod
    def copyToTempFile(self) -> 'IHttpResponse':
        """ generated source for method copyToTempFile """

    #
    #      * Create a copy of the {@code HttpResponse} with the provided status code.
    #      *
    #      * @param statusCode the new status code for response
    #      *
    #      * @return A new {@code HttpResponse} instance.
    #
    @abstractmethod
    def withStatusCode(self, statusCode) -> 'IHttpResponse':
        """ generated source for method withStatusCode """

    #
    #      * Create a copy of the {@code HttpResponse} with the new reason phrase.
    #      *
    #      * @param reasonPhrase the new reason phrase for response
    #      *
    #      * @return A new {@code HttpResponse} instance.
    #
    @abstractmethod
    def withReasonPhrase(self, reasonPhrase) -> 'IHttpResponse':
        """ generated source for method withReasonPhrase """

    #
    #      * Create a copy of the {@code HttpResponse} with the new http version.
    #      *
    #      * @param httpVersion the new http version for response
    #      *
    #      * @return A new {@code HttpResponse} instance.
    #
    @abstractmethod
    def withHttpVersion(self, httpVersion) -> 'IHttpResponse':
        """ generated source for method withHttpVersion """

    #
    #      * Create a copy of the {@code HttpResponse} with the updated body.<br>
    #      * Updates Content-Length header.
    #      *
    #      * @param body the new body for the response
    #      *
    #      * @return A new {@code HttpResponse} instance.
    #
    @abstractmethod
    def withBody(self, body) -> 'IHttpResponse':
        """ generated source for method withBody """

    #
    #      * Create a copy of the {@code HttpResponse} with the updated body.<br>
    #      * Updates Content-Length header.
    #      *
    #      * @param body the new body for the response
    #      *
    #      * @return A new {@code HttpResponse} instance.
    #
    @abstractmethod
    def withBody_0(self, body) -> 'IHttpResponse':
        """ generated source for method withBody_0 """

    #
    #      * Create a copy of the {@code HttpResponse} with the added header.
    #      *
    #      * @param header The {@link HttpHeader} to add to the response.
    #      *
    #      * @return The updated response containing the added header.
    #
    # @abstractmethod
    # def withAddedHeader(self, header) -> 'IHttpResponse':
    #     """ generated source for method withAddedHeader """

    # #
    # #      * Create a copy of the {@code HttpResponse}  with the added header.
    # #      *
    # #      * @param name  The name of the header.
    # #      * @param value The value of the header.
    # #      *
    # #      * @return The updated response containing the added header.
    # #
    @abstractmethod
    def withAddedHeader(self, name, value) -> 'IHttpResponse':
        """ generated source for method withAddedHeader_0 """

    #
    #      * Create a copy of the {@code HttpResponse}  with the updated header.
    #      *
    #      * @param header The {@link HttpHeader} to update containing the new value.
    #      *
    #      * @return The updated response containing the updated header.
    #
    # @abstractmethod
    # def withUpdatedHeader(self, header) -> 'IHttpResponse':
    #     """ generated source for method withUpdatedHeader """

    # #
    # #      * Create a copy of the {@code HttpResponse}  with the updated header.
    # #      *
    # #      * @param name  The name of the header to update the value of.
    # #      * @param value The new value of the specified HTTP header.
    # #      *
    # #      * @return The updated response containing the updated header.
    # #
    @abstractmethod
    def withUpdatedHeader(self, name, value) -> 'IHttpResponse':
        """ generated source for method withUpdatedHeader_0 """

    #
    #      * Create a copy of the {@code HttpResponse}  with the removed header.
    #      *
    #      * @param header The {@link HttpHeader} to remove from the response.
    #      *
    #      * @return The updated response containing the removed header.
    #
    # @abstractmethod
    # def withRemovedHeader(self, header) -> 'IHttpResponse':
    #     """ generated source for method withRemovedHeader """

    # #
    # #      * Create a copy of the {@code HttpResponse}  with the removed header.
    # #      *
    # #      * @param name The name of the HTTP header to remove from the response.
    # #      *
    # #      * @return The updated response containing the removed header.
    # #
    @abstractmethod
    def withRemovedHeader(self, name) -> 'IHttpResponse':
        """ generated source for method withRemovedHeader_0 """

    #
    #      * Create a copy of the {@code HttpResponse} with the added markers.
    #      *
    #      * @param markers Request markers to add.
    #      *
    #      * @return A new {@code MarkedHttpRequestResponse} instance.
    #
    @abstractmethod
    def withMarkers(self, markers) -> 'IHttpResponse':
        """ generated source for method withMarkers """

    #
    #      * Create a copy of the {@code HttpResponse} with the added markers.
    #      *
    #      * @param markers Request markers to add.
    #      *
    #      * @return A new {@code MarkedHttpRequestResponse} instance.
    #
    @abstractmethod
    def withMarkers_0(self, *markers) -> 'IHttpResponse':
        """ generated source for method withMarkers_0 """

    #
    #      * Create a new empty instance of {@link HttpResponse}.<br>
    #      *
    #      * @return A new {@link HttpResponse} instance.
    #
    @abstractmethod
    def httpResponse(self, response) -> 'IHttpResponse':
        """ generated source for method httpResponse """


HttpResponse: IHttpResponse = _BurpHttpResponse
