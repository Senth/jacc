package com.spiddekauga.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.util.List;

/**
 * Keeps session/cookie data between multiple connections
 * @author Matteus Magnusson <matteus.magnusson@spiddekauga.com>
 */
public class HttpSessionConnection {
	/**
	 * Connects to a built HttpURLConnection.
	 * @param connection
	 * @return response from the server
	 * @throws IOException
	 */
	public synchronized InputStream connect(HttpURLConnection connection) throws IOException {
		if (mCookies != null) {
			for (String cookie : mCookies) {
				connection.addRequestProperty("Cookie", cookie.split(";", 2)[0]);
			}
		} else {
			CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
		}

		InputStream inputStream = connection.getInputStream();

		if (mCookies == null) {
			mCookies = connection.getHeaderFields().get("Set-Cookie");
		}

		return inputStream;
	}

	private List<String> mCookies = null;
}
