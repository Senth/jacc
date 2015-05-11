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
import java.util.UUID;

/**
 * Builds a new HTTP URL Connection
 * @author Matteus Magnusson <matteus.magnusson@spiddekauga.com>
 */
public class HttpPostBuilder extends HttpParameterBuilder {
	/**
	 * Create a new connection as a POST request. To upload a file please call
	 * {@link #doFileUpload()}
	 * @param url string to parse as URL
	 * @throws IOException
	 * @throws MalformedURLException if no protocol is specified, or an unknown protocol
	 *         is found, or URL is null.
	 */
	public HttpPostBuilder(String url) throws MalformedURLException, IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		initConnection(connection);
	}

	/**
	 * Create a new POST connection from a GET URL. This doesn't remove the GET
	 * parameters. Rather it adds POST parameters to the URL request. To upload a file
	 * please call {@link #doFileUpload()}
	 * @param getBuilder builder for GET request
	 * @throws IOException
	 * @throws MalformedURLException
	 */
	public HttpPostBuilder(HttpGetBuilder getBuilder) throws MalformedURLException, IOException {
		HttpURLConnection connection = getBuilder.build();

		// Set charset from existing connection
		String charset = connection.getRequestProperty("Accept-Charset");
		if (charset != null) {
			mCharset = charset;
		}

		initConnection(connection);
	}

	/**
	 * Set this POST builder to send the data as multipart that can handle files. This
	 * method must be called before any calls to addParameter().
	 * @throws IllegalStateException If any addParamater() method has been called.
	 */
	public void doFileUpload() {
		mFileUpload = true;
		setContentType(FILE_UPLOAD_TYPE);
	}

	/**
	 * Initializes the connection and output
	 * @param connection the connection to the server
	 */
	private void initConnection(HttpURLConnection connection) {
		mConnection = connection;
		mConnection.setDoOutput(true);
		setCharset(mCharset);
		resetContentTypeBondary();
	}

	/**
	 * Initializes the output
	 * @throws IOException
	 */
	private void initOutput() throws IOException {
		if (mOutput == null) {
			mOutput = mConnection.getOutputStream();
			mWriter = new PrintWriter(mOutput);
		}
	}

	@Override
	public void setCharset(String charset) {
		super.setCharset(charset);
		if (mOutput == null) {
			mConnection.setRequestProperty("Accept-Charset", charset);
			resetContentTypeBondary();
		} else {
			throw new IllegalStateException("Output of parameters has already been started");
		}
	}

	/**
	 * Set the content type of the message. Default is application/x-www-form-urlencoded
	 * @param type content type
	 */
	private void setContentType(String type) {
		if (mOutput == null) {
			mContentType = type;
			resetContentTypeBondary();
		} else {
			throw new IllegalStateException("Output of parameters has already been started");
		}
	}

	/**
	 * Set the boundary between messages. Defaults to random UUID
	 * @param boundary message separator
	 */
	public void setBoundary(String boundary) {
		if (mOutput == null) {
			mBoundary = boundary;
			resetContentTypeBondary();
		} else {
			throw new IllegalStateException("Output of parameters has already been started");
		}
	}

	/**
	 * Reset content type and boundary
	 */
	private void resetContentTypeBondary() {
		if (mConnection != null) {
			String value = mContentType;
			if (mCharset != null && !mCharset.isEmpty()) {
				value += "; charset=" + mCharset;
			}
			if (mFileUpload && mBoundary != null && !mBoundary.isEmpty()) {
				value += "; boundary=" + mBoundary;
			}
			mConnection.setRequestProperty("Content-Type", value);
		}
	}

	@Override
	public void addParameter(String name, CharSequence text) {
		if (!mFileUpload) {
			super.addParameter(name, text);
		} else {
			addParameter(name, text, "text/plain");
		}
	}

	/**
	 * Add a text parameter with the specific content type
	 * @param name field name
	 * @param text value of the field
	 * @param contentType type of content of value
	 */
	protected void addParameter(String name, CharSequence text, String contentType) {
		try {
			initOutput();
			beginParameter(name, null, contentType + "; charset=\"" + mCharset + "\"");
			mWriter.append(CRLF).append(text);
			endParameter();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void addParameter(String name, byte[] array) {
		if (!mFileUpload) {
			super.addParameter(name, array);
		} else {
			addParameter(name, array, "application/octet-stream");
		}
	}

	/**
	 * Add binary parameter with the specified content type
	 * @param name field name
	 * @param array binary array
	 * @param contentType type of content value
	 */
	protected void addParameter(String name, byte[] array, String contentType) {
		try {
			initOutput();
			beginParameter(name, null, contentType);
			mWriter.append("Content-Transfer-Encoding: binary").append(CRLF).append(CRLF).flush();
			mOutput.write(array);
			mOutput.flush();
			endParameter();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Add file parameter with as application/octet-stream. Adding a file requires the
	 * content type to be set as multipart/form-data in {@link #setContentType(String)}.
	 * @param name field name
	 * @param file the file to upload
	 * @throws IOException
	 * @throws IllegalStateException if {@link #doFileUpload()} hasn't been called
	 */
	public void addFile(String name, File file) throws IOException {
		addFile(name, file, "application/octet-stream");
	}

	/**
	 * Add binary parameter with the specified content type. Adding a file requires the
	 * content type to be set as multipart/form-data in {@link #setContentType(String)}.
	 * @param name field name
	 * @param file binary array
	 * @param contentType type of content value
	 * @throws IOException
	 * @throws IllegalStateException if {@link #doFileUpload()} hasn't been called
	 */
	public void addFile(String name, File file, String contentType) throws IOException {
		addFile(name, file, contentType, file.getName());
	}

	/**
	 * Add binary parameter with the specified content type. Adding a file requires the
	 * content type to be set as multipart/form-data in {@link #setContentType(String)}.
	 * @param name field name
	 * @param file binary array
	 * @param contentType type of content value
	 * @param filename custom filename
	 * @throws IOException
	 * @throws IllegalStateException if {@link #doFileUpload()} hasn't been called
	 */
	public void addFile(String name, File file, String contentType, String filename) throws IOException {
		if (mFileUpload) {
			initOutput();
			beginParameter(name, filename, contentType);
			mWriter.append("Content-Transfer-Encoding: binary").append(CRLF).append(CRLF).flush();
			DataInputStream dataInputStream = new DataInputStream(new FileInputStream(file));
			byte[] bytes = new byte[(int) file.length()];
			dataInputStream.readFully(bytes);
			dataInputStream.close();
			mOutput.write(bytes);
			mOutput.flush();
			endParameter();
		} else {
			throw new IllegalStateException("doFileUpload() hasn't been called");
		}
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
	 * Set cookies
	 * @param cookieStorage
	 * @throws IllegalStateException if parameters have been added already
	 */
	public void setCookies(HttpCookieStorage cookieStorage) {
		setCookies(cookieStorage.getCookies());
	}

	/**
	 * Set cookies
	 * @param cookies
	 * @throws IllegalStateException if parameters have been added already
	 */
	void setCookies(String cookies) {
		if (mOutput == null) {
			mConnection.addRequestProperty("Cookie", cookies);
		} else {
			throw new IllegalStateException("Output of parameters has already been started");
		}
	}

	/**
	 * Finalizes the connection. Call getInputStream() on the HttpURLConnection to
	 * connect.
	 * @return a HttpURLConnection ready to make a connection and receive a response
	 */
	public HttpURLConnection build() {
		if (!mFileUpload) {
			try {
				initOutput();
				mWriter.append(mBuilder.toString()).flush();;
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			mWriter.append("--").append(mBoundary).append("--").append(CRLF).flush();
		}

		return mConnection;
	}

	private HttpURLConnection mConnection = null;
	private OutputStream mOutput = null;
	private PrintWriter mWriter = null;
	private String mBoundary = UUID.randomUUID().toString();
	private String mContentType = REGULAR_FORM_TYPE;
	private boolean mFileUpload = false;

	private static final String REGULAR_FORM_TYPE = "application/x-www-form-urlencoded";
	private static final String FILE_UPLOAD_TYPE = "multipart/form-data";
	private static final String CRLF = "\r\n";
}
