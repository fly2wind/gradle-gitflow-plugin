package cn.thinkjoy.gradle.plugins.credentials;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.util.FS;

import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;
import com.jcraft.jsch.agentproxy.USocketFactory;
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector;
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory;



public class SessionCredentialsProvider extends JschConfigSessionFactory {

	@Override
	protected void configure(Host hc, Session session) {
		session.setConfig("StrictHostKeyChecking", "no");
	}

	@Override
	protected JSch createDefaultJSch(FS fs) throws JSchException {
		Connector con = null;
		JSch jsch = null;

		try {
			if (SSHAgentConnector.isConnectorAvailable()) {
				USocketFactory usf = new JNAUSocketFactory();
				con = new SSHAgentConnector(usf);
			}
		}
		catch (AgentProxyException e) {
			System.out.println(e.getMessage());
		}

		if (null == con) {
			jsch = super.createDefaultJSch(fs);

			return jsch;
		}
		else {
			jsch = new JSch();
			jsch.setConfig("PreferredAuthentications", "publickey");
			IdentityRepository irepo = new RemoteIdentityRepository(con);
			jsch.setIdentityRepository(irepo);

			//why these in super is private, I don't know
			knownHosts(jsch, fs);
			identities(jsch, fs);
			return jsch;
		}
	}

	//copied from super class
	private void knownHosts(final JSch sch, FS fs) throws JSchException
	{
		final File home = fs.userHome();
		if (home == null)
		{ return; }
		final File known_hosts = new File(new File(home, ".ssh"), "known_hosts");
		try
		{
			FileInputStream input = new FileInputStream(known_hosts);
			try
			{
				sch.setKnownHosts(input);
			}
			finally
			{
				input.close();
			}
		}
		catch (FileNotFoundException none)
		{
			// Oh well. They don't have a known hosts in home.
		}
		catch (IOException err)
		{
			// Oh well. They don't have a known hosts in home.
		}
	}

	private void identities(final JSch sch, FS fs)
	{
		final File home = fs.userHome();
		if (home == null)
		{ return; }
		final File sshdir = new File(home, ".ssh");
		if (sshdir.isDirectory())
		{
			loadIdentity(sch, new File(sshdir, "identity"));
			loadIdentity(sch, new File(sshdir, "id_rsa"));
			loadIdentity(sch, new File(sshdir, "id_dsa"));
		}
	}

	private void loadIdentity(final JSch sch, final File priv)
	{
		if (priv.isFile())
		{
			try
			{
				sch.addIdentity(priv.getAbsolutePath());
			}
			catch (JSchException e)
			{
				// Instead, pretend the key doesn't exist.
			}
		}
	}

}
