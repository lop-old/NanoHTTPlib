package com.poixson.NanoHTTPlib;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
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


	// ------------------------------------------------------------------------------- //


	/**
	 * Worker contains the connected socket and thread, and handles communications.
	 */
	public static class httpServerWorker extends Thread implements Closeable {

		private final NanoHTTPserver parent;
		private final int index;
		private final Socket socket;
		private final InputStream in;
		private final OutputStream out;

		// requests
		private volatile int countRequests = 0;


		public httpServerWorker(final int index, final NanoHTTPserver parent,
				final Socket accept, final InputStream in, final OutputStream out) {
			if(accept == null) throw new NullPointerException();
			if(in  == null) throw new NullPointerException();
			if(out == null) throw new NullPointerException();
			this.index = index;
			this.parent = parent;
			this.socket = accept;
			this.in  = in;
			this.out = out;
			// thread name
			{
				final StringBuilder name = new StringBuilder();
				name.append(parent.getThreadName());
				name.append("[").append(Integer.toString(index)).append("]");
				this.setName(name.toString());
			}
			// start listener thread
			this.setDaemon(true);
			this.start();
		}


		@Override
		public void run() {
//TempFileManager tempFileManager = tempFileManagerFactory.create();
//final httpTempFileManager tempFiles = null;
//			final httpSession session = new httpServerSession(tempFiles, in, out);
			httpServerRequest request = null;
			httpServerResponse result = null;
			while(!socket.isClosed()) {
				try {
					// wait for then parse the headers and load data key/value pairs
					request = new httpServerRequest(in);
					// find a handler to execute request
					result = parent.serve(request);
				} catch (SocketTimeoutException ignore) {
					request = null;
					result = null;
					break;
				} catch (NanoHTTPserver.httpResponseException e) {
					result = e.getResponse(request);
					e.printStackTrace();
					break;
				} catch (Exception e) {
					// When the socket is closed by the client, we throw our own SocketException
					// to break the "keep alive" loop above.
//					if(!(e instanceof SocketException && EXCEPTION_SHUTDOWN_MSG.equals(e.getMessage())))
					result = null;
					e.printStackTrace();
					break;
				}
				// handle errors
				if(request == null || result == null) break;
				// +1 request
				incrementRequests();
				if(result.isChunked()) break;
				// send the result
				send(result);
				request = null;
				result = null;
			}
			if(request != null) {
				if(result == null) {
					result = new httpServerResponse(
						request,
						httpStatus.INTERNAL_ERROR,
						NanoHTTPserver.DEFAULT_MIME,
						""
					);
				}
				// send the result
				if(result != null)
					send(result);
			}
			request = null;
			result = null;
			// close
			NanoHTTPserver.safeClose(this);
		}
		public void send(httpServerResponse result) {
			if(result == null) return;
			try {
				result.send(out);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}


		@Override
		public void close() throws IOException {
			NanoHTTPserver.safeClose(socket);
			NanoHTTPserver.safeClose(in);
			NanoHTTPserver.safeClose(out);
			parent.unregisterWorker(this);
		}
		public boolean isClosed() {
			return socket.isClosed() || !Thread.currentThread().isAlive();
		}


		protected int incrementRequests() {
			parent.incrementRequests();
			return ++countRequests;
		}
		public int getRequests() {
			return countRequests;
		}


	}


	// ------------------------------------------------------------------------------- //


	public static class httpServerRequest {

		public final BufferedReader reader;

		private final Properties pre     = new Properties();
		private final Properties query   = new Properties();
		private final Properties headers = new Properties();
//		private final Properties files   = new Properties();


		/**
		 * Decodes the sent headers and loads the data into Key/value pairs
		 */
		public httpServerRequest(InputStream in) throws IOException {
			reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
//			// decode the header into java properties

			// read the request line
			final StringTokenizer token;
			{
				final String line = reader.readLine();
				if(line == null) return;
System.out.println(line);
				token = new StringTokenizer(line);
			}
			if(!token.hasMoreTokens())
				throw new NanoHTTPserver.httpResponseException(
					httpStatus.BAD_REQUEST,
					"BAD REQUEST: Syntax error. Usage: GET /example/file.html"
				);
			pre.put("method", token.nextToken());
			if(!token.hasMoreTokens())
				throw new NanoHTTPserver.httpResponseException(
					httpStatus.BAD_REQUEST,
					"BAD REQUEST: Missing URI. Usage: GET /example/file.html"
				);
			String uri = token.nextToken();
			// decode parameters from the URI
			final int qmi = uri.indexOf('?');
			if(qmi >= 0) {
				decodeQuery(uri.substring(qmi+1), query);
				uri = decodePercent(uri.substring(0, qmi));
			} else {
				uri = decodePercent(uri);
			}
			pre.put("uri", uri);
			// If there's another token, it's protocol version,
			// followed by HTTP headers. Ignore version but parse headers.
			// NOTE: this now forces header names lowercase since they are
			// case insensitive and vary by client.
			if(token.hasMoreTokens()) {
				String line = reader.readLine();
				while(line != null && line.trim().length() > 0) {
					final int p = line.indexOf(':');
					if(p >= 0)
						headers.put(
							line.substring(0, p).trim().toLowerCase(Locale.US),
							line.substring(p+1).trim()
						);
					line = reader.readLine();
				}
			}
//			} catch () {
//				throw new ResponseException(httpStatus.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: "+e.getMessage(), e);
//			}
//String remoteIp = addr.isLoopbackAddress() || addr.isAnyLocalAddress() ? "127.0.0.1" : addr.getHostAddress().toString();
//headers.put("remote-addr", remoteIp);
//headers.put("http-client-ip", remoteIp);
//String remoteIp = inetAddress.isLoopbackAddress() || inetAddress.isAnyLocalAddress() ? "127.0.0.1" : inetAddress.getHostAddress().toString();
//headers = new HashMap<String, String>();
//headers.put("remote-addr", remoteIp);
//headers.put("http-client-ip", remoteIp);
		}


		/**
		 * HTTP request method.
		 * @return httpMethod object.
		 */
		public httpMethod getMethod() {
			final String str = (String) this.pre.get("method");
			if(str == null || str.isEmpty())
				return null;
			return httpMethod.lookup(str);
		}


		/**
		 * Decodes parameters in percent-encoded URI-format
		 * ( e.g. "name=Jack%20Daniels&pass=Single%20Malt" )
		 * and adds them to given Properties.
		 * NOTE: this doesn't support multiple identical keys due to the
		 * simplicity of Properties -- if you need multiples, you might want to
		 * replace the Properties with a Hashtable of Vectors or such.
		 */
		protected static void decodeQuery(String parms, Properties query) {
			if(parms == null) return;
			final StringTokenizer token = new StringTokenizer(parms, "&");
			while(token.hasMoreTokens()) {
				final String str = token.nextToken();
				final int p = str.indexOf('=');
				if(p >= 0) {
					query.put(
						decodePercent(str.substring(0, p)),
						decodePercent(str.substring(p+1))
					);
				} else {
					query.put(
						decodePercent(str),
						""
					);
				}
//				query.put(
//					decodePercent(
//						(p >= 0) ? str.substring(0, p) : str
//					).trim(),
//					(p >= 0) ? decodePercent(str.substring(p+1)) : ""
//				);
			}
		}
		/**
		 * Decode percent encoded <code>String</code> values.
		 * @param str the percent encoded <code>String</code>
		 * @return expanded form of the input, for example "foo%20bar" becomes "foo bar"
		 */
		protected static String decodePercent(String str) {
			String decoded = null;
			try {
				decoded = URLDecoder.decode(str, NanoHTTPserver.UTF8);
			} catch (UnsupportedEncodingException ignored) {}
			return decoded;
		}


	}


	// ------------------------------------------------------------------------------- //


	/**
	 * HTTP response. Return one of these from serve().
	 */
	public static class httpServerResponse {

//		private final httpServerRequest request;

		// HTTP status code after processing, e.g. "200 OK", HTTP_OK
		private volatile httpStatus status = httpStatus.OK;
		// MIME type of content, e.g. "text/html"
		private volatile httpMime mime = null; // NanoHTTPserver.DEFAULT_MIME
		// Request method used for this request.
		private final httpMethod method;

		// Headers for the HTTP response. Use addHeader() to add lines.
		private final Map<String, String> headers = new HashMap<String, String>();
		// Data of the response.
		private volatile InputStream data = null;
		// Send data in chunked mode (rather than fixed length)
		private volatile boolean chunked = false;
		// basic http authentication
		private volatile httpBasicAuth basicAuth = null;


		/**
		 * Convenience method assuming OK status and plain text mime type.
		 */
		public httpServerResponse(httpServerRequest request, String msg) {
			this(request, httpStatus.OK, httpMime.PLAINTEXT, msg);
		}
		/**
		 * Convenience method that makes an InputStream out of given text.
		 */
		public httpServerResponse(httpServerRequest request, httpStatus status, httpMime mime, String msg) {
			this(request, status, mime, (InputStream) null);
			if(msg != null) {
				try {
					this.data = new ByteArrayInputStream(
						msg.getBytes(NanoHTTPserver.UTF8)
					);
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
			}
		}
		/**
		 * Basic constructor.
		 */
		public httpServerResponse(httpServerRequest request, httpStatus status, httpMime mime, InputStream data) {
			if(request == null) throw new NullPointerException("request cannot be null");
//			this.request = request;
			this.method = request.getMethod();
			if(status != null)
				this.status = status;
			if(mime != null)
				this.mime = mime;
			if(data != null)
				this.data = data;
		}


		/**
		 * Sends given response to the socket.
		 */
		public void send(OutputStream out) {
			if(out == null) throw new NullPointerException();
			// local copies
			final httpStatus tmpStatus = this.status;
			final httpMime tmpMime = this.mime == null ? NanoHTTPserver.DEFAULT_MIME : this.mime;
			final Map<String, String> tmpHeaders = new HashMap<String, String>(this.headers);
			final InputStream tmpData = this.data;
			final boolean tmpChunked = this.chunked;
			final httpBasicAuth tmpBasicAuth = (this.basicAuth == null ? null : this.basicAuth.clone());
			// validate data
			if(tmpStatus == null) throw new Error("send(): Status can't be null.");
			// build http headers
			final String EOL = "\r\n";
			final PrintWriter pw = new PrintWriter(out);
			try {
				pw.print("HTTP/1.1 "+tmpStatus.toString()+EOL);
				// date/time
				if(tmpHeaders.get("Date") == null)
					pw.print("Date: "+getDateTime()+EOL);
				// server software
				pw.print("Server: NanoHTTPlib/"+NanoHTTPserver.version+EOL);
				// basic auth
				if(tmpBasicAuth != null)
					pw.print("WWW-Authenticate: Basic realm=\""+tmpBasicAuth.getRealm()+"\""+EOL);
				// HEAD method safety
				if(httpMethod.HEAD.equals(this.method))
					data = null;
				// content size
				int pending = -1;
				if(!tmpChunked && tmpData != null) {
					pending = tmpData.available();
					pw.print("Accept-Ranges: bytes"+EOL);
					pw.print("Content-Length: "+Integer.toString(pending)+EOL);
					pw.print("Connection: keep-alive"+EOL);
//					pw.print("Content-MD5: "+MD5(data)+EOL);
				} else {
					chunked = true;
					pw.print("Transfer-Encoding: chunked"+EOL);
					pw.print("Connection: close"+EOL);
				}
				// content type
				pw.print("Content-Type: "+tmpMime.toString()+EOL);
				// custom headers
				if(!tmpHeaders.isEmpty()) {
					for(Entry<String, String> entry : tmpHeaders.entrySet())
						pw.print(entry.getKey()+": "+entry.getValue()+EOL);
				}
				// headers finished
				pw.print(EOL);
				pw.flush();
				// send data
				if(pending > 0) {
					final byte[] CRLF = EOL.getBytes();
					final int BUFFER_SIZE = 16 * 1024; // 16K
					final byte[] buff = new byte[BUFFER_SIZE];
					int read;
					// buffer and send in chunks
					if(tmpChunked) {
						while((read = data.read(buff)) > 0) {
							out.write(String.format("%x"+EOL, read).getBytes());
							out.write(buff, 0, read);
							out.write(CRLF);
						}
						out.write(String.format("0"+EOL+EOL).getBytes());

					// fixed buffer, all fits at once
					} else {
						while(pending > 0) {
							final int size = (pending > BUFFER_SIZE) ? BUFFER_SIZE : pending;
							read = data.read(buff, 0, size);
							if(read <= 0) break;
							out.write(buff, 0, read);
							pending -= read;
						}
					}
					out.flush();
				}
			} catch (Exception e) {
			//} catch (IOException e) {
				// Couldn't write? No can do.
				e.printStackTrace();
			} finally {
//				NanoHTTPserver.safeClose(out);
				NanoHTTPserver.safeClose(data);
			}
		}


		/**
		 * Get formatted date/time timestamp for http response header.
		 *
		 * @return example: Thu, 02 Jan 2014 16:34:42 GMT
		 */
		private final String getDateTime() {
			final SimpleDateFormat gmtFormat =
				new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
			gmtFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
			return gmtFormat.format(new Date());
		}


		public boolean isChunked() {
			return chunked;
		}


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
