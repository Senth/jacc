package edu.gvsu.cis.masl.channelAPI;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;

import org.apache.http.HttpResponse;

import edu.gvsu.cis.masl.channelAPI.ChannelAPI.ChannelException;
import edu.gvsu.cis.masl.channelAPI.ChannelAPI.InvalidMessageException;

/**
 * Helper class for parsing talk messages. Again, this protocol has been
 * reverse-engineered so it doesn't have a lot of error checking and is generally
 * fairly lenient.
 */
class TalkMessageParser {
	private HttpResponse mHttpResponse;
	private BufferedReader mReader;

	public TalkMessageParser(HttpResponse resp) throws ChannelException {
		try {
			mHttpResponse = resp;
			InputStream ins = resp.getEntity().getContent();
			mReader = new BufferedReader(new InputStreamReader(ins));
		} catch (IllegalStateException e) {
			throw new ChannelException(e);
		} catch (IOException e) {
			throw new ChannelException(e);
		}
	}

	public TalkMessage getMessage() throws ChannelException {
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
	}

	public void close() {
		try {
			mReader.close();
		} catch (IOException e) {
		}

		if (mHttpResponse != null) {
			ChannelAPI.consume(mHttpResponse.getEntity());
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