package com.spiddekauga.net;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Builds HTTP GET and POST parameters.
 * @author Matteus Magnusson <matteus.magnusson@spiddekauga.com>
 */
class HttpParameterBuilder {
	/**
	 * Add a parameter to the request
	 * @param name field name
	 */
	public void addParameter(String name) {
		addParameter(name, (CharSequence) null);
	}

	/**
	 * Add a parameter to the request
	 * @param name field name
	 * @param text the value of the parameter (can be null)
	 */
	public void addParameter(String name, CharSequence text) {
		addSeparator();
		try {
			mBuilder.append(URLEncoder.encode(name, mCharset));
			if (text != null && text.length() > 0) {
				mBuilder.append("=").append(URLEncoder.encode(text.toString(), mCharset));
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Add a parameter to the request
	 * @param name field name
	 * @param text the value of the parameter (can be null)
	 */
	public void addParameter(String name, char[] text) {
		addParameter(name, new String(text));
	}

	/**
	 * Add a parameter to the request
	 * @param name field name
	 * @param number the value of the parameter (can be null)
	 */
	public void addParameter(String name, Number number) {
		addParameter(name, String.valueOf(number));
	}

	/**
	 * Add binary parameter.
	 * @param name field name
	 * @param array binary array. Will be encoded as a string and then URL-encoded.
	 */
	public void addParameter(String name, byte[] array) {
		String byteString = new String(array);
		addParameter(name, byteString);
	}

	/**
	 * Adds a separator between URL and parameter or parameters.
	 */
	protected void addSeparator() {
		if (mAddedParameter) {
			mBuilder.append("&");
		} else {
			mAddedParameter = true;
		}
	}

	/**
	 * Set the charset to use. UTF-8 by default. Don't change this after calling
	 * addParameter(...)
	 * @param charset
	 */
	public void setCharset(String charset) {
		mCharset = charset;
	}

	// REMOVE
	protected void printCookies(HttpURLConnection connection) {


		// StringBuilder stringBuilder = new StringBuilder();
		// try {
		// URI uri = connection.getURL().toURI();
		// stringBuilder.append("URL: " + uri + "\n");
		// stringBuilder.append(CookieHandler.getDefault().get(uri, new HashMap<String,
		// List<String>>()));
		// } catch (IOException | URISyntaxException e) {
		// e.printStackTrace();
		// }
		//
		// mLogger.info(stringBuilder.toString());
	}

	// REMOVE
	protected Logger mLogger = Logger.getLogger(getClass().getSimpleName());
	/** Added parameter */
	protected boolean mAddedParameter = false;
	/** Charset the parameters are encoded into */
	protected String mCharset = StandardCharsets.UTF_8.name();
	/** The parameter builder */
	protected StringBuilder mBuilder = new StringBuilder();
}
