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
	 * Request handler.
	 */
	public interface httpIO {
		public httpServerResponse serve(httpServerRequest request);
	}


	// ------------------------------------------------------------------------------- //


	/**
	 * Response exception
	 */
	public static final class httpResponseException extends IOException {
		private static final long serialVersionUID = 1L;
		private final httpStatus status;
		public httpResponseException(String msg) {
			super(msg);
			this.status = httpStatus.INTERNAL_ERROR;
		}
		public httpResponseException(httpStatus status, String msg) {
			super(msg);
			this.status = status;
		}
		public httpResponseException(String msg, Exception e) {
			super(msg, e);
			this.status = httpStatus.INTERNAL_ERROR;
		}
		public httpResponseException(httpStatus status, String msg, Exception e) {
			super(msg, e);
			this.status = status;
		}
		public httpStatus getStatus() {
			return status;
		}
		public httpServerResponse getResponse(httpServerRequest request) {
			if(request == null) return null;
			return new httpServerResponse(request, this.getStatus(), DEFAULT_MIME, this.getMessage());
		}
	}












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
