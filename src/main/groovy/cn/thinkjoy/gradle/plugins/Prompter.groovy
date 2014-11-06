package cn.thinkjoy.gradle.plugins;

public class Prompter {

	private static final String LINE_SEP = System.getProperty("line.separator");

	private static final String PROMPT = "${LINE_SEP}??>"


	String readLine(String message, String defaultValue = null) {
		String _message = "$PROMPT $message" + (defaultValue ? " [$defaultValue] " : "")
		if (System.console()) {
			return System.console().readLine(_message) ?: defaultValue
		}
		println "$_message (WAITING FOR INPUT BELOW)"
		return System.in.newReader().readLine() ?: defaultValue
	}

	String readUsername(String message) {
		String _message = "$PROMPT $message:"

		return  System.console().readLine(_message)
	}

	String readPassword(String message) {
		String _message = "$PROMPT $message:"

		return  new String(System.console().readPassword(_message))
	}
}
