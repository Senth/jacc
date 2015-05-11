package edu.gvsu.cis.masl.channelAPI;

import java.io.IOException;
import java.net.HttpURLConnection;

import org.apache.http.HttpStatus;

import com.spiddekauga.net.HttpResponseParser;

/**
 * Nice wrapper for HTTP responses
 */
public class HttpNiceResponse {
	private String mResponseText;
	private Integer mStatus;
	private String mStatusText;

	/**
	 * Handles the Response of a HttpRequest, grab data later by using Getters
	 * @param connection HTTP connection
	 * @throws IOException
	 */
	public HttpNiceResponse(HttpURLConnection connection) throws IOException {
		mStatus = connection.getResponseCode();
		mStatusText = connection.getResponseMessage();

		// OK -> Get message
		if (mStatus == HttpURLConnection.HTTP_OK) {
			mResponseText = HttpResponseParser.getStringResponse(connection);
		}
	}

	// @formatter:off
	/**
	 * @return HTTP response message, if any, returned along with the response code from a server. From
	 * responses like:
	 * HTTP/1.0 200 OK
	 * HTTP/1.0 404 Not Found
	 * Extracts the Strings "OK" and "Not Found" respectively. Returns null if none could be discerned from the
	 * responses (the result was not valid HTTP).
	 */
	// @formatter:on
	public String getStatusText() {
		return mStatusText;
	}

	// @formatter:off
	/**
	 * @return Status code from an HTTP response message. For example, in the case of the following status
	 * lines:
	 * HTTP/1.0 200 OK
	 * HTTP/1.0 401 Unauthorized
	 * It will return 200 and 401 respectively. Returns -1 if no code can be discerned from the response (i.e., the
	 * response is not valid HTTP).
	 */
	// @formatter:on
	public int getStatus() {
		return mStatus;
	}

	/**
	 * @return true on good connection
	 */
	public boolean isSuccess() {
		return (mStatus == HttpStatus.SC_OK);
	}

	/**
	 * @return the message from the server
	 */
	public String getResponseText() {
		return mResponseText;
	}

	/**
	 * Override toString Method, makes for nice print statements
	 */
	@Override
	public String toString() {
		StringBuffer out = new StringBuffer();
		out.append("Status: ");
		out.append(mStatus);
		out.append(", Error: ");
		out.append(mStatusText);
		out.append(", Message: ");
		out.append(mResponseText);
		return out.toString();
	}
}