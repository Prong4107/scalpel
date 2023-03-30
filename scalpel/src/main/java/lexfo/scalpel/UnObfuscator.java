package lexfo.scalpel;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.burpsuite.BurpSuite;
import burp.api.montoya.burpsuite.ShutdownOptions;
import burp.api.montoya.collaborator.Collaborator;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.CollaboratorServer;
import burp.api.montoya.collaborator.DnsDetails;
import burp.api.montoya.collaborator.DnsQueryType;
import burp.api.montoya.collaborator.HttpDetails;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.collaborator.InteractionFilter;
import burp.api.montoya.collaborator.InteractionId;
import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.collaborator.PayloadOption;
import burp.api.montoya.collaborator.SecretKey;
import burp.api.montoya.collaborator.SmtpDetails;
import burp.api.montoya.collaborator.SmtpProtocol;
import burp.api.montoya.comparer.Comparer;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.core.BurpSuiteEdition;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.HighlightColor;
import burp.api.montoya.core.Marker;
import burp.api.montoya.core.Range;
import burp.api.montoya.core.Registration;
import burp.api.montoya.core.Task;
import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.core.Version;
import burp.api.montoya.decoder.Decoder;
import burp.api.montoya.extension.Extension;
import burp.api.montoya.extension.ExtensionUnloadingHandler;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.HttpProtocol;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestAction;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.ContentType;
import burp.api.montoya.http.message.Cookie;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpMessage;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.MimeType;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.requests.HttpTransformation;
import burp.api.montoya.http.message.requests.MalformedRequestException;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.http.message.responses.analysis.Attribute;
import burp.api.montoya.http.message.responses.analysis.AttributeType;
import burp.api.montoya.http.message.responses.analysis.KeywordCount;
import burp.api.montoya.http.message.responses.analysis.ResponseKeywordsAnalyzer;
import burp.api.montoya.http.message.responses.analysis.ResponseVariationsAnalyzer;
import burp.api.montoya.http.sessions.ActionResult;
import burp.api.montoya.http.sessions.CookieJar;
import burp.api.montoya.http.sessions.SessionHandlingAction;
import burp.api.montoya.http.sessions.SessionHandlingActionData;
import burp.api.montoya.internal.MontoyaObjectFactory;
import burp.api.montoya.internal.ObjectFactoryLocator;
import burp.api.montoya.intruder.AttackConfiguration;
import burp.api.montoya.intruder.GeneratedPayload;
import burp.api.montoya.intruder.HttpRequestTemplate;
import burp.api.montoya.intruder.Intruder;
import burp.api.montoya.intruder.IntruderInsertionPoint;
import burp.api.montoya.intruder.PayloadData;
import burp.api.montoya.intruder.PayloadGenerator;
import burp.api.montoya.intruder.PayloadGeneratorProvider;
import burp.api.montoya.intruder.PayloadProcessingAction;
import burp.api.montoya.intruder.PayloadProcessingResult;
import burp.api.montoya.intruder.PayloadProcessor;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.persistence.PersistedList;
import burp.api.montoya.persistence.PersistedObject;
import burp.api.montoya.persistence.Persistence;
import burp.api.montoya.persistence.Preferences;
import burp.api.montoya.proxy.MessageReceivedAction;
import burp.api.montoya.proxy.MessageToBeSentAction;
import burp.api.montoya.proxy.Proxy;
import burp.api.montoya.proxy.ProxyHistoryFilter;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import burp.api.montoya.proxy.ProxyWebSocketHistoryFilter;
import burp.api.montoya.proxy.ProxyWebSocketMessage;
import burp.api.montoya.proxy.http.InterceptedHttpMessage;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.InterceptedResponse;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;
import burp.api.montoya.proxy.http.ProxyResponseHandler;
import burp.api.montoya.proxy.http.ProxyResponseReceivedAction;
import burp.api.montoya.proxy.http.ProxyResponseToBeSentAction;
import burp.api.montoya.proxy.websocket.BinaryMessageReceivedAction;
import burp.api.montoya.proxy.websocket.BinaryMessageToBeSentAction;
import burp.api.montoya.proxy.websocket.InterceptedBinaryMessage;
import burp.api.montoya.proxy.websocket.InterceptedTextMessage;
import burp.api.montoya.proxy.websocket.ProxyMessageHandler;
import burp.api.montoya.proxy.websocket.ProxyWebSocket;
import burp.api.montoya.proxy.websocket.ProxyWebSocketCreation;
import burp.api.montoya.proxy.websocket.ProxyWebSocketCreationHandler;
import burp.api.montoya.proxy.websocket.TextMessageReceivedAction;
import burp.api.montoya.proxy.websocket.TextMessageToBeSentAction;
import burp.api.montoya.repeater.Repeater;
import burp.api.montoya.scanner.AuditConfiguration;
import burp.api.montoya.scanner.AuditResult;
import burp.api.montoya.scanner.BuiltInAuditConfiguration;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.Crawl;
import burp.api.montoya.scanner.CrawlAndAudit;
import burp.api.montoya.scanner.CrawlConfiguration;
import burp.api.montoya.scanner.ReportFormat;
import burp.api.montoya.scanner.ScanCheck;
import burp.api.montoya.scanner.ScanConfiguration;
import burp.api.montoya.scanner.ScanTask;
import burp.api.montoya.scanner.Scanner;
import burp.api.montoya.scanner.audit.Audit;
import burp.api.montoya.scanner.audit.AuditIssueHandler;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointProvider;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPointType;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueDefinition;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import burp.api.montoya.scope.Scope;
import burp.api.montoya.scope.ScopeChange;
import burp.api.montoya.scope.ScopeChangeHandler;
import burp.api.montoya.sitemap.SiteMap;
import burp.api.montoya.sitemap.SiteMapFilter;
import burp.api.montoya.sitemap.SiteMapNode;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.Theme;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.contextmenu.ComponentEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.InvocationSource;
import burp.api.montoya.ui.contextmenu.InvocationType;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.editor.Editor;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import burp.api.montoya.ui.editor.RawEditor;
import burp.api.montoya.ui.editor.WebSocketMessageEditor;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.EditorMode;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;
import burp.api.montoya.ui.menu.Menu;
import burp.api.montoya.ui.menu.MenuBar;
import burp.api.montoya.ui.menu.MenuItem;
import burp.api.montoya.ui.swing.SwingUtils;
import burp.api.montoya.utilities.Base64DecodingOptions;
import burp.api.montoya.utilities.Base64EncodingOptions;
import burp.api.montoya.utilities.Base64Utils;
import burp.api.montoya.utilities.ByteUtils;
import burp.api.montoya.utilities.CompressionType;
import burp.api.montoya.utilities.CompressionUtils;
import burp.api.montoya.utilities.CryptoUtils;
import burp.api.montoya.utilities.DigestAlgorithm;
import burp.api.montoya.utilities.HtmlEncoding;
import burp.api.montoya.utilities.HtmlUtils;
import burp.api.montoya.utilities.NumberUtils;
import burp.api.montoya.utilities.RandomUtils;
import burp.api.montoya.utilities.StringUtils;
import burp.api.montoya.utilities.URLUtils;
import burp.api.montoya.utilities.Utilities;
import burp.api.montoya.websocket.BinaryMessage;
import burp.api.montoya.websocket.BinaryMessageAction;
import burp.api.montoya.websocket.Direction;
import burp.api.montoya.websocket.MessageAction;
import burp.api.montoya.websocket.MessageHandler;
import burp.api.montoya.websocket.TextMessage;
import burp.api.montoya.websocket.TextMessageAction;
import burp.api.montoya.websocket.WebSocket;
import burp.api.montoya.websocket.WebSocketCreated;
import burp.api.montoya.websocket.WebSocketCreatedHandler;
import burp.api.montoya.websocket.WebSockets;
import burp.api.montoya.websocket.extension.ExtensionWebSocket;
import burp.api.montoya.websocket.extension.ExtensionWebSocketCreation;
import burp.api.montoya.websocket.extension.ExtensionWebSocketCreationStatus;
import burp.api.montoya.websocket.extension.ExtensionWebSocketMessageHandler;

// import burp.api.montoya.ui.contextmenu.AuditIssueContextMenuEvent;
// import burp.api.montoya.ui.contextmenu.WebSocketMessage;
// import burp.api.montoya.ui.contextmenu.WebSocketContextMenuEvent;
// import burp.api.montoya.ui.contextmenu.WebSocketEditorEvent;
// import burp.api.montoya.ui.menu.BasicMenuItem;

public class UnObfuscator {

	/**
		Returns the real class name of the specified object.
		(Awful and used for debug purposes)

		@param obj The object to get the class name of.
		@return The class name of the specified object.
  	*/
	public static String getClassName(Object obj) {
		// Retrieve real type.
		// Burp uses proxy and symbols obfuscation
		// So we cannot naively use obj.getClass(), we seem to have no choice but to use an instanceof if forest.

		// Handle null object
		if (obj == null) return "null";

		if (
			obj instanceof RequestToBeSentAction
		) return "RequestToBeSentAction";
		if (obj instanceof HttpRequestToBeSent) return "HttpRequestToBeSent";
		if (obj instanceof HttpResponseReceived) return "HttpResponseReceived";
		if (obj instanceof HttpRequest) return "HttpRequest";
		if (obj instanceof HttpRequestResponse) return "HttpRequestResponse";

		if (obj instanceof ContentType) return "ContentType";
		if (obj instanceof HttpParameterType) return "HttpParameterType";
		if (obj instanceof HttpParameter) return "HttpParameter";
		if (obj instanceof ParsedHttpParameter) return "ParsedHttpParameter";
		if (obj instanceof Cookie) return "Cookie";
		if (
			obj instanceof MalformedRequestException
		) return "MalformedRequestException";
		if (obj instanceof HttpTransformation) return "HttpTransformation";
		if (obj instanceof Attribute) return "Attribute";
		if (obj instanceof AttributeType) return "AttributeType";
		if (obj instanceof HttpResponse) return "HttpResponse";
		if (obj instanceof HttpMessage) return "HttpMessage";
		if (obj instanceof HttpHeader) return "HttpHeader";
		if (obj instanceof MimeType) return "MimeType";
		if (obj instanceof HttpMode) return "HttpMode";
		if (obj instanceof Http) return "Http";
		if (obj instanceof HttpProtocol) return "HttpProtocol";
		if (obj instanceof HttpService) return "HttpService";
		if (obj instanceof KeywordCount) return "KeywordCount";
		if (obj instanceof Decoder) return "Decoder";
		if (obj instanceof MontoyaApi) return "MontoyaApi";
		if (obj instanceof Utilities) return "Utilities";
		if (obj instanceof CryptoUtils) return "CryptoUtils";
		if (obj instanceof ByteUtils) return "ByteUtils";
		if (obj instanceof URLUtils) return "URLUtils";
		if (obj instanceof DigestAlgorithm) return "DigestAlgorithm";
		if (obj instanceof CompressionType) return "CompressionType";
		if (obj instanceof NumberUtils) return "NumberUtils";
		if (obj instanceof Base64Utils) return "Base64Utils";
		if (
			obj instanceof Base64EncodingOptions
		) return "Base64EncodingOptions";
		if (obj instanceof StringUtils) return "StringUtils";
		if (obj instanceof HtmlUtils) return "HtmlUtils";
		if (obj instanceof HtmlEncoding) return "HtmlEncoding";
		if (obj instanceof RandomUtils) return "RandomUtils";
		if (
			obj instanceof Base64DecodingOptions
		) return "Base64DecodingOptions";
		if (obj instanceof CompressionUtils) return "CompressionUtils";
		if (obj instanceof InteractionType) return "InteractionType";
		if (obj instanceof HttpDetails) return "HttpDetails";
		if (obj instanceof InteractionFilter) return "InteractionFilter";
		if (obj instanceof CollaboratorPayload) return "CollaboratorPayload";
		if (obj instanceof PayloadOption) return "PayloadOption";
		if (obj instanceof CollaboratorClient) return "CollaboratorClient";
		if (obj instanceof Collaborator) return "Collaborator";
		if (obj instanceof SecretKey) return "SecretKey";
		if (obj instanceof DnsQueryType) return "DnsQueryType";
		if (obj instanceof SmtpProtocol) return "SmtpProtocol";
		if (obj instanceof CollaboratorServer) return "CollaboratorServer";
		if (obj instanceof Interaction) return "Interaction";
		if (obj instanceof InteractionId) return "InteractionId";
		if (obj instanceof DnsDetails) return "DnsDetails";
		if (obj instanceof SmtpDetails) return "SmtpDetails";
		if (
			obj instanceof SessionHandlingAction
		) return "SessionHandlingAction";
		if (
			obj instanceof SessionHandlingActionData
		) return "SessionHandlingActionData";
		if (obj instanceof CookieJar) return "CookieJar";
		if (obj instanceof ActionResult) return "ActionResult";
		if (obj instanceof RequestAction) return "RequestAction";
		if (obj instanceof ResponseAction) return "ResponseAction";
		if (obj instanceof HttpHandler) return "HttpHandler";
		if (
			obj instanceof ResponseReceivedAction
		) return "ResponseReceivedAction";
		if (
			obj instanceof ResponseKeywordsAnalyzer
		) return "ResponseKeywordsAnalyzer";
		if (
			obj instanceof ResponseVariationsAnalyzer
		) return "ResponseVariationsAnalyzer";
		if (obj instanceof PersistedObject) return "PersistedObject";
		if (obj instanceof Persistence) return "Persistence";
		if (obj instanceof PersistedList) return "PersistedList";
		if (obj instanceof Preferences) return "Preferences";
		if (obj instanceof ObjectFactoryLocator) return "ObjectFactoryLocator";
		if (obj instanceof MontoyaObjectFactory) return "MontoyaObjectFactory";
		if (obj instanceof Comparer) return "Comparer";
		if (obj instanceof BurpExtension) return "BurpExtension";
		if (obj instanceof Marker) return "Marker";
		if (obj instanceof HighlightColor) return "HighlightColor";
		if (obj instanceof Version) return "Version";
		if (obj instanceof ToolType) return "ToolType";
		if (obj instanceof Range) return "Range";
		if (obj instanceof BurpSuiteEdition) return "BurpSuiteEdition";
		if (obj instanceof Annotations) return "Annotations";
		if (obj instanceof ByteArray) return "ByteArray";
		if (obj instanceof ToolSource) return "ToolSource";
		if (obj instanceof Registration) return "Registration";
		if (obj instanceof Task) return "Task";
		if (obj instanceof UserInterface) return "UserInterface";
		if (obj instanceof InvocationSource) return "InvocationSource";
		if (obj instanceof ComponentEvent) return "ComponentEvent";
		if (
			obj instanceof ContextMenuItemsProvider
		) return "ContextMenuItemsProvider";
		if (obj instanceof ContextMenuEvent) return "ContextMenuEvent";
		// if (
		// 	obj instanceof WebSocketContextMenuEvent
		// ) return "WebSocketContextMenuEvent";
		if (obj instanceof InvocationType) return "InvocationType";
		// if (obj instanceof WebSocketEditorEvent) return "WebSocketEditorEvent";
		if (
			obj instanceof MessageEditorHttpRequestResponse
		) return "MessageEditorHttpRequestResponse";
		if (obj instanceof MenuBar) return "MenuBar";
		// if (obj instanceof BasicMenuItem) return "BasicMenuItem";
		if (obj instanceof MenuItem) return "MenuItem";
		if (obj instanceof Menu) return "Menu";
		if (obj instanceof Theme) return "Theme";
		if (obj instanceof Editor) return "Editor";
		if (obj instanceof RawEditor) return "RawEditor";
		if (obj instanceof HttpResponseEditor) return "HttpResponseEditor";
		if (obj instanceof EditorOptions) return "EditorOptions";
		if (obj instanceof HttpRequestEditor) return "HttpRequestEditor";
		if (obj instanceof EditorMode) return "EditorMode";
		if (
			obj instanceof HttpRequestEditorProvider
		) return "HttpRequestEditorProvider";
		if (
			obj instanceof ExtensionProvidedHttpRequestEditor
		) return "ExtensionProvidedHttpRequestEditor";
		if (
			obj instanceof HttpResponseEditorProvider
		) return "HttpResponseEditorProvider";
		if (
			obj instanceof EditorCreationContext
		) return "EditorCreationContext";
		if (
			obj instanceof ExtensionProvidedHttpResponseEditor
		) return "ExtensionProvidedHttpResponseEditor";
		if (
			obj instanceof ExtensionProvidedEditor
		) return "ExtensionProvidedEditor";
		if (
			obj instanceof WebSocketMessageEditor
		) return "WebSocketMessageEditor";
		if (obj instanceof Selection) return "Selection";
		if (obj instanceof SwingUtils) return "SwingUtils";
		if (obj instanceof Repeater) return "Repeater";
		if (obj instanceof SiteMapNode) return "SiteMapNode";
		if (obj instanceof SiteMap) return "SiteMap";
		if (obj instanceof SiteMapFilter) return "SiteMapFilter";
		if (obj instanceof ReportFormat) return "ReportFormat";
		if (obj instanceof ScanConfiguration) return "ScanConfiguration";
		if (obj instanceof AuditResult) return "AuditResult";
		if (obj instanceof ScanTask) return "ScanTask";
		if (obj instanceof CrawlAndAudit) return "CrawlAndAudit";
		if (obj instanceof ConsolidationAction) return "ConsolidationAction";
		if (
			obj instanceof AuditInsertionPointType
		) return "AuditInsertionPointType";
		if (obj instanceof AuditInsertionPoint) return "AuditInsertionPoint";
		if (
			obj instanceof AuditInsertionPointProvider
		) return "AuditInsertionPointProvider";
		if (obj instanceof Audit) return "Audit";
		if (obj instanceof AuditIssue) return "AuditIssue";
		if (obj instanceof AuditIssueSeverity) return "AuditIssueSeverity";
		if (obj instanceof AuditIssueDefinition) return "AuditIssueDefinition";
		if (obj instanceof AuditIssueConfidence) return "AuditIssueConfidence";
		if (obj instanceof AuditIssueHandler) return "AuditIssueHandler";
		if (obj instanceof Scanner) return "Scanner";
		if (obj instanceof ScanCheck) return "ScanCheck";
		if (obj instanceof AuditConfiguration) return "AuditConfiguration";
		if (obj instanceof CrawlConfiguration) return "CrawlConfiguration";
		if (
			obj instanceof BuiltInAuditConfiguration
		) return "BuiltInAuditConfiguration";
		if (obj instanceof Crawl) return "Crawl";
		if (obj instanceof ShutdownOptions) return "ShutdownOptions";
		if (obj instanceof BurpSuite) return "BurpSuite";
		if (
			obj instanceof ProxyRequestReceivedAction
		) return "ProxyRequestReceivedAction";
		if (
			obj instanceof ProxyResponseReceivedAction
		) return "ProxyResponseReceivedAction";
		if (obj instanceof InterceptedRequest) return "InterceptedRequest";
		if (obj instanceof InterceptedResponse) return "InterceptedResponse";
		if (
			obj instanceof ProxyRequestToBeSentAction
		) return "ProxyRequestToBeSentAction";
		if (obj instanceof ProxyResponseHandler) return "ProxyResponseHandler";
		if (obj instanceof ProxyRequestHandler) return "ProxyRequestHandler";
		if (
			obj instanceof InterceptedHttpMessage
		) return "InterceptedHttpMessage";
		if (
			obj instanceof ProxyResponseToBeSentAction
		) return "ProxyResponseToBeSentAction";
		if (obj instanceof Proxy) return "Proxy";
		if (obj instanceof ProxyHistoryFilter) return "ProxyHistoryFilter";
		if (
			obj instanceof ProxyWebSocketHistoryFilter
		) return "ProxyWebSocketHistoryFilter";
		if (
			obj instanceof MessageReceivedAction
		) return "MessageReceivedAction";
		if (
			obj instanceof ProxyHttpRequestResponse
		) return "ProxyHttpRequestResponse";
		if (
			obj instanceof MessageToBeSentAction
		) return "MessageToBeSentAction";
		if (
			obj instanceof BinaryMessageReceivedAction
		) return "BinaryMessageReceivedAction";
		if (
			obj instanceof InterceptedBinaryMessage
		) return "InterceptedBinaryMessage";
		if (obj instanceof ProxyMessageHandler) return "ProxyMessageHandler";
		if (
			obj instanceof BinaryMessageToBeSentAction
		) return "BinaryMessageToBeSentAction";
		if (
			obj instanceof ProxyWebSocketCreationHandler
		) return "ProxyWebSocketCreationHandler";
		if (obj instanceof ProxyWebSocket) return "ProxyWebSocket";
		if (
			obj instanceof TextMessageToBeSentAction
		) return "TextMessageToBeSentAction";
		if (
			obj instanceof InterceptedTextMessage
		) return "InterceptedTextMessage";
		if (
			obj instanceof ProxyWebSocketCreation
		) return "ProxyWebSocketCreation";
		if (
			obj instanceof TextMessageReceivedAction
		) return "TextMessageReceivedAction";
		if (
			obj instanceof ProxyWebSocketMessage
		) return "ProxyWebSocketMessage";
		if (obj instanceof AttackConfiguration) return "AttackConfiguration";
		if (obj instanceof Intruder) return "Intruder";
		if (
			obj instanceof IntruderInsertionPoint
		) return "IntruderInsertionPoint";
		if (obj instanceof PayloadGenerator) return "PayloadGenerator";
		if (
			obj instanceof PayloadGeneratorProvider
		) return "PayloadGeneratorProvider";
		if (obj instanceof PayloadProcessor) return "PayloadProcessor";
		if (obj instanceof PayloadData) return "PayloadData";
		if (obj instanceof GeneratedPayload) return "GeneratedPayload";
		if (
			obj instanceof PayloadProcessingResult
		) return "PayloadProcessingResult";
		if (
			obj instanceof PayloadProcessingAction
		) return "PayloadProcessingAction";
		if (obj instanceof HttpRequestTemplate) return "HttpRequestTemplate";
		if (obj instanceof Scope) return "Scope";
		if (obj instanceof ScopeChangeHandler) return "ScopeChangeHandler";
		if (obj instanceof ScopeChange) return "ScopeChange";
		if (obj instanceof Extension) return "Extension";
		if (
			obj instanceof ExtensionUnloadingHandler
		) return "ExtensionUnloadingHandler";
		if (obj instanceof Logging) return "Logging";
		if (obj instanceof BinaryMessageAction) return "BinaryMessageAction";
		if (obj instanceof MessageAction) return "MessageAction";
		if (obj instanceof BinaryMessage) return "BinaryMessage";
		if (obj instanceof WebSocketCreated) return "WebSocketCreated";
		if (obj instanceof TextMessageAction) return "TextMessageAction";
		if (obj instanceof TextMessage) return "TextMessage";
		if (obj instanceof Direction) return "Direction";
		if (obj instanceof WebSockets) return "WebSockets";
		if (
			obj instanceof ExtensionWebSocketCreation
		) return "ExtensionWebSocketCreation";
		if (
			obj instanceof ExtensionWebSocketCreationStatus
		) return "ExtensionWebSocketCreationStatus";
		if (obj instanceof ExtensionWebSocket) return "ExtensionWebSocket";
		if (
			obj instanceof ExtensionWebSocketMessageHandler
		) return "ExtensionWebSocketMessageHandler";
		if (
			obj instanceof WebSocketCreatedHandler
		) return "WebSocketCreatedHandler";
		if (obj instanceof MessageHandler) return "MessageHandler";
		if (obj instanceof WebSocket) return "WebSocket";

		return obj.getClass().getName();
	}
}
