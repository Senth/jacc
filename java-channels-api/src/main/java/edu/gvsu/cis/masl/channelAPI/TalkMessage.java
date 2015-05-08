package edu.gvsu.cis.masl.channelAPI;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.gvsu.cis.masl.channelAPI.ChannelAPI.InvalidMessageException;

/**
 * A "talk" message is a data structure containing lists of strings, integers and
 * (recursive) talk messages.
 */
class TalkMessage {
	public enum MessageEntryKind {
		ME_STRING,
		ME_NUMBER,
		ME_EMPTY,
		ME_TALKMESSAGE
	}

	private ArrayList<TalkMessage.TalkMessageEntry> mEntries;

	public TalkMessage() {
		mEntries = new ArrayList<TalkMessage.TalkMessageEntry>();
	}

	private TalkMessage(ArrayList<TalkMessage.TalkMessageEntry> entries) {
		mEntries = entries;
	}

	public List<TalkMessage.TalkMessageEntry> getEntries() {
		return mEntries;
	}

	public void parse(BufferedReader reader) throws InvalidMessageException {
		try {
			if (skipWhitespace(reader) != '[') {
				throw new InvalidMessageException("Expected initial [");
			}

			mEntries = parseMessage(reader);
		} catch (IOException e) {
			throw new InvalidMessageException(e);
		}
	}

	@Override
	public String toString() {
		String str = "[";
		for (TalkMessage.TalkMessageEntry entry : mEntries) {
			if (str != "[") {
				str += ",";
			}
			str += entry.toString();
		}
		return str + "]";
	}

	private static ArrayList<TalkMessage.TalkMessageEntry> parseMessage(BufferedReader reader) throws InvalidMessageException, IOException {
		ArrayList<TalkMessage.TalkMessageEntry> entries = new ArrayList<TalkMessage.TalkMessageEntry>();

		int ch = skipWhitespace(reader);
		while (ch != ']') {
			if (ch < 0) {
				throw new InvalidMessageException("Unexpected end-of-message.");
			}

			if (ch == '[') {
				ArrayList<TalkMessage.TalkMessageEntry> childEntries = parseMessage(reader);
				entries.add(new TalkMessageEntry(MessageEntryKind.ME_TALKMESSAGE, new TalkMessage(childEntries)));
			} else if (ch == '\"' || ch == '\'') {
				String stringValue = parseStringValue(reader, (char) ch);
				entries.add(new TalkMessageEntry(MessageEntryKind.ME_STRING, stringValue));
			} else if (ch == ',') {
				// blank entry
				entries.add(new TalkMessageEntry(MessageEntryKind.ME_EMPTY, null));
			} else {
				// we assume it's a number
				long numValue = parseNumberValue(reader, (char) ch);
				entries.add(new TalkMessageEntry(MessageEntryKind.ME_NUMBER, numValue));
			}

			// We expect a comma next, or the end of the message
			if (ch != ',') {
				ch = skipWhitespace(reader);
			}

			if (ch != ',' && ch != ']') {
				throw new InvalidMessageException("Expected , or ], found " + ((char) ch));
			} else if (ch == ',') {
				ch = skipWhitespace(reader);
			}
		}

		return entries;
	}

	private static String parseStringValue(BufferedReader reader, char quote) throws IOException {
		String str = "";
		for (int ch = reader.read(); ch > 0 && ch != quote; ch = reader.read()) {
			if (ch == '\\') {
				ch = reader.read();
				if (ch < 0) {
					break;
				}
			}
			str += (char) ch;
		}

		return str;
	}

	private static long parseNumberValue(BufferedReader reader, char firstChar) throws IOException {
		String str = "";
		for (int ch = firstChar; ch > 0 && Character.isDigit(ch); ch = reader.read()) {
			str += (char) ch;
			reader.mark(1);
		}
		reader.reset();

		return Long.parseLong(str);
	}

	private static int skipWhitespace(BufferedReader reader) throws IOException {
		int ch = reader.read();
		while (ch >= 0) {
			if (!Character.isWhitespace(ch)) {
				return ch;
			}
			ch = reader.read();
		}
		return -1;
	}

	static class TalkMessageEntry {
		TalkMessage.MessageEntryKind mKind;
		Object mValue;

		public TalkMessageEntry(TalkMessage.MessageEntryKind kind, Object value) {
			mKind = kind;
			mValue = value;
		}

		public TalkMessage.MessageEntryKind getKind() {
			return mKind;
		}

		public String getStringValue() throws InvalidMessageException {
			if (mKind == MessageEntryKind.ME_STRING) {
				return (String) mValue;
			} else {
				throw new InvalidMessageException("String value expected, found: " + mKind + " (" + mValue + ")");
			}
		}

		public long getNumberValue() throws InvalidMessageException {
			if (mKind == MessageEntryKind.ME_NUMBER) {
				return (Long) mValue;
			} else {
				throw new InvalidMessageException("Number value expected, found: " + mKind + " (" + mValue + ")");
			}
		}

		public TalkMessage getMessageValue() throws InvalidMessageException {
			if (mKind == MessageEntryKind.ME_TALKMESSAGE) {
				return (TalkMessage) mValue;
			} else {
				throw new InvalidMessageException("TalkMessage value expected, found: " + mKind + " (" + mValue + ")");
			}
		}

		@Override
		public String toString() {
			if (mKind == MessageEntryKind.ME_EMPTY) {
				return "";
			} else if (mKind == MessageEntryKind.ME_STRING) {
				return "\"" + mValue.toString() + "\"";
			} else {
				return mValue.toString();
			}
		}
	}
}