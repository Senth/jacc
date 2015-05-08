package edu.gvsu.cis.masl.channelAPI;

import java.io.IOException;
import java.net.HttpURLConnection;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;

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
	 * @param response
	 * @throws IOException
	 */
	@Deprecated
	public HttpNiceResponse(HttpResponse response) throws IOException {
		StatusLine statusLine = response.getStatusLine();
		HttpEntity httpEntity = response.getEntity();
		mStatus = statusLine.getStatusCode();
		mStatusText = statusLine.getReasonPhrase();
		mResponseText = IOUtils.toString(httpEntity.getContent(), "UTF-8");
	}

	/**
	 * Handles the Response of a HttpRequest, grab data later by using Getters
	 * @param connection HTTP connection
	 * @throws IOException
	 */
	public HttpNiceResponse(HttpURLConnection connection) throws IOException {
		mResponseText = HttpResponseParser.getStringResponse(connection);
		mStatus = connection.getResponseCode();
		mStatusText = connection.getResponseMessage();
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
		return this.mStatusText;
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
		return this.mStatus;
	}

	/**
	 * @return true on good connection
	 */
	public boolean isSuccess() {
		return (this.mStatus == HttpStatus.SC_OK);
	}

	/**
	 * @return the message from the server
	 */
	public String getResponseText() {
		return this.mResponseText;
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