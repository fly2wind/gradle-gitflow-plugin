package cn.thinkjoy.gradle.plugins.credentials

import org.eclipse.jgit.errors.UnsupportedCredentialItem
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.URIish

import cn.thinkjoy.gradle.plugins.Prompter
import com.google.common.base.Strings;

class ConsoleCredentialsProvider extends CredentialsProvider {

	private Prompter prompter

	private String username
	private String password


	ConsoleCredentialsProvider(Prompter prompter) {
		this.prompter = prompter
	}

	@Override
	public boolean isInteractive() {
		return true
	}

	@Override
	public boolean supports(CredentialItem... items) {
		for (CredentialItem i : items) {
			if (i instanceof CredentialItem.StringType)
				continue

			else if (i instanceof CredentialItem.CharArrayType)
				continue

			else if (i instanceof CredentialItem.YesNoType)
				continue

			else if (i instanceof CredentialItem.InformationalMessage)
				continue

			else
				return false
		}
		return true
	}

	@Override
	public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
		boolean ok = true;

		for (CredentialItem item : items) {
			ok = getCredentialItem(item)
		}
		return ok
	}


	private boolean getCredentialItem(CredentialItem item) {
		if (item instanceof CredentialItem.StringType) {
			if (item.isValueSecure()) {
				String v = askPassword(item.getPromptText())
				if (v != null) {
					item.setValue(v)
					return true
				}else {
					return false
				}
			} else {
				String v = askUsername(item.getPromptText())
				if (v != null) {
					item.setValue(v)
					return true
				}else {
					return false
				}
			}
		}else if (item instanceof CredentialItem.CharArrayType) {
			if (item.isValueSecure()) {
				String v = askPassword(item.getPromptText())
				if (v != null) {
					item.setValue(v.toCharArray())
					return true
				}else {
					return false
				}
			} else {
				String v = askUsername(item.getPromptText())
				if (v != null) {
					item.setValue(v.toCharArray())
					return true
				}else {
					return false
				}
			}
		}else if (item instanceof CredentialItem.InformationalMessage) {
			try {
				prompter.readPassword(item.getPromptText())
			} catch (Exception e) {
				e.printStackTrace()
			}
			return true
		}else {
			return false;
		}
	}


	private String askPassword(String prompted) {
		try {
			if (this.password == null) {
				this.password = prompter.readPassword(prompted);
			}
			return this.password;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private String askUsername(String prompted) {
		try {
			if (this.username == null) {
				this.username = Strings.emptyToNull(prompter.readUsername(prompted));
			}
			return this.username;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}
}
