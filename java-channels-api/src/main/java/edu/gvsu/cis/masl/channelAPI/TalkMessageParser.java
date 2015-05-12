package edu.gvsu.cis.masl.channelAPI;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;

import edu.gvsu.cis.masl.channelAPI.ChannelAPI.ChannelException;
import edu.gvsu.cis.masl.channelAPI.ChannelAPI.InvalidMessageException;

/**
 * Helper class for parsing talk messages. Again, this protocol has been
 * reverse-engineered so it doesn't have a lot of error checking and is generally fairly
 * lenient.
 */
class TalkMessageParser {
	private HttpURLConnection mConnection = null;
	private BufferedReader mReader;


	/**
	 * Parses a Google Talk Message from an HTTP connection
	 * @param connection
	 * @throws ChannelException
	 */
	public TalkMessageParser(HttpURLConnection connection) throws ChannelException {
		try {
			mConnection = connection;
			mReader = new BufferedReader(new InputStreamReader(mConnection.getInputStream()));
		} catch (IOException e) {
			throw new ChannelException(e);
		}
	}


	/**
	 * Get the google talk message
	 * @return google talk message
	 * @throws ChannelException
	 */
	public TalkMessage getMessage() throws ChannelException {
		try {
			String submission = readSubmission();
			if (submission == null) {
				return null;
			}

			TalkMessage msg = new TalkMessage();

			try {
				msg.parse(new BufferedReader(new StringReader(submission)));
			} catch (InvalidMessageException e) {
				throw new ChannelException(e);
			}

			return msg;
		} catch (ChannelException e) {
			throw e;
		}
	}

	public void close() {
		try {
			mReader.close();
		} catch (IOException e) {
		}
	}

	private String readSubmission() throws ChannelException {
		try {
			String line = mReader.readLine();
			if (line == null) {
				return null;
			}

			int numChars = Integer.parseInt(line);
			char[] chars = new char[numChars];
			int total = 0;
			while (total < numChars) {
				int numRead = mReader.read(chars, total, numChars - total);
				total += numRead;
			}
			return new String(chars);
		} catch (IOException e) {
			throw new ChannelException(e);
		} catch (NumberFormatException e) {
			throw new ChannelException("Submission was not in expected format.", e);
		}
	}
}