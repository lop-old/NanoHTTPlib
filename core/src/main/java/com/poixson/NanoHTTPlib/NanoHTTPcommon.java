package com.poixson.NanoHTTPlib;

import java.io.Closeable;


public abstract class NanoHTTPcommon implements Closeable {

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
