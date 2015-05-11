package com.spiddekauga.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;

/**
 * Keeps session/cookie data between multiple connections
 * @author Matteus Magnusson <matteus.magnusson@spiddekauga.com>
 */
public class HttpCookieStorage {
	/**
	 * Store initial cookies. If cookies has been set already this method does nothing
	 * @param connection HTTP connection
	 * @throws IOException
	 */
	public synchronized void storeInitialCookies(HttpURLConnection connection) throws IOException {
		if (mCookies == null) {
			connection.getInputStream();
			List<String> cookies = connection.getHeaderFields().get("Set-Cookie");

			if (cookies != null && !cookies.isEmpty()) {
				StringBuilder stringBuilder = new StringBuilder();
				for (String cookie : cookies) {
					stringBuilder.append(cookie.split(";", 2)[0]);
				}
				mCookies = stringBuilder.toString();
			}
		}
	}

	/**
	 * @return get session cookie
	 */
	synchronized String getCookies() {
		return mCookies;
	}

	private String mCookies = null;
}
