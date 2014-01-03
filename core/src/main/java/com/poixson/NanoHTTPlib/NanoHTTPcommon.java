package com.poixson.NanoHTTPlib;

import java.io.Closeable;
import java.net.InetSocketAddress;


public abstract class NanoHTTPcommon implements Closeable {

	public static final String version = "1.0.0";

	// defaults
	public static final httpMime DEFAULT_MIME = httpMime.PLAINTEXT;
	public static final String DEFAULT_HOST = null;
	public static final int DEFAULT_PORT = 80;
	public static final int MAX_PORT_NUMBER = 65534;

	/**
	 * Maximum time to wait on Socket.getInputStream().read() (in milliseconds)
	 * This is required as the Keep-Alive HTTP connections would otherwise
	 * block the socket reading thread forever (or as long the browser is open).
	 */
	public static final int SOCKET_TIMEOUT = 5000;
	public static final int SERVER_BACKLOG_CONNECTIONS = 5;

	public static final String UTF8 = "UTF-8";

	// ------------------------------------------------------------------------------- //
	// super object


	protected volatile String host;
	protected volatile int port;
	protected volatile InetSocketAddress inet = null;


	/**
	 * Constructs an HTTP client/server on given port and binds to given host.
	 */
	public NanoHTTPcommon(String host, int port) {
		if(host == null || host.isEmpty()) host = null;
		this.host = host;
		this.port = port;
		validateHostPort();
	}
	@Override
	public Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}


	/**
	 * Validates host and port, and creates a new InetSocketAddress object.
	 */
	protected void validateHostPort() {
		if(host == null || host.isEmpty()) host = null;
		if(port == -1) port = DEFAULT_PORT;
		if(port < 1 || port > MAX_PORT_NUMBER)
			throw new IllegalArgumentException("Port number out of range");
		if(host == null)
			inet = new InetSocketAddress(port);
		else
			inet = new InetSocketAddress(host, port);
	}


	// ------------------------------------------------------------------------------- //


	/**
	 * HTTP response status codes.
	 */
	public enum httpStatus {
		CONTINUE                  (100, "Continue"),
		OK                        (200, "OK"),
		CREATED                   (201, "Created"),
		ACCEPTED                  (202, "Accepted"),
		NO_CONTENT                (204, "No Content"),
		PARTIAL_CONTENT           (206, "Partial Content"),
		REDIRECT                  (301, "Moved Permanently"),
		FOUND                     (302, "Found"),
		NOT_MODIFIED              (304, "Not Modified"),
		TEMP_REDIRECT             (307, "Temporary Redirect"),
		BAD_REQUEST               (400, "Bad Request"),
		UNAUTHORIZED              (401, "Unauthorized"),
		FORBIDDEN                 (403, "Forbidden"),
		NOT_FOUND                 (404, "Not Found"),
		METHOD_NOT_ALLOWED        (405, "Method Not Allowed"),
		REQUEST_TIMEOUT           (408, "Request Timeout"),
		CONFLICT                  (409, "Conflict"),
		GONE                      (410, "Gone"),
		LENGTH_REQUIRED           (411, "Length Required"),
		REQUEST_ENTITY_TOO_LARGE  (413, "Request Entity Too Large"),
		REQUEST_URI_TOO_LONG      (414, "Request-URI Too Long"),
		UNSUPPORTED_MEDIA_TYPE    (415, "Unsupported Media Type"),
		RANGE_NOT_SATISFIABLE     (416, "Requested Range Not Satisfiable"),
		LOCKED                    (423, "Locked"),
		FAILED_DEPENDENCY         (424, "Failed Dependency"),
		UPGRADE_REQUIRED          (426, "Upgrade Required"),
		INTERNAL_ERROR            (500, "Internal Server Error"),
		NOT_IMPLEMENTED           (501, "Not Implemented"),
		BAD_GATEWAY               (502, "Bad Gateway"),
		SERVICE_UNAVAILABLE       (503, "Service Unavailable"),
		GATEWAY_TIMEOUT           (504, "Gateway Timeout"),
		HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported");
		private final int    value;
		private final String desc;
		httpStatus(int value, String desc) {
			this.value = value;
			this.desc  = desc;
		}
		public int getValue() {
			return value;
		}
		public String getDesc() {
			return desc;
		}
		@Override
		public String toString() {
			return Integer.toString(getValue())+" "+getDesc();
		}
	}


	/**
	 *
	 */
	public enum httpVersion {
		HTTP_1_0("1.0"), HTTP_1_1("1.1");
		private final String versionStr;
		httpVersion(String versionStr) {
			this.versionStr = versionStr;
		}
		public static httpVersion lookup(String str) {
			if(str == null || str.isEmpty())
				return null;
			for(httpVersion v : httpVersion.values()) {
				if(str.equals(v.versionStr))
					return v;
			}
			return null;
		}
	}


	/**
	 * HTTP Request methods.
	 */
	public enum httpMethod {
		GET, PUT, POST, DELETE, HEAD, OPTIONS;
		public static httpMethod lookup(String str) {
			if(str == null || str.isEmpty())
				return null;
			for(httpMethod m : httpMethod.values()) {
				if(m.toString().equalsIgnoreCase(str))
					return m;
			}
			return null;
		}
	}


	/**
	 * Common mime types for dynamic content.
	 */
	public enum httpMime {
		// text
		PLAINTEXT("text/plain",               "txt"),
		HTML     ("text/html",                "htm", "html"),
		XML      ("text/xml",                 "xml"),
		JSON     ("application/json",         "json"),
		JAVASCRIPT("text/javascript",         "js"),
		CSS      ("text/css",                 "css"),
		CSV      ("text/csv",                 "csv"),
		// binary
		BINARY   ("application/octet-stream", "exe", "bin", "class"),
		ZIP      ("application/zip",          "zip"),
		GZ       ("application/gzip",         "gz"),
		PDF      ("application/pdf",          "pdf"),
		DOC      ("application/msword",       "doc"),
		// image
		PNG      ("image/png",                "png"),
		JPG      ("image/jpeg",               "jpg", "jpeg"),
		GIF      ("image/gif",                "gif"),
		ICO      ("image/x-icon",             "ico"),
		SVG      ("image/svg+xml",            "svg"),
		// audio
		MP3      ("audio/mpeg",               "mp3"),
		OGG      ("audio/ogg"),
		// playlists
		M3U      ("audio/mpeg-url",           "m3u"),
		PLS      ("audio/x-scpls",            "pls"),
		// video
		MPEG     ("video/mpeg",               "mpg", "mpeg"),
		MP4      ("video/mp4",                "mp4", "m4v"),
		OGV      ("video/ogg",                "ogg", "ogv"),
		FLASH    ("video/x-flv",              "flv"),
		WEBM     ("video/webm",               "webm");
		private final String msg;
		private final String[] ext;
		httpMime(String msg, String...ext) {
			this.msg = msg;
			this.ext = ext;
		}
		@Override
		public String toString() {
			return msg;
		}
		public String[] getExtensions() {
			return java.util.Arrays.copyOf(ext, ext.length);
		}
		public static httpMime lookup(String str) {
			if(str == null || str.isEmpty())
				return null;
			if(str.contains("."))
				str = str.substring(str.lastIndexOf("."));
			str = str.toLowerCase();
System.out.println(str);
			for(httpMime mime : httpMime.values())
				if(mime.hasExtension(str))
					return mime;
			return null;
		}
		/**
		 * @param String, file extension to look for.
		 * @return boolean, true if httpMime contains the file extension in question.
		 */
		public boolean hasExtension(String str) {
			if(str == null || str.isEmpty())
				return false;
			for(String ex : this.ext)
				if(str.equals(ex))
					return true;
			return false;
		}
	}


}
