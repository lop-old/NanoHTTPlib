package com.poixson.NanoHTTPlib;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A simple, tiny, nicely embeddable HTTP server in Java
 * <p/>
 * <p/>
 * NanoHTTPD
 * <p></p>Copyright (c) 2012-2013 by Paul S. Hawke, 2001,2005-2013 by Jarno Elonen, 2010 by Konstantinos Togias</p>
 * <p/>
 * <p/>
 * <b>Features + limitations: </b>
 * <ul>
 * <p/>
 * <li>Only one Java file</li>
 * <li>Java 5 compatible</li>
 * <li>Released as open source, Modified BSD licence</li>
 * <li>No fixed config files, logging, authorization etc. (Implement yourself if you need them.)</li>
 * <li>Supports parameter parsing of GET and POST methods (+ rudimentary PUT support in 1.25)</li>
 * <li>Supports both dynamic content and file serving</li>
 * <li>Supports file upload (since version 1.2, 2010)</li>
 * <li>Supports partial content (streaming)</li>
 * <li>Supports ETags</li>
 * <li>Never caches anything</li>
 * <li>Doesn't limit bandwidth, request time or simultaneous connections</li>
 * <li>Default code serves files and shows all HTTP parameters and headers</li>
 * <li>File server supports directory listing, index.html and index.htm</li>
 * <li>File server supports partial content (streaming)</li>
 * <li>File server supports ETags</li>
 * <li>File server does the 301 redirection trick for directories without '/'</li>
 * <li>File server supports simple skipping for files (continue download)</li>
 * <li>File server serves also very long files without memory overhead</li>
 * <li>Contains a built-in list of most common mime types</li>
 * <li>All header names are converted lowercase so they don't vary between browsers/clients</li>
 * <p/>
 * </ul>
 * <p/>
 * <p/>
 * <b>How to use: </b>
 * <ul>
 * <p/>
 * <li>Subclass and implement serve() and embed to your own program</li>
 * <p/>
 * </ul>
 * <p/>
 * See the separate "LICENSE.md" file for the distribution license (Modified BSD licence)
 */

public class NanoHTTPserver extends NanoHTTPcommon {

	public static final int DEFAULT_MAX_CONNECTIONS = 10;

	// run state
	private volatile Boolean running = false;
	private volatile boolean stopping = false;

	// listen socket
	private volatile ServerSocket socket = null;
	private final Object serverLock = new Object();

	// connections
	private final Set<httpServerWorker> connections = new HashSet<httpServerWorker>();
	private volatile int countConnections = 0;
	private volatile int countRequests = 0;

	// request handlers
	private final CopyOnWriteArraySet<httpIO> handlers = new CopyOnWriteArraySet<httpIO>();


	// ------------------------------------------------------------------------------- //
	// http server constructors


	/**
	 * Creates an HTTP server on default port 80.
	 */
	public NanoHTTPserver() {
		this(DEFAULT_PORT);
	}
	/**
	 * Creates an HTTP server on the given port.
	 * @param port TCP port to listen on.
	 */
	public NanoHTTPserver(int port) {
		this(DEFAULT_HOST, port);
	}
	/**
	 * Creates an HTTP server on the given host/port.
	 * @param host IP address or hostname to bind to.
	 * @param port TCP port to listen on.
	 */
	public NanoHTTPserver(final String host, final int port) {
		super(host, port);
	}


	// ------------------------------------------------------------------------------- //
	// start/stop socket listener


	public void start() throws IOException {
		// already running
		if(running || socket != null) return;
		synchronized(serverLock) {
			if(running || socket != null) return;
			socket = new ServerSocket();
		}
		// start listener thread
		setThreadName();
		thread.setDaemon(true);
		thread.start();
	}
	public void stop() {
		safeClose(this);
	}


	// ------------------------------------------------------------------------------- //




















	/**
	 * Maximum time to wait on Socket.getInputStream().read() (in milliseconds)
	 * This is required as the Keep-Alive HTTP connections would otherwise
	 * block the socket reading thread forever (or as long the browser is open).
	 */
	public static final int SOCKET_READ_TIMEOUT = 5000;
	/**
	 * Common mime type for dynamic content: plain text
	 */
	public static final String MIME_PLAINTEXT = "text/plain";
	/**
	 * Common mime type for dynamic content: html
	 */
	public static final String MIME_HTML = "text/html";
	/**
	 * Pseudo-Parameter to use to store the actual query string in the parameters map for later re-processing.
	 */
	private static final String QUERY_STRING_PARAMETER = "NanoHttpd.QUERY_STRING";
	private final String hostname;
	private final int myPort;
	private ServerSocket myServerSocket;
	private Set<Socket> openConnections = new HashSet<Socket>();
	private Thread myThread;
	/**
	 * Pluggable strategy for asynchronously executing requests.
	 */
	private AsyncRunner asyncRunner;
	/**
	 * Pluggable strategy for creating and cleaning up temporary files.
	 */
	private TempFileManagerFactory tempFileManagerFactory;

	/**
	 * Constructs an HTTP server on given port.
	 */
	public NanoHTTPD(int port) {
		this(null, port);
	}




	/**
	 * Registers that a new connection has been set up.
	 *
	 * @param socket
	 *			the {@link Socket} for the connection.
	 */
	public synchronized void registerConnection(Socket socket) {
		openConnections.add(socket);
	}

	/**
	 * Registers that a connection has been closed
	 *
	 * @param socket
	 *			the {@link Socket} for the connection.
	 */
	public synchronized void unRegisterConnection(Socket socket) {
		openConnections.remove(socket);
	}

	/**
	 * Forcibly closes all connections that are open.
	 */
	public synchronized void closeAllConnections() {
		for (Socket socket : openConnections) {
			safeClose(socket);
		}
	}

	public final int getListeningPort() {
		return myServerSocket == null ? -1 : myServerSocket.getLocalPort();
	}

	public final boolean wasStarted() {
		return myServerSocket != null && myThread != null;
	}

	public final boolean isAlive() {
		return wasStarted() && !myServerSocket.isClosed() && myThread.isAlive();
	}

	/**
	 * Override this to customize the server.
	 * <p/>
	 * <p/>
	 * (By default, this delegates to serveFile() and allows directory listing.)
	 *
	 * @param uri	 Percent-decoded URI without parameters, for example "/index.cgi"
	 * @param method  "GET", "POST" etc.
	 * @param parms   Parsed, percent decoded parameters from URI and, in case of POST, data.
	 * @param headers Header entries, percent decoded
	 * @return HTTP response, see class Response for details
	 */
	@Deprecated
	public Response serve(String uri, Method method, Map<String, String> headers, Map<String, String> parms,
								   Map<String, String> files) {
		return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
	}

	/**
	 * Override this to customize the server.
	 * <p/>
	 * <p/>
	 * (By default, this delegates to serveFile() and allows directory listing.)
	 *
	 * @param session The HTTP session
	 * @return HTTP response, see class Response for details
	 */
	public Response serve(IHTTPSession session) {
		Map<String, String> files = new HashMap<String, String>();
		Method method = session.getMethod();
		if (Method.PUT.equals(method) || Method.POST.equals(method)) {
			try {
				session.parseBody(files);
			} catch (IOException ioe) {
				return new Response(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
			} catch (ResponseException re) {
				return new Response(re.getStatus(), MIME_PLAINTEXT, re.getMessage());
			}
		}

		Map<String, String> parms = session.getParms();
		parms.put(QUERY_STRING_PARAMETER, session.getQueryParameterString());
		return serve(session.getUri(), method, session.getHeaders(), parms, files);
	}

	/**
	 * Decode percent encoded <code>String</code> values.
	 *
	 * @param str the percent encoded <code>String</code>
	 * @return expanded form of the input, for example "foo%20bar" becomes "foo bar"
	 */
	protected String decodePercent(String str) {
		String decoded = null;
		try {
			decoded = URLDecoder.decode(str, "UTF8");
		} catch (UnsupportedEncodingException ignored) {
		}
		return decoded;
	}

	/**
	 * Decode parameters from a URL, handing the case where a single parameter name might have been
	 * supplied several times, by return lists of values.  In general these lists will contain a single
	 * element.
	 *
	 * @param parms original <b>NanoHttpd</b> parameters values, as passed to the <code>serve()</code> method.
	 * @return a map of <code>String</code> (parameter name) to <code>List&lt;String&gt;</code> (a list of the values supplied).
	 */
	protected Map<String, List<String>> decodeParameters(Map<String, String> parms) {
		return this.decodeParameters(parms.get(QUERY_STRING_PARAMETER));
	}

	/**
	 * Decode parameters from a URL, handing the case where a single parameter name might have been
	 * supplied several times, by return lists of values.  In general these lists will contain a single
	 * element.
	 *
	 * @param queryString a query string pulled from the URL.
	 * @return a map of <code>String</code> (parameter name) to <code>List&lt;String&gt;</code> (a list of the values supplied).
	 */
	protected Map<String, List<String>> decodeParameters(String queryString) {
		Map<String, List<String>> parms = new HashMap<String, List<String>>();
		if (queryString != null) {
			StringTokenizer st = new StringTokenizer(queryString, "&");
			while (st.hasMoreTokens()) {
				String e = st.nextToken();
				int sep = e.indexOf('=');
				String propertyName = (sep >= 0) ? decodePercent(e.substring(0, sep)).trim() : decodePercent(e).trim();
				if (!parms.containsKey(propertyName)) {
					parms.put(propertyName, new ArrayList<String>());
				}
				String propertyValue = (sep >= 0) ? decodePercent(e.substring(sep + 1)) : null;
				if (propertyValue != null) {
					parms.get(propertyName).add(propertyValue);
				}
			}
		}
		return parms;
	}

	// ------------------------------------------------------------------------------- //
	//
	// Threading Strategy.
	//
	// ------------------------------------------------------------------------------- //

	/**
	 * Pluggable strategy for asynchronously executing requests.
	 *
	 * @param asyncRunner new strategy for handling threads.
	 */
	public void setAsyncRunner(AsyncRunner asyncRunner) {
		this.asyncRunner = asyncRunner;
	}

	// ------------------------------------------------------------------------------- //
	//
	// Temp file handling strategy.
	//
	// ------------------------------------------------------------------------------- //

	/**
	 * Pluggable strategy for creating and cleaning up temporary files.
	 *
	 * @param tempFileManagerFactory new strategy for handling temp files.
	 */
	public void setTempFileManagerFactory(TempFileManagerFactory tempFileManagerFactory) {
		this.tempFileManagerFactory = tempFileManagerFactory;
	}

	/**
	 * HTTP Request methods, with the ability to decode a <code>String</code> back to its enum value.
	 */
	public enum Method {
		GET, PUT, POST, DELETE, HEAD, OPTIONS;

		static Method lookup(String method) {
			for (Method m : Method.values()) {
				if (m.toString().equalsIgnoreCase(method)) {
					return m;
				}
			}
			return null;
		}
	}

	/**
	 * Pluggable strategy for asynchronously executing requests.
	 */
	public interface AsyncRunner {
		void exec(Runnable code);
	}

	/**
	 * Factory to create temp file managers.
	 */
	public interface TempFileManagerFactory {
		TempFileManager create();
	}

	// ------------------------------------------------------------------------------- //

	/**
	 * Temp file manager.
	 * <p/>
	 * <p>Temp file managers are created 1-to-1 with incoming requests, to create and cleanup
	 * temporary files created as a result of handling the request.</p>
	 */
	public interface TempFileManager {
		TempFile createTempFile() throws Exception;

		void clear();
	}

	/**
	 * A temp file.
	 * <p/>
	 * <p>Temp files are responsible for managing the actual temporary storage and cleaning
	 * themselves up when no longer needed.</p>
	 */
	public interface TempFile {
		OutputStream open() throws Exception;

		void delete() throws Exception;

		String getName();
	}

	/**
	 * Default threading strategy for NanoHttpd.
	 * <p/>
	 * <p>By default, the server spawns a new Thread for every incoming request.  These are set
	 * to <i>daemon</i> status, and named according to the request number.  The name is
	 * useful when profiling the application.</p>
	 */
	public static class DefaultAsyncRunner implements AsyncRunner {
		private long requestCount;

		@Override
		public void exec(Runnable code) {
			++requestCount;
			Thread t = new Thread(code);
			t.setDaemon(true);
			t.setName("NanoHttpd Request Processor (#" + requestCount + ")");
			t.start();
		}
	}

	/**
	 * Default strategy for creating and cleaning up temporary files.
	 * <p/>
	 * <p></p>This class stores its files in the standard location (that is,
	 * wherever <code>java.io.tmpdir</code> points to).  Files are added
	 * to an internal list, and deleted when no longer needed (that is,
	 * when <code>clear()</code> is invoked at the end of processing a
	 * request).</p>
	 */
	public static class DefaultTempFileManager implements TempFileManager {
		private final String tmpdir;
		private final List<TempFile> tempFiles;

		public DefaultTempFileManager() {
			tmpdir = System.getProperty("java.io.tmpdir");
			tempFiles = new ArrayList<TempFile>();
		}

		@Override
		public TempFile createTempFile() throws Exception {
			DefaultTempFile tempFile = new DefaultTempFile(tmpdir);
			tempFiles.add(tempFile);
			return tempFile;
		}

		@Override
		public void clear() {
			for (TempFile file : tempFiles) {
				try {
					file.delete();
				} catch (Exception ignored) {
				}
			}
			tempFiles.clear();
		}
	}

	/**
	 * Default strategy for creating and cleaning up temporary files.
	 * <p/>
	 * <p></p></[>By default, files are created by <code>File.createTempFile()</code> in
	 * the directory specified.</p>
	 */
	public static class DefaultTempFile implements TempFile {
		private File file;
		private OutputStream fstream;

		public DefaultTempFile(String tempdir) throws IOException {
			file = File.createTempFile("NanoHTTPD-", "", new File(tempdir));
			fstream = new FileOutputStream(file);
		}

		@Override
		public OutputStream open() throws Exception {
			return fstream;
		}

		@Override
		public void delete() throws Exception {
			safeClose(fstream);
			file.delete();
		}

		@Override
		public String getName() {
			return file.getAbsolutePath();
		}
	}


	public static final class ResponseException extends Exception {

		private final Response.Status status;

		public ResponseException(Response.Status status, String message) {
			super(message);
			this.status = status;
		}

		public ResponseException(Response.Status status, String message, Exception e) {
			super(message, e);
			this.status = status;
		}

		public Response.Status getStatus() {
			return status;
		}
	}

	/**
	 * Default strategy for creating and cleaning up temporary files.
	 */
	private class DefaultTempFileManagerFactory implements TempFileManagerFactory {
		@Override
		public TempFileManager create() {
			return new DefaultTempFileManager();
		}
	}



}
