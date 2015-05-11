package edu.gvsu.cis.masl.channelAPI;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.RandomStringUtils;
import org.json.JSONException;
import org.json.JSONObject;

import com.spiddekauga.net.HttpCookieStorage;
import com.spiddekauga.net.HttpGetBuilder;
import com.spiddekauga.net.HttpPostBuilder;
import com.spiddekauga.net.HttpResponseParser;

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
	private Thread mtPoll = null;

	private HttpCookieStorage mHttpSession = new HttpCookieStorage();

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
	 */
	public ChannelAPI(String URL, String channelKey, ChannelService channelService) throws IOException {
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
	 */
	private String createChannel(String key) throws IOException {
		String token = "";

		HttpGetBuilder builder = new HttpGetBuilder(mBaseUrl + "/token");
		builder.addParameter("c", key);
		HttpURLConnection connection = builder.build();
//		mHttpSession.storeInitialCookies(connection);

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
			// Does nothing
		}

		HttpGetBuilder builder = new HttpGetBuilder(PROD_TALK_URL + "d");
		builder.addParameter("token", mChannelId);
		builder.addParameter("xpc", xpc.toString());
		builder.setCookies(mHttpSession);

		try {
			HttpURLConnection connection = builder.build();
			mHttpSession.storeInitialCookies(connection);
			String response = HttpResponseParser.getStringResponse(connection);

			Pattern pattern = Pattern.compile("chat\\.WcsDataClient\\(([^\\)]+)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
			Matcher matcher = pattern.matcher(response);

			if (matcher.find()) {
				String fields = matcher.group(1);

				pattern = Pattern.compile("\"([^\"]*?)\"[\\s,]*", Pattern.MULTILINE);
				matcher = pattern.matcher(fields);

				for (int i = 0; i < 7; i++) {
					if (!matcher.find()) {
						throw new ChannelException("Expected iteration #" + i + " to find something.");
					}
					if (i == 2) {
						mClientId = matcher.group(1);
					} else if (i == 3) {
						mSessionId = matcher.group(1);
					} else if (i == 6) {
						if (!mChannelId.equals(matcher.group(1))) {
							throw new ChannelException("Tokens do not match!");
						}
					}
				}
			}

			connection.disconnect();

		} catch (IOException e) {
			throw new ChannelException(e);
		}
	}

	/**
	 * Fetches and parses the SID, which is a kind of session ID.
	 * @throws ChannelException
	 */
	private void fetchSid() throws ChannelException {
		HttpGetBuilder getBuilder = getBindUrl();
		getBuilder.addParameter("CVER", "1");

		try {
			HttpPostBuilder postBuilder = getBuilder.toPostBuilder();
			postBuilder.setCookies(mHttpSession);
			getBuilder = null;
			postBuilder.addParameter("count", "0");
			HttpURLConnection connection = postBuilder.build();

			TalkMessageParser parser = new TalkMessageParser(connection);
			TalkMessage msg = parser.getMessage();

			TalkMessage.TalkMessageEntry entry = msg.getEntries().get(0);
			entry = entry.getMessageValue().getEntries().get(1);
			List<TalkMessage.TalkMessageEntry> entries = entry.getMessageValue().getEntries();
			if (!entries.get(0).getStringValue().equals("c")) {
				throw new InvalidMessageException("Expected first value to be 'c', found: " + entries.get(0).getStringValue());
			}

			mSid = entries.get(1).getStringValue();

			connection.disconnect();

		} catch (IOException | InvalidMessageException e) {
			new ChannelException(e);
		}
	}

	/**
	 * We need to make this "connect" request to set up the binding.
	 * @throws ChannelException
	 */
	private void connect() throws ChannelException {
		HttpGetBuilder getBuilder = getBindUrl();
		getBuilder.addParameter("AID", mMessageId);
		getBuilder.addParameter("CVER", "1");
		getBuilder.setCookies(mHttpSession);

		try {
			HttpPostBuilder postBuilder = getBuilder.toPostBuilder();
			postBuilder.addParameter("count", "1");
			postBuilder.addParameter("ofs", "0");
			postBuilder.addParameter("req0_m", "[\"connect-add-client\"]");
			postBuilder.addParameter("req0_c", mClientId);
			postBuilder.addParameter("req0__sc", "c");

			HttpURLConnection connection = postBuilder.build();
			connection.connect();
			connection.disconnect();

		} catch (IOException e) {
			throw new ChannelException(e);
		}

		mChannelListener.onOpen();
	}

	/**
	 * Get the URL to the "/bind" endpoint.
	 * @return HttpGetBuilder with the appropriate GET parameters set
	 */
	private HttpGetBuilder getBindUrl() {
		HttpGetBuilder getBuilder = new HttpGetBuilder(PROD_TALK_URL + "dch/bind");

		getBuilder.addParameter("VER", "8");
		getBuilder.addParameter("token", mChannelId);
		getBuilder.addParameter("gsessionid", mSessionId);
		getBuilder.addParameter("clid", mClientId);
		getBuilder.addParameter("prop", "data");
		getBuilder.addParameter("zx", UUID.randomUUID().toString());
		getBuilder.addParameter("t", "1");
		getBuilder.addParameter("RID", mRequestId);
		mRequestId++;

		if (mSid != null && !mSid.isEmpty()) {
			getBuilder.addParameter("SID", mSid);
		}

		return getBuilder;
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
				HttpGetBuilder getBuilder = getBindUrl();
				getBuilder.addParameter("CI", "0");
				getBuilder.addParameter("AID", mMessageId);
				getBuilder.addParameter("TYPE", "xmlhttp");
				getBuilder.addParameter("RID", "rpc");
				getBuilder.setCookies(mHttpSession);

				HttpURLConnection connection = null;
				TalkMessageParser talkMessageParser = null;
				try {
					connection = getBuilder.build();
					talkMessageParser = new TalkMessageParser(connection);
				} catch (IOException | ChannelException e) {
					// Does nothing
				} finally {
					if (connection != null) {
						connection.disconnect();
					}
				}
				return talkMessageParser;
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
							continue;
						}
					}
					try {
						TalkMessage msg = parser.getMessage();
						if (msg == null) {
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
	 * @param msg google talk message
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
	 * Development getUrl
	 * @param command
	 * @return development URL
	 * @throws IOException
	 */
	private String getUrl(String command) throws IOException {
		String url = mBaseUrl + CHANNEL_URL + "dev?command=" + command + "&channel=";

		url += URLEncoder.encode(mChannelId, StandardCharsets.UTF_8.name());
		if (mClientId != null) {
			url += "&client=" + URLEncoder.encode(mClientId, StandardCharsets.UTF_8.name());
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
	 * @param response
	 */
	private void forwardMessage(HttpNiceResponse response) {
		if (response.isSuccess()) {
			String data = chomp(response.getResponseText());
			if (data != null && !data.isEmpty()) {
				mChannelListener.onMessage(data);
			}
		} else {
			mChannelListener.onError(response.getStatus(), response.getStatusText());
			setReadyState(ReadyState.ERROR);
		}
	}

	/**
	 * Chomp the specified message. Removing the last "\r\n", "\n", or "\r" if it's the
	 * end of the string. Only removes the last newline.
	 * @param message the message to chomp
	 * @return string without newline, null if null String input
	 */
	private String chomp(String message) {
		if (message == null) {
			return null;
		}

		int length = message.length();
		int lastIndex = length - 1;
		int penultimateIndex = length - 2;
		if (message.charAt(lastIndex) == '\n') {
			// Check for "\r\n"
			if (message.charAt(penultimateIndex) == '\r') {
				return message.substring(0, penultimateIndex);
			}
			// Just '\n'
			else {
				return message.substring(0, lastIndex);
			}
		}
		// Check for '\r'
		else if (message.charAt(lastIndex) == '\r') {
			return message.substring(0, lastIndex);
		}

		return message;
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
	 * @param response
	 */
	private void forwardSendComplete(HttpNiceResponse response) {
		if (!response.isSuccess()) {
			mChannelListener.onError(response.getStatus(), response.getStatusText());
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

		HttpPostBuilder postBuilder = new HttpPostBuilder(url);
		postBuilder.addParameter("channelKey", mApplicationKey);
		postBuilder.addParameter("message", message);
		HttpURLConnection connection = postBuilder.build();
		HttpNiceResponse niceResponse = new HttpNiceResponse(connection);
		forwardSendComplete(niceResponse);

		return true;
	}

	/**
	 * Send a HTTP GET request and get the response as a string
	 * @param url
	 * @return nice response from a HTTP request
	 * @throws MalformedURLException
	 * @throws IOException
	 */
	private HttpNiceResponse sendGet(String url) throws MalformedURLException, IOException {
		HttpGetBuilder getBuilder = new HttpGetBuilder(url);
		// getBuilder.setCookies(mHttpSession);
		HttpURLConnection connection = getBuilder.build();
		// mHttpSession.storeInitialCookies(connection);
		HttpNiceResponse response = new HttpNiceResponse(connection);
		connection.disconnect();
		return response;
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