from typing import Protocol
from abc import abstractmethod

class ByteArray(Protocol):
    """ generated source for interface ByteArray """
    # 
    #      * Access the byte stored at the provided index.
    #      *
    #      * @param index Index of the byte to be retrieved.
    #      *
    #      * @return The byte at the index.
    #      
    @abstractmethod
    def getByte(self, index):
        """ generated source for method getByte """

    # 
    #      * Sets the byte at the provided index to the provided byte.
    #      *
    #      * @param index Index of the byte to be set.
    #      * @param value The byte to be set.
    #      
    @abstractmethod
    def setByte(self, index, value):
        """ generated source for method setByte """

    # 
    #      * Sets the byte at the provided index to the provided narrowed integer value.
    #      *
    #      * @param index Index of the byte to be set.
    #      * @param value The integer value to be set after a narrowing primitive conversion to a byte.
    #      
    @abstractmethod
    def setByte_0(self, index, value):
        """ generated source for method setByte_0 """

    # 
    #      * Sets bytes starting at the specified index to the provided bytes.
    #      *
    #      * @param index The index of the first byte to set.
    #      * @param data  The byte[] or sequence of bytes to be set.
    #      
    @abstractmethod
    def setBytes(self, index, *data):
        """ generated source for method setBytes """

    # 
    #      * Sets bytes starting at the specified index to the provided integers after narrowing primitive conversion to bytes.
    #      *
    #      * @param index The index of the first byte to set.
    #      * @param data  The int[] or the sequence of integers to be set after a narrowing primitive conversion to bytes.
    #      
    @abstractmethod
    def setBytes_0(self, index, *data):
        """ generated source for method setBytes_0 """

    # 
    #      * Sets bytes starting at the specified index to the provided bytes.
    #      *
    #      * @param index     The index of the first byte to set.
    #      * @param byteArray The {@code ByteArray} object holding the provided bytes.
    #      
    @abstractmethod
    def setBytes_1(self, index, byteArray):
        """ generated source for method setBytes_1 """

    # 
    #      * Number of bytes stored in the {@code ByteArray}.
    #      *
    #      * @return Length of the {@code ByteArray}.
    #      
    @abstractmethod
    def length(self) -> int:
        """ generated source for method length """

    # 
    #      * Copy of all bytes
    #      *
    #      * @return Copy of all bytes.
    #      
    @abstractmethod
    def getBytes(self) -> bytes:
        """ generated source for method getBytes """

    # 
    #      * New ByteArray with all bytes between the start index (inclusive) and the end index (exclusive).
    #      *
    #      * @param startIndexInclusive The inclusive start index of retrieved range.
    #      * @param endIndexExclusive   The exclusive end index of retrieved range.
    #      *
    #      * @return ByteArray containing all bytes in the specified range.
    #      
    @abstractmethod
    def subArray(self, startIndexInclusive, endIndexExclusive):
        """ generated source for method subArray """

    # 
    #      * New ByteArray with all bytes in the specified range.
    #      *
    #      * @param range The {@link Range} of bytes to be returned.
    #      *
    #      * @return ByteArray containing all bytes in the specified range.
    #      
    @abstractmethod
    def subArray_0(self, range):
        """ generated source for method subArray_0 """

    # 
    #      * Create a copy of the {@code ByteArray}
    #      *
    #      * @return New {@code ByteArray} with a copy of the wrapped bytes.
    #      
    @abstractmethod
    def copy(self):
        """ generated source for method copy """

    # 
    #      * Create a copy of the {@code ByteArray} in temporary file.<br>
    #      * This method is used to save the {@code ByteArray} object to a temporary file,
    #      * so that it is no longer held in memory. Extensions can use this method to convert
    #      * {@code ByteArray} objects into a form suitable for long-term usage.
    #      *
    #      * @return A new {@code ByteArray} instance stored in temporary file.
    #      
    @abstractmethod
    def copyToTempFile(self):
        """ generated source for method copyToTempFile """

    # 
    #      * Searches the data in the ByteArray for the first occurrence of a specified term.
    #      * It works on byte-based data in a way that is similar to the way the native Java method {@link String#indexOf(String)} works on String-based data.
    #      *
    #      * @param searchTerm The value to be searched for.
    #      *
    #      * @return The offset of the first occurrence of the pattern within the specified bounds, or -1 if no match is found.
    #      
    @abstractmethod
    def indexOf(self, searchTerm):
        """ generated source for method indexOf """

    # 
    #      * Searches the data in the ByteArray for the first occurrence of a specified term.
    #      * It works on byte-based data in a way that is similar to the way the native Java method {@link String#indexOf(String)} works on String-based data.
    #      *
    #      * @param searchTerm The value to be searched for.
    #      *
    #      * @return The offset of the first occurrence of the pattern within the specified bounds, or -1 if no match is found.
    #      
    @abstractmethod
    def indexOf_0(self, searchTerm):
        """ generated source for method indexOf_0 """

    # 
    #      * Searches the data in the ByteArray for the first occurrence of a specified term.
    #      * It works on byte-based data in a way that is similar to the way the native Java method {@link String#indexOf(String)} works on String-based data.
    #      *
    #      * @param searchTerm    The value to be searched for.
    #      * @param caseSensitive Flags whether the search is case-sensitive.
    #      *
    #      * @return The offset of the first occurrence of the pattern within the specified bounds, or -1 if no match is found.
    #      
    @abstractmethod
    def indexOf_1(self, searchTerm, caseSensitive):
        """ generated source for method indexOf_1 """

    # 
    #      * Searches the data in the ByteArray for the first occurrence of a specified term.
    #      * It works on byte-based data in a way that is similar to the way the native Java method {@link String#indexOf(String)} works on String-based data.
    #      *
    #      * @param searchTerm    The value to be searched for.
    #      * @param caseSensitive Flags whether the search is case-sensitive.
    #      *
    #      * @return The offset of the first occurrence of the pattern within the specified bounds, or -1 if no match is found.
    #      
    @abstractmethod
    def indexOf_2(self, searchTerm, caseSensitive):
        """ generated source for method indexOf_2 """

    # 
    #      * Searches the data in the ByteArray for the first occurrence of a specified term.
    #      * It works on byte-based data in a way that is similar to the way the native Java method {@link String#indexOf(String)} works on String-based data.
    #      *
    #      * @param searchTerm          The value to be searched for.
    #      * @param caseSensitive       Flags whether the search is case-sensitive.
    #      * @param startIndexInclusive The inclusive start index for the search.
    #      * @param endIndexExclusive   The exclusive end index for the search.
    #      *
    #      * @return The offset of the first occurrence of the pattern within the specified bounds, or -1 if no match is found.
    #      
    @abstractmethod
    def indexOf_3(self, searchTerm, caseSensitive, startIndexInclusive, endIndexExclusive):
        """ generated source for method indexOf_3 """

    # 
    #      * Searches the data in the ByteArray for the first occurrence of a specified term.
    #      * It works on byte-based data in a way that is similar to the way the native Java method {@link String#indexOf(String)} works on String-based data.
    #      *
    #      * @param searchTerm          The value to be searched for.
    #      * @param caseSensitive       Flags whether the search is case-sensitive.
    #      * @param startIndexInclusive The inclusive start index for the search.
    #      * @param endIndexExclusive   The exclusive end index for the search.
    #      *
    #      * @return The offset of the first occurrence of the pattern within the specified bounds, or -1 if no match is found.
    #      
    @abstractmethod
    def indexOf_4(self, searchTerm, caseSensitive, startIndexInclusive, endIndexExclusive):
        """ generated source for method indexOf_4 """

    # 
    #      * Searches the data in the ByteArray and counts all matches for a specified term.
    #      *
    #      * @param searchTerm The value to be searched for.
    #      *
    #      * @return The count of all matches of the pattern
    #      
    @abstractmethod
    def countMatches(self, searchTerm):
        """ generated source for method countMatches """

    # 
    #      * Searches the data in the ByteArray and counts all matches for a specified term.
    #      *
    #      * @param searchTerm The value to be searched for.
    #      *
    #      * @return The count of all matches of the pattern
    #      
    @abstractmethod
    def countMatches_0(self, searchTerm):
        """ generated source for method countMatches_0 """

    # 
    #      * Searches the data in the ByteArray and counts all matches for a specified term.
    #      *
    #      * @param searchTerm    The value to be searched for.
    #      * @param caseSensitive Flags whether the search is case-sensitive.
    #      *
    #      * @return The count of all matches of the pattern
    #      
    @abstractmethod
    def countMatches_1(self, searchTerm, caseSensitive):
        """ generated source for method countMatches_1 """

    # 
    #      * Searches the data in the ByteArray and counts all matches for a specified term.
    #      *
    #      * @param searchTerm    The value to be searched for.
    #      * @param caseSensitive Flags whether the search is case-sensitive.
    #      *
    #      * @return The count of all matches of the pattern
    #      
    @abstractmethod
    def countMatches_2(self, searchTerm, caseSensitive):
        """ generated source for method countMatches_2 """

    # 
    #      * Searches the data in the ByteArray and counts all matches for a specified term.
    #      *
    #      * @param searchTerm          The value to be searched for.
    #      * @param caseSensitive       Flags whether the search is case-sensitive.
    #      * @param startIndexInclusive The inclusive start index for the search.
    #      * @param endIndexExclusive   The exclusive end index for the search.
    #      *
    #      * @return The count of all matches of the pattern within the specified bounds
    #      
    @abstractmethod
    def countMatches_3(self, searchTerm, caseSensitive, startIndexInclusive, endIndexExclusive):
        """ generated source for method countMatches_3 """

    # 
    #      * Searches the data in the ByteArray and counts all matches for a specified term.
    #      *
    #      * @param searchTerm          The value to be searched for.
    #      * @param caseSensitive       Flags whether the search is case-sensitive.
    #      * @param startIndexInclusive The inclusive start index for the search.
    #      * @param endIndexExclusive   The exclusive end index for the search.
    #      *
    #      * @return The count of all matches of the pattern within the specified bounds
    #      
    @abstractmethod
    def countMatches_4(self, searchTerm, caseSensitive, startIndexInclusive, endIndexExclusive):
        """ generated source for method countMatches_4 """

    # 
    #      * Convert the bytes of the ByteArray into String form using the encoding specified by Burp Suite.
    #      *
    #      * @return The converted data in String form.
    #      
    @abstractmethod
    def __str__(self):
        """ generated source for method toString """

    # 
    #      * Create a copy of the {@code ByteArray} appended with the provided bytes.
    #      *
    #      * @param data The byte[] or sequence of bytes to append.
    #      
    @abstractmethod
    def withAppended(self, *data):
        """ generated source for method withAppended """

    # 
    #      * Create a copy of the {@code ByteArray} appended with the provided integers after narrowing primitive conversion to bytes.
    #      *
    #      * @param data The int[] or sequence of integers to append after narrowing primitive conversion to bytes.
    #      

    #      
    @abstractmethod
    def byteArrayOfLength(cls, length):
        """ generated source for method byteArrayOfLength """

    # 
    #      * Create a new {@code ByteArray} with the provided byte data.<br>
    #      *
    #      * @param data byte[] to wrap, or sequence of bytes to wrap.
    #      *
    #      * @return New {@code ByteArray} wrapping the provided byte array.
    #      
    @abstractmethod
    def byteArray(data: bytes) -> 'ByteArray':
        """ generated source for method byteArray """

    # 
    #      * Create a new {@code ByteArray} with the provided integers after a narrowing primitive conversion to bytes.<br>
    #      *
    #      * @param data bytes.
    #      *
    #      * @return New {@code ByteArray} wrapping the provided data after a narrowing primitive conversion to bytes.
    #      


# 
#   * Provides access to the functionality related to logging and events.
#   
class Logging(Protocol):
    """ generated source for interface Logging """
    # 
    #       * Obtain the current extension's standard output
    #       * stream. Extensions should write all output to this stream, allowing the
    #       * Burp user to configure how that output is handled from within the UI.
    #       *
    #       * @return The extension's standard output stream.
    #       
    @abstractmethod
    def output(self):
        """ generated source for method output """

    # 
    #       * Obtain the current extension's standard error
    #       * stream. Extensions should write all error messages to this stream,
    #       * allowing the Burp user to configure how that output is handled from
    #       * within the UI.
    #       *
    #       * @return The extension's standard error stream.
    #       
    @abstractmethod
    def error(self):
        """ generated source for method error """

    # 
    #       * This method prints a line of output to the current extension's standard
    #       * output stream.
    #       *
    #       * @param message The message to print.
    #       
    @abstractmethod
    def logToOutput(self, message):
        """ generated source for method logToOutput """

    # 
    #       * This method prints a line of output to the current extension's standard
    #       * error stream.
    #       *
    #       * @param message The message to print.
    #       
    @abstractmethod
    def logToError(self, message):
        """ generated source for method logToError """

    # 
    #       * This method can be used to display a debug event in the Burp Suite
    #       * event log.
    #       *
    #       * @param message The debug message to display.
    #       
    @abstractmethod
    def raiseDebugEvent(self, message):
        """ generated source for method raiseDebugEvent """

    # 
    #       * This method can be used to display an informational event in the Burp
    #       * Suite event log.
    #       *
    #       * @param message The informational message to display.
    #       
    @abstractmethod
    def raiseInfoEvent(self, message):
        """ generated source for method raiseInfoEvent """

    # 
    #       * This method can be used to display an error event in the Burp Suite
    #       * event log.
    #       *
    #       * @param message The error message to display.
    #       
    @abstractmethod
    def raiseErrorEvent(self, message):
        """ generated source for method raiseErrorEvent """

    # 
    #       * This method can be used to display a critical event in the Burp Suite
    #       * event log.
    #       *
    #       * @param message The critical message to display.
    #       
    @abstractmethod
    def raiseCriticalEvent(self, message):
        """ generated source for method raiseCriticalEvent """

class HttpRequest(Protocol):
    @abstractmethod
    def withUpdatedHeader(self, key: str, value: str) -> 'HttpRequest':
        raise NotImplementedError

    @abstractmethod
    def withAddedHeader(self, key: str, value: str) -> 'HttpRequest':
        raise NotImplementedError

    @abstractmethod
    def toByteArray() -> ByteArray:
        raise NotImplementedError
    
class HttpResponse(Protocol):
    @abstractmethod
    def withUpdatedHeader(self, key: str, value: str) -> 'HttpResponse':
        raise NotImplementedError

    @abstractmethod
    def withAddedHeader(self, key: str, value: str) -> 'HttpResponse':
        raise NotImplementedError

    @abstractmethod
    def toByteArray() -> ByteArray:
        raise NotImplementedError