package edu.gvsu.cis.masl.channelAPI;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import com.spiddekauga.net.HttpGetBuilder;
import com.spiddekauga.net.HttpResponseParser;
import com.spiddekauga.net.HttpSessionConnection;

/**
 * API for interacating with Channels on a Google App Engine server.
 * @author Jonathan Engelsma (https://github.com/jengelsma) Original author of ChannelAPI
 * @author Matteus Magnusson <matteus.magnusson@spiddekauga.com> Migrated class from
 *         Apache's HttpClient to HttpURLConnection for Android compatibility.
 */
public class ChannelAPI {

	private enum ReadyState {
		CONNECTING,
		OPEN,
		ERROR,
		CLOSING,
		CLOSED
	};

	private static final String DEFAULT_URL = "http://localhost:8888";
	private static final Integer TIMEOUT_MS = 500;
	private static final String CHANNEL_URL = "/_ah/channel/";
	private static final String PROD_TALK_URL = "https://talkgadget.google.com/talkgadget/";

	private String mBaseUrl = DEFAULT_URL;
	private String mChannelId = null;
	private String mApplicationKey = null;
	private String mClientId = null;
	private Integer mRequestId = 0;
	private String mSessionId = null;
	private String mSid = null;
	private long mMessageId = 1;
	private ChannelService mChannelListener = new ChannelListener();
	private ReadyState mReadyState = ReadyState.CLOSED;
	private HttpClient mHttpClient = HttpClientBuilder.create().build();
	private Thread mtPoll = null;

	private HttpSessionConnection mHttpSession = new HttpSessionConnection();

	/**
	 * Default Constructor
	 */
	public ChannelAPI() {
		mClientId = null;
		mChannelId = null;
		mRequestId = null;
		mSessionId = null;
		mRequestId = 0;
		mMessageId = 1;
		mApplicationKey = null;
	}

	/**
	 * Create A Channel, Using URL, ChannelKey and a ChannelService
	 * @param URL - Server Location - http://localhost:8888
	 * @param channelKey - Unique Identifier for channel groups, server uses this to push
	 *        data to clients, can have multiple clients on the same key, but only 1
	 *        channel per client
	 * @param channelService - An Implementation of the ChannelService class, this is
	 *        where the function methods will get called when the server pushes data
	 * @throws IOException JSON Related
	 * @throws ClientProtocolException Connection Related
	 */
	public ChannelAPI(String URL, String channelKey, ChannelService channelService) throws IOException, ClientProtocolException {
		mClientId = null;
		mBaseUrl = URL;
		mRequestId = 0;
		mMessageId = 1;
		mChannelId = createChannel(channelKey);
		mApplicationKey = channelKey;

		if (mChannelListener != null) {
			mChannelListener = channelService;
		}
	}

	/**
	 * Ability to join an existing Channel with a full channel token, URL, and
	 * ChannelService
	 * @param URL - Server Location - http://localhost:8888
	 * @param token - Unique token returned by the App-Engine server implementation from a
	 *        previously created channel
	 * @param channelService - An Implementation of the ChannelService class, this is
	 *        where the function methods will get called when the server pushes data
	 */
	public void joinChannel(String URL, String token, ChannelService channelService) {
		mClientId = null;
		mBaseUrl = URL;
		mChannelId = token;


		mApplicationKey = mChannelId.substring(mChannelId.lastIndexOf("-") + 1);
		if (mChannelListener != null) {
			mChannelListener = channelService;
		}
	}

	/**
	 * Create a Channel on the Server and return the channelID + Key
	 * @param key
	 * @return String: Channel 'Token/ID'
	 * @throws IOException
	 * @throws ClientProtocolException
	 */
	private String createChannel(String key) throws IOException, ClientProtocolException {
		String token = "";

		HttpGetBuilder builder = new HttpGetBuilder(mBaseUrl + "/token");
		builder.addParameter("c", key);
		HttpURLConnection connection = builder.build();
		mHttpSession.connect(connection);

		try {
			JSONObject json = new JSONObject(HttpResponseParser.getStringResponse(connection));
			token = json.getString("token");
		} catch (JSONException e) {
			System.out.println("Error: Parsing JSON");
		}
		connection.disconnect();

		return token;
	}


	/**
	 * Connect to the Channel Decides to use either Production Mode / Development Mode
	 * based on "localhost" being found in the BASE_URL
	 * @throws IOException
	 * @throws ChannelException
	 */
	public void open() throws IOException, ChannelException {
		setReadyState(ReadyState.CONNECTING);

		// Local Development Mode
		if (mBaseUrl.contains("localhost")) {
			connect(sendGet(getUrl("connect")));
		}
		// Production - AppEngine Mode
		else {
			initialize();
			fetchSid();
			connect();
			longPoll();
		}
	}

	/**
	 * Sets up the initial connection, passes in the token
	 * @throws ChannelException
	 */
	private void initialize() throws ChannelException {

		JSONObject xpc = new JSONObject();
		try {
			xpc.put("cn", RandomStringUtils.random(10, true, false));
			xpc.put("tp", "null");
			xpc.put("lpu", PROD_TALK_URL + "xpc_blank");
			xpc.put("ppu", mBaseUrl + CHANNEL_URL + "xpc_blank");

		} catch (JSONException e1) {

		}

		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("token", mChannelId));
		params.add(new BasicNameValuePair("xpc", xpc.toString()));

		String initUri = PROD_TALK_URL + "d?" + URLEncodedUtils.format(params, "UTF-8");

		HttpGet httpGet = new HttpGet(initUri);
		try {
			HttpResponse resp = mHttpClient.execute(httpGet);
			if (resp.getStatusLine().getStatusCode() > 299) {
				throw new ChannelException("Initialize failed: " + resp.getStatusLine());
			}

			String html = IOUtils.toString(resp.getEntity().getContent(), "UTF-8");
			consume(resp.getEntity());

			Pattern p = Pattern.compile("chat\\.WcsDataClient\\(([^\\)]+)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
			Matcher m = p.matcher(html);
			if (m.find()) {
				String fields = m.group(1);
				p = Pattern.compile("\"([^\"]*?)\"[\\s,]*", Pattern.MULTILINE);
				m = p.matcher(fields);

				for (int i = 0; i < 7; i++) {
					if (!m.find()) {
						throw new ChannelException("Expected iteration #" + i + " to find something.");
					}
					if (i == 2) {
						mClientId = m.group(1);
					} else if (i == 3) {
						mSessionId = m.group(1);
					} else if (i == 6) {
						if (!mChannelId.equals(m.group(1))) {
							throw new ChannelException("Tokens do not match!");
						}
					}
				}
			}
		} catch (IOException e) {
			throw new ChannelException(e);
		}
	}

	/**
	 * Fetches and parses the SID, which is a kind of session ID.
	 * @throws ChannelException
	 */
	private void fetchSid() throws ChannelException {

		String uri = getBindString(new BasicNameValuePair("CVER", "1"));

		HttpPost httpPost = new HttpPost(uri);

		List<NameValuePair> data = new ArrayList<NameValuePair>();
		data.add(new BasicNameValuePair("count", "0"));
		try {
			httpPost.setEntity(new UrlEncodedFormEntity(data));
		} catch (UnsupportedEncodingException e) {

		}

		TalkMessageParser parser = null;
		try {
			HttpResponse resp = mHttpClient.execute(httpPost);
			parser = new TalkMessageParser(resp);
			TalkMessage msg = parser.getMessage();

			TalkMessage.TalkMessageEntry entry = msg.getEntries().get(0);
			entry = entry.getMessageValue().getEntries().get(1);
			List<TalkMessage.TalkMessageEntry> entries = entry.getMessageValue().getEntries();
			if (!entries.get(0).getStringValue().equals("c")) {
				throw new InvalidMessageException("Expected first value to be 'c', found: " + entries.get(0).getStringValue());
			}

			mSid = entries.get(1).getStringValue();
		} catch (ClientProtocolException e) {
			throw new ChannelException(e);
		} catch (IOException e) {
			throw new ChannelException(e);
		} catch (InvalidMessageException e) {
			throw new ChannelException(e);
		} finally {
			if (parser != null) {
				parser.close();
			}
		}
	}

	/**
	 * We need to make this "connect" request to set up the binding.
	 */
	private void connect() throws ChannelException {
		String uri = getBindString(new BasicNameValuePair("AID", Long.toString(mMessageId)), new BasicNameValuePair("CVER", "1"));

		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("count", "1"));
		params.add(new BasicNameValuePair("ofs", "0"));
		params.add(new BasicNameValuePair("req0_m", "[\"connect-add-client\"]"));
		params.add(new BasicNameValuePair("req0_c", mClientId));
		params.add(new BasicNameValuePair("req0__sc", "c"));

		HttpEntity entity = null;
		try {
			entity = new UrlEncodedFormEntity(params);
		} catch (UnsupportedEncodingException e) {

		}

		HttpPost httpPost = new HttpPost(uri);
		httpPost.setEntity(entity);
		try {
			HttpResponse resp = mHttpClient.execute(httpPost);
			consume(resp.getEntity());
		} catch (ClientProtocolException e) {
			throw new ChannelException(e);
		} catch (IOException e) {
			throw new ChannelException(e);
		}

		mChannelListener.onOpen();
	}

	/**
	 * Gets the URL to the "/bind" endpoint.
	 * @param extraParams
	 * @return
	 */
	private String getBindString(NameValuePair... extraParams) {
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("token", mChannelId));
		params.add(new BasicNameValuePair("gsessionid", mSessionId));
		params.add(new BasicNameValuePair("clid", mClientId));
		params.add(new BasicNameValuePair("prop", "data"));
		params.add(new BasicNameValuePair("zx", RandomStringUtils.random(12, true, false)));
		params.add(new BasicNameValuePair("t", "1"));
		if (mSid != null && mSid != "") {
			params.add(new BasicNameValuePair("SID", mSid));
		}
		for (int i = 0; i < extraParams.length; i++) {
			params.add(extraParams[i]);
		}

		params.add(new BasicNameValuePair("RID", Integer.toString(mRequestId)));
		mRequestId++;

		return PROD_TALK_URL + "dch/bind?VER=8&" + URLEncodedUtils.format(params, "UTF-8");
	}

	/**
	 * Grabbing Data "Production" Path
	 */
	private void longPoll() {
		if (mtPoll != null) {
			return;
		}

		mtPoll = new Thread(new Runnable() {
			private TalkMessageParser repoll() {
				String bindString = getBindString(new BasicNameValuePair("CI", "0"), new BasicNameValuePair("AID", Long.toString(mMessageId)),
						new BasicNameValuePair("TYPE", "xmlhttp"), new BasicNameValuePair("RID", "rpc"));

				HttpGet httpGet = new HttpGet(bindString);
				HttpResponse resp = null;
				try {
					resp = mHttpClient.execute(httpGet);
					return new TalkMessageParser(resp);
				} catch (ClientProtocolException e) {
				} catch (IOException e) {
				} catch (ChannelException e) {
				}

				return null;
			}

			@Override
			public void run() {
				TalkMessageParser parser = null;
				while (getReadyState() == ReadyState.OPEN) {
					if (parser == null) {
						parser = repoll();
						if (parser == null) {
							try {
								Thread.sleep(2500);
							} catch (InterruptedException e) {
							}
						}
					}
					try {
						TalkMessage msg = parser.getMessage();
						if (msg == null) {
							parser.close();
							parser = null;
						} else {
							handleMessage(msg);
						}
					} catch (ChannelException e) {
						mChannelListener.onError(500, e.getMessage());

						return;
					}
				}
			}
		});

		setReadyState(ReadyState.OPEN);
		mtPoll.start();
	}

	/**
	 * Used each time we receive a message on the Production side, filters garbage data
	 * from actual data
	 */
	private void handleMessage(TalkMessage msg) {
		try {
			List<TalkMessage.TalkMessageEntry> entries = msg.getEntries();
			msg = entries.get(0).getMessageValue();

			entries = msg.getEntries();
			mMessageId = entries.get(0).getNumberValue();

			msg = entries.get(1).getMessageValue();
			entries = msg.getEntries();

			if (entries.get(0).getKind() == TalkMessage.MessageEntryKind.ME_STRING && entries.get(0).getStringValue().equals("c")) {
				msg = entries.get(1).getMessageValue();
				entries = msg.getEntries();

				String thisSessionID = entries.get(0).getStringValue();
				if (!thisSessionID.equals(mSessionId)) {
					mSessionId = thisSessionID;
				}

				msg = entries.get(1).getMessageValue();
				entries = msg.getEntries();

				if (entries.get(0).getStringValue().equalsIgnoreCase("ae")) {
					String msgValue = entries.get(1).getStringValue();
					mChannelListener.onMessage(msgValue);
				}
			}
		} catch (InvalidMessageException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This exception will be thrown any time we have an issue parsing a talk message.
	 * Probably this means they've changed the protocol on us.
	 */
	@SuppressWarnings("javadoc")
	public static class InvalidMessageException extends Exception {
		private static final long serialVersionUID = 1L;

		public InvalidMessageException(String msg) {
			super(msg);
		}

		public InvalidMessageException(Throwable e) {
			super(e);
		}
	}

	/**
	 * Sets the ready state
	 * @param readyState
	 */
	private synchronized void setReadyState(ReadyState readyState) {
		mReadyState = readyState;
	}

	/**
	 * @return current ready state
	 */
	private synchronized ReadyState getReadyState() {
		return mReadyState;
	}

	/**
	 * Close the Channel, Channel is gone from the server now, a new Channel is required
	 * if you wish to reconnect, you can't re-use the old one
	 * @throws IOException
	 */
	public void close() throws IOException {
		setReadyState(ReadyState.CLOSING);
		disconnect(sendGet(getUrl("disconnect")));
	}

	/**
	 * A helper method that consumes an HttpEntity so that the HttpClient can be reused.
	 * If you're not planning to run on Android, you can use the non-deprecated
	 * EntityUtils.consume() method instead.
	 */
	@SuppressWarnings("deprecation")
	static void consume(HttpEntity entity) {
		// Grab Everything
		try {
			if (entity != null) {
				entity.consumeContent();
			}
		} catch (IOException e) {
			// Don't Worry About
		}
	}

	/**
	 * Development getUrl
	 * @param command
	 * @return
	 * @throws IOException
	 */
	private String getUrl(String command) throws IOException {
		String url = mBaseUrl + CHANNEL_URL + "dev?command=" + command + "&channel=";

		url += URLEncoder.encode(mChannelId, "UTF-8");
		if (mClientId != null) {
			url += "&client=" + URLEncoder.encode(mClientId, "UTF-8");
		}
		return url;
	};

	/**
	 * If you successfully connect to the Channel, start pulling data. If you fail to
	 * connect, the error is printed to the terminal and the connection is closed.
	 * @param xhr
	 */
	private void connect(HttpNiceResponse xhr) {
		if (xhr.isSuccess()) {
			mClientId = xhr.getResponseText();
			setReadyState(ReadyState.OPEN);
			mChannelListener.onOpen();
			poll();
		} else {
			setReadyState(ReadyState.CLOSING);
			mChannelListener.onError(xhr.getStatus(), xhr.getStatusText());
			setReadyState(ReadyState.CLOSED);
			mChannelListener.onClose();
		}
	}

	/**
	 * Closing the channel
	 * @param xhr
	 */
	private void disconnect(HttpNiceResponse xhr) {
		setReadyState(ReadyState.CLOSED);
		mChannelListener.onClose();
	}

	/**
	 * @param xhr
	 */
	private void forwardMessage(HttpNiceResponse xhr) {
		if (xhr.isSuccess()) {
			String data = StringUtils.chomp(xhr.getResponseText());
			if (!StringUtils.isEmpty(data)) {
				mChannelListener.onMessage(data);
			}
		} else {
			mChannelListener.onError(xhr.getStatus(), xhr.getStatusText());
			setReadyState(ReadyState.ERROR);
		}
	}

	/**
	 * Grabbing Data "DEV"
	 */
	private void poll() {
		if (mtPoll == null) {
			mtPoll = new Thread(new Runnable() {

				@Override
				public void run() {
					while (getReadyState() == ReadyState.OPEN) {
						try {
							HttpNiceResponse response = sendGet(getUrl("poll"));
							forwardMessage(response);
							Thread.sleep(TIMEOUT_MS);
						} catch (Exception e) {
							// Does nothing
						}
					}
				}
			});
			mtPoll.start();
		}
	}

	/**
	 * Send Message On, If there is an error notify the channelListener!
	 * @param xhr
	 */
	private void forwardSendComplete(HttpNiceResponse xhr) {
		if (!xhr.isSuccess()) {
			mChannelListener.onError(xhr.getStatus(), xhr.getStatusText());
		}
	}

	/**
	 * Used to send a message to the server
	 * @param message
	 * @param urlPattern - where the server should look for the message. ex: "/chat"
	 * @return true
	 * @throws IOException
	 */
	public boolean send(String message, String urlPattern) throws IOException {
		if (getReadyState() != ReadyState.OPEN) {
			return false;
		}
		String url = mBaseUrl + urlPattern;
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("channelKey", mApplicationKey));
		params.add(new BasicNameValuePair("message", message));
		forwardSendComplete(sendPost(url, params));
		return true;
	}

	/**
	 * Send an HTTPPOST, convenience function
	 * @param url
	 * @param params
	 * @return XHR, nice responses from httpRequests
	 * @throws IOException
	 */
	private HttpNiceResponse sendPost(String url, List<NameValuePair> params) throws IOException {
		HttpClient sendClient = HttpClientBuilder.create().build();
		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
		HttpPost httpPost = new HttpPost(url);
		httpPost.setEntity(entity);
		return new HttpNiceResponse(sendClient.execute(httpPost));
	}

	/**
	 * Send a HTTP GET request and get the response as a string
	 * @param url
	 * @return nice response from a HTTP request
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	private HttpNiceResponse sendGet(String url) throws MalformedURLException, IOException {
		HttpURLConnection connection = new HttpGetBuilder(url).build();
		mHttpSession.connect(connection);
		return new HttpNiceResponse(connection);
	}

	/**
	 * Set a new ChannelListener
	 * @param channelListener
	 */
	public void setChannelListener(ChannelService channelListener) {
		if (channelListener != null) {
			mChannelListener = channelListener;
		}
	}

	/**
	 * This exception is thrown in case of errors.
	 */
	@SuppressWarnings("javadoc")
	public static class ChannelException extends Exception {
		private static final long serialVersionUID = 1L;

		public ChannelException() {
		}

		public ChannelException(Throwable cause) {
			super(cause);
		}

		public ChannelException(String message) {
			super(message);
		}

		public ChannelException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}