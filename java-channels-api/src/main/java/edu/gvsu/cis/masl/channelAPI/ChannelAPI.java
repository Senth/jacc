package edu.gvsu.cis.masl.channelAPI;

import java.io.IOException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import com.spiddekauga.http.HttpGetBuilder;
import com.spiddekauga.http.HttpPostBuilder;
import com.spiddekauga.http.HttpResponseParser;

/**
 * API for interacting with Channels on a Google App Engine server.
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
	private static SecureRandom mSecureRandom = new SecureRandom();

	private boolean mProduction = false;
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
		fixBaseUrl();
		mRequestId = 0;
		mMessageId = 1;
		mChannelId = createChannel(channelKey);
		mApplicationKey = channelKey;

		if (mChannelListener != null) {
			mChannelListener = channelService;
		}

		calculateProductionOrLocalDevelopmentUrl();
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
		fixBaseUrl();
		mChannelId = token;


		mApplicationKey = mChannelId.substring(mChannelId.lastIndexOf("-") + 1);
		if (mChannelListener != null) {
			mChannelListener = channelService;
		}

		calculateProductionOrLocalDevelopmentUrl();
	}

	/**
	 * Automatically calculates whether this is a production or local development instance
	 */
	private void calculateProductionOrLocalDevelopmentUrl() {
		setProduction(!mBaseUrl.contains("localhost"));
	}

	/**
	 * Sets whether this is a production or local development
	 * @param production true if production, false if local development
	 */
	public void setProduction(boolean production) {
		mProduction = production;
	}

	/**
	 * @return true if the connection is production, false if it's local development
	 */
	public boolean isProduction() {
		return mProduction;
	}

	/**
	 * Remove trailing slash from base URL
	 */
	private void fixBaseUrl() {
		if (mBaseUrl != null && !mBaseUrl.isEmpty() && mBaseUrl.charAt(mBaseUrl.length() - 1) == '/') {
			mBaseUrl = mBaseUrl.substring(0, mBaseUrl.length() - 1);
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

		// Production - AppEngine Mode
		if (isProduction()) {
			initialize();
			fetchSid();
			connect();
			longPoll();
		}
		// Local Development Mode
		else {
			connect(sendGet(getUrl("connect")));
		}
	}

	/**
	 * Sets up the initial connection, passes in the token
	 * @throws ChannelException
	 */
	private void initialize() throws ChannelException {

		JSONObject xpc = new JSONObject();
		try {
			xpc.put("cn", getRandomString());
			xpc.put("tp", "null");
			xpc.put("lpu", PROD_TALK_URL + "xpc_blank");
			xpc.put("ppu", mBaseUrl + CHANNEL_URL + "xpc_blank");

		} catch (JSONException e1) {
			// Does nothing
		}

		try {
			HttpGetBuilder getBuilder = new HttpGetBuilder(PROD_TALK_URL + "d");
			getBuilder.addParameter("token", mChannelId);
			getBuilder.addParameter("xpc", xpc.toString());

			HttpURLConnection connection = getBuilder.build();
			HttpNiceResponse niceResponse = new HttpNiceResponse(connection);
			if (niceResponse.getStatus() > 299) {
				throw new ChannelException("Initialize failed: " + niceResponse.getStatusText());
			}

			String response = niceResponse.getResponseText();

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

		TalkMessageParser parser = null;
		HttpURLConnection connection = null;
		try {
			HttpGetBuilder getBuilder = getBindUrl(false);
			getBuilder.addParameter("CVER", "1");

			HttpPostBuilder postBuilder = getBuilder.toPostBuilder();
			postBuilder.addParameter("count", "0");
			connection = postBuilder.build();

			parser = new TalkMessageParser(connection);
			TalkMessage msg = parser.getMessage();

			TalkMessage.TalkMessageEntry entry = msg.getEntries().get(0);
			entry = entry.getMessageValue().getEntries().get(1);
			List<TalkMessage.TalkMessageEntry> entries = entry.getMessageValue().getEntries();
			if (!entries.get(0).getStringValue().equals("c")) {
				throw new InvalidMessageException("Expected first value to be 'c', found: " + entries.get(0).getStringValue());
			}

			mSid = entries.get(1).getStringValue();

			parser.close();

			connection.disconnect();

		} catch (IOException | InvalidMessageException e) {
			if (parser != null) {
				parser.close();
			}
			if (connection != null) {
				connection.disconnect();
			}
			new ChannelException(e);
		}
	}

	/**
	 * We need to make this "connect" request to set up the binding.
	 * @throws ChannelException
	 */
	private void connect() throws ChannelException {
		try {
			HttpGetBuilder getBuilder = getBindUrl(false);
			getBuilder.addParameter("AID", mMessageId);
			getBuilder.addParameter("CVER", "1");


			HttpPostBuilder postBuilder = getBuilder.toPostBuilder();
			postBuilder.addParameter("count", "1");
			postBuilder.addParameter("ofs", "0");
			postBuilder.addParameter("req0_m", "[\"connect-add-client\"]");
			postBuilder.addParameter("req0_c", mClientId);
			postBuilder.addParameter("req0__sc", "c");

			HttpURLConnection connection = postBuilder.build();
			connection.connect();
			// Necessary for actually connecting...
			new HttpNiceResponse(connection);
			connection.disconnect();

		} catch (IOException e) {
			throw new ChannelException(e);
		}

		mChannelListener.onOpen();
	}

	/**
	 * Get the URL to the "/bind" endpoint.
	 * @param useRpc use RPC instead of request id
	 * @return HttpGetBuilder with the appropriate GET parameters set
	 * @throws IOException
	 */
	private HttpGetBuilder getBindUrl(boolean useRpc) throws IOException {
		HttpGetBuilder getBuilder = new HttpGetBuilder(PROD_TALK_URL + "dch/bind");

		getBuilder.addParameter("VER", "8");
		getBuilder.addParameter("token", mChannelId);
		getBuilder.addParameter("gsessionid", mSessionId);
		getBuilder.addParameter("clid", mClientId);
		getBuilder.addParameter("prop", "data");
		getBuilder.addParameter("zx", getRandomString());
		getBuilder.addParameter("t", "1");

		if (useRpc) {
			getBuilder.addParameter("RID", "rpc");
		} else {
			getBuilder.addParameter("RID", mRequestId);
			mRequestId++;
		}

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
			private void repoll() {
				try {
					HttpGetBuilder getBuilder = getBindUrl(true);
					getBuilder.addParameter("CI", "0");
					getBuilder.addParameter("AID", mMessageId);
					getBuilder.addParameter("TYPE", "xmlhttp");
					// getBuilder.addParameter("RID", "rpc");

					mConnection = getBuilder.build();
					mParser = new TalkMessageParser(mConnection);
				} catch (IOException | ChannelException e) {
					// Does nothing
				}
			}

			@Override
			public void run() {
				while (getReadyState() == ReadyState.OPEN) {
					if (mParser == null) {
						repoll();
						if (mParser == null) {
							try {
								Thread.sleep(2500);
							} catch (InterruptedException e) {
							}
							continue;
						}
					}
					try {
						TalkMessage msg = mParser.getMessage();
						if (msg == null) {
							mParser.close();
							mParser = null;
							mConnection.disconnect();
							mConnection = null;
						} else {
							handleMessage(msg);
						}
					} catch (ChannelException e) {
						mChannelListener.onError(500, e.getMessage());

						// Close the connection.
						// TODO try to connect again?
						try {
							close();
						} catch (IOException e1) {
							// Does nothing
						}
						return;
					}
				}

				if (mParser != null) {
					mParser.close();
				}
				if (mConnection != null) {
					mConnection.disconnect();
				}
			}

			private TalkMessageParser mParser = null;
			private HttpURLConnection mConnection = null;
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
		if (isProduction()) {
			setReadyState(ReadyState.CLOSED);
			mChannelListener.onClose();
		} else {
			setReadyState(ReadyState.CLOSING);
			disconnect(sendGet(getUrl("disconnect")));
		}
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
		HttpURLConnection connection = getBuilder.build();
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
	 * @return randomized string
	 */
	private String getRandomString() {
		return new BigInteger(130, mSecureRandom).toString(32);
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