package com.spiddekauga.net;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Create a new connection as a GET request
 * @author Matteus Magnusson <matteus.magnusson@spiddekauga.com>
 */
public class HttpGetBuilder {
	/**
	 * Create a new connection as a GET request
	 * @param url string to parse as URL
	 */
	public HttpGetBuilder(String url) {
		mBuilder.append(url);
	}

	/**
	 * Set the charset to use. UTF-8 by default. Don't change this after calling
	 * {@link #addParameter(String, String)}
	 * @param charset
	 */
	public void setCharset(String charset) {
		mCharset = charset;
	}

	/**
	 * Add a parameter to the request
	 * @param name field name
	 * @param value the value of the parameter (can be null)
	 */
	public void addParameter(String name, String value) {
		addSeparator();
		try {
			mBuilder.append(URLEncoder.encode(name, mCharset));
			if (value != null && !value.isEmpty()) {
				mBuilder.append("=").append(URLEncoder.encode(value, mCharset));
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Adds a separator between url and parameter or parameters.
	 */
	private void addSeparator() {
		if (mAddedParameter) {
			mBuilder.append("&");
		} else {
			mBuilder.append("?");
			mAddedParameter = true;
		}
	}

	/**
	 * Build the connection
	 * @return HttpURLConnection with a GET request set
	 * @throws IOException
	 * @throws MalformedURLException
	 */
	public HttpURLConnection build() throws MalformedURLException, IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(mBuilder.toString()).openConnection();
		connection.setRequestProperty("Accept-Charset", mCharset);
		return connection;
	}

	private String mCharset = StandardCharsets.UTF_8.name();
	private boolean mAddedParameter = false;
	private StringBuilder mBuilder = new StringBuilder();
}
