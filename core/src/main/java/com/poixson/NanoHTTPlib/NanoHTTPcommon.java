package com.poixson.NanoHTTPlib;

import java.io.Closeable;


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






}
