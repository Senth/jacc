package com.spiddekauga.net;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Builds a new HTTP URL Connection
 * @author Matteus Magnusson <matteus.magnusson@spiddekauga.com>
 */
public class HttpPostBuilder {
	/**
	 * Create a new connection as a POST request
	 * @param url string to parse as URL
	 * @throws IOException
	 * @throws MalformedURLException if no protocol is specified, or an unknown protocol
	 *         is found, or URL is null.
	 */
	public HttpPostBuilder(String url) throws MalformedURLException, IOException {
		mConnection = (HttpURLConnection) new URL(url).openConnection();
		mConnection.setDoOutput(true);
		mOutput = mConnection.getOutputStream();
		mWriter = new PrintWriter(mOutput);

		setCharset(mCharset);
		resetContentTypeBondary();
	}

	/**
	 * Override the default charset. Default is UTF-8 This should be set before calling
	 * any
	 * @param charset valid charset
	 */
	public void setCharset(String charset) {
		mCharset = charset;
		mConnection.setRequestProperty("Accept-Charset", charset);
	}

	/**
	 * Set the content type of the message. Default is application/x-www-form-urlencoded
	 * @param type content type
	 */
	public void setContentType(String type) {
		mContentType = type;
		resetContentTypeBondary();
	}

	/**
	 * Set the boundary between messages. Defaults to random UUID
	 * @param boundary message separator
	 */
	public void setBoundary(String boundary) {
		mBoundary = boundary;
		resetContentTypeBondary();
	}

	/**
	 * Reset content type and boundary
	 */
	private void resetContentTypeBondary() {
		mConnection.setRequestProperty("Content-Type", mContentType + "; boundary=" + mBoundary);
	}

	/**
	 * Add a text parameter to the connection. ContentType: text/plain
	 * @param name field name
	 * @param text value of the field
	 */
	public void addParameter(String name, CharSequence text) {
		addParameter(name, text, "text/plain");
	}

	/**
	 * Add a text parameter with the specific content type
	 * @param name field name
	 * @param text value of the field
	 * @param contentType type of content of value
	 */
	public void addParameter(String name, CharSequence text, String contentType) {
		beginParameter(name, null, contentType + "; charset=\"" + mCharset + "\"");
		mWriter.append(CRLF).append(text);
		endParameter();

	}

	/**
	 * Add a text parameter to the connection. ContentType: text/plain
	 * @param name field name
	 * @param text value of the field
	 */
	public void addParameter(String name, char[] text) {
		addParameter(name, text, "text/plain");
	}

	/**
	 * Add a text parameter with the specific content type
	 * @param name field name
	 * @param text value of the field
	 * @param contentType type of content of value
	 */
	public void addParameter(String name, char[] text, String contentType) {
		addParameter(name, new String(text));
	}

	/**
	 * Add binary parameter with as application/octet-stream
	 * @param name field name
	 * @param array binary array
	 * @throws IOException
	 */
	public void addParameter(String name, byte[] array) throws IOException {
		addParameter(name, array, "application/octet-stream");
	}

	/**
	 * Add binary parameter with the specified content type
	 * @param name field name
	 * @param array binary array
	 * @param contentType type of content value
	 * @throws IOException
	 */
	public void addParameter(String name, byte[] array, String contentType) throws IOException {
		beginParameter(name, null, contentType);
		mWriter.append("Content-Transfer-Encoding: binary").append(CRLF).append(CRLF).flush();
		mOutput.write(array);
		mOutput.flush();
		endParameter();
	}

	/**
	 * Add file parameter with as application/octet-stream. Adding a file requires the
	 * content type to be set as multipart/form-data in {@link #setContentType(String)}.
	 * @param name field name
	 * @param file the file to upload
	 * @throws IOException
	 */
	public void addParameter(String name, File file) throws IOException {
		addParameter(name, file, "application/octet-stream");
	}

	/**
	 * Add binary parameter with the specified content type. Adding a file requires the
	 * content type to be set as multipart/form-data in {@link #setContentType(String)}.
	 * @param name field name
	 * @param file binary array
	 * @param contentType type of content value
	 * @throws IOException
	 */
	public void addParameter(String name, File file, String contentType) throws IOException {
		addParameter(name, file, contentType, file.getName());
	}

	/**
	 * Add binary parameter with the specified content type. Adding a file requires the
	 * content type to be set as multipart/form-data in {@link #setContentType(String)}.
	 * @param name field name
	 * @param file binary array
	 * @param contentType type of content value
	 * @param filename custom filename
	 * @throws IOException
	 */
	public void addParameter(String name, File file, String contentType, String filename) throws IOException {
		beginParameter(name, filename, contentType);
		mWriter.append("Content-Transfer-Encoding: binary").append(CRLF).append(CRLF).flush();
		DataInputStream dataInputStream = new DataInputStream(new FileInputStream(file));
		byte[] bytes = new byte[(int) file.length()];
		dataInputStream.readFully(bytes);
		dataInputStream.close();
		mOutput.write(bytes);
		mOutput.flush();
		endParameter();
	}

	/**
	 * Begin writing a parameter
	 * @param name field name
	 * @param filename optional filename, set to null to skip usage
	 * @param contentType content type
	 */
	private void beginParameter(String name, String filename, String contentType) {
		mWriter.append("--").append(mBoundary).append(CRLF);
		mWriter.append("Content-Disposition: form-data; name=\"").append(name).append("\"");
		if (filename != null && !filename.isEmpty()) {
			mWriter.append("; filename=\"").append(filename).append("\"");
		}
		mWriter.append(CRLF);

		mWriter.append("Content-Type: ").append(contentType).append(CRLF);
	}

	/**
	 * End a parameter
	 */
	private void endParameter() {
		mWriter.append(CRLF).flush();
	}

	/**
	 * Finalizes the connection. Call getInputStream() on the HttpURLConnection to
	 * connect.
	 * @return a HttpURLConnection ready to make a connection and receive a response
	 */
	public HttpURLConnection build() {
		mWriter.append("--").append(mBoundary).append("--").append(CRLF).flush();
		return mConnection;
	}

	private HttpURLConnection mConnection = null;
	private OutputStream mOutput = null;
	private PrintWriter mWriter = null;
	/** Used a boundary between parameters */
	private String mBoundary = UUID.randomUUID().toString();
	private String mContentType = "application/x-www-form-urlencoded";
	private String mCharset = StandardCharsets.UTF_8.name();
	private static final String CRLF = "\r\n";
}
