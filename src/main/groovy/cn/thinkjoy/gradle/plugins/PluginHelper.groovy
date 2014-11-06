package cn.thinkjoy.gradle.plugins

import java.io.File
import java.text.MessageFormat
import java.util.HashMap
import java.util.List
import java.util.Map
import java.util.Set

import org.codehaus.plexus.logging.Logger
import org.codehaus.plexus.util.StringUtils
import org.eclipse.jgit.api.AddCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.StoredConfig
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshSessionFactory
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.mvn3.org.apache.maven.artifact.ArtifactUtils

import cn.thinkjoy.gradle.plugins.credentials.ConsoleCredentialsProvider
import cn.thinkjoy.gradle.plugins.credentials.SessionCredentialsProvider

import com.atlassian.jgitflow.core.JGitFlow
import com.atlassian.jgitflow.core.JGitFlowReporter
import com.google.common.base.Strings
import com.google.common.collect.ImmutableMap 


class PluginHelper {
	private static final String LINE_SEP = System.getProperty('line.separator')

	private static final String PROMPT = "${LINE_SEP}??>"
	
	Prompter prompter = new Prompter()
	
	Project project

	String getReleaseVersion() {
		String defaultVersion = project.version

		String suggestVersion = null
		String releaseVersion = defaultVersion

		while (null == releaseVersion || ArtifactUtils.isSnapshot(releaseVersion)) {
			VersionInfo info = null
			try {
				info = new VersionInfo(defaultVersion)
			}catch (Exception e) {
				if (project.gitflow.interactive) {
					try {
						info = new VersionInfo("1.0.0")
					}catch (Exception e1) {
						throw new GradleException("error parsing 1.0.0 version!!!", e1)
					}
				}else {
					throw new GradleException("error parsing release version: ${e.message}", e)
				}
			}

			suggestVersion = info.getReleaseVersionString()

			if(project.gitflow.interactive) {
				releaseVersion = prompter.readLine("What is the release version for this ${project.name}:", suggestVersion)
			}else {
				releaseVersion = suggestVersion
			}
		}
		return releaseVersion
	}

	String getDevelopVersion() {
		String defaultVersion = project.version

		String suggestVersion = null
		String developVersion = defaultVersion

		while (null == developVersion || !ArtifactUtils.isSnapshot(developVersion)) {
			VersionInfo info = null
			try {
				info = new VersionInfo(defaultVersion)
			}catch (Exception e) {
				if (project.gitflow.interactive) {
					try {
						info = new VersionInfo("1.0.0")
					}catch (Exception e1) {
						throw new GradleException("error parsing 1.0.0 version!!!", e1)
					}
				}else {
					throw new GradleException("error parsing develop version: ${e.message}", e)
				}
			}

			suggestVersion = info.getNextVersion().getSnapshotVersionString()

			if(project.gitflow.interactive) {
				developVersion = prompter.readLine("What is the develop version for this ${project.name}:", suggestVersion)
			}else {
				developVersion = suggestVersion
			}
		}
		return developVersion
	}

	void ensureOrigin(String defaultRemote, JGitFlow flow) {
		String newOriginUrl = defaultRemote

		try {
			StoredConfig config = flow.git().getRepository().getConfig()

			String originUrl = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "url")

			if (Strings.isNullOrEmpty(originUrl) && !Strings.isNullOrEmpty(defaultRemote)) {
				if(defaultRemote.startsWith("file://")) {
					newOriginUrl = "file://" +  new File(defaultRemote.substring(7)).getCanonicalPath()
				}
				//adding origin from default...
				config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "url", newOriginUrl)
				config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "fetch", "+refs/heads/*:refs/remotes/origin/*")
			}

			if(!Strings.isNullOrEmpty(originUrl) || !Strings.isNullOrEmpty(newOriginUrl)) {
				if(Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION,flow.getMasterBranchName(),"remote"))) {
					config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getMasterBranchName(), "remote", Constants.DEFAULT_REMOTE_NAME)
				}

				if(Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getMasterBranchName(), "merge"))) {
					config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getMasterBranchName(), "merge", Constants.R_HEADS + flow.getMasterBranchName())
				}

				if(Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "remote"))) {
					config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "remote", Constants.DEFAULT_REMOTE_NAME)
				}

				if(Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "merge"))) {
					config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "merge", Constants.R_HEADS + flow.getDevelopBranchName())
				}

				config.save()

				try {
					config.load()
					flow.git().fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).call()
				}
				catch (Exception e) {
					e.printStackTrace();
					throw new GradleException("error configuring remote git repo with url: ${newOriginUrl}")
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new GradleException("error configuring remote git repo with url: ${defaultRemote}")
		}
	}


	void commitAllChanges(Git git, String message) {
		try {
			Status status = git.status().call()

			if (!status.isClean()) {
				git.add().addFilepattern(".").call()
				git.commit().setMessage(message).call()
			}
		}catch (GitAPIException e) {
			throw new GradleException("error committing changes: ${e.message}", e)
		}
	}

	void commitAllProperties(Git git, String message) {
		try {
			File repositoryDir = git.getRepository().getDirectory().getParentFile()

			Status status = git.status().call()
			if (!status.isClean()) {
				git.add().addFilepattern(relativePath(repositoryDir, new File(project.projectDir, Project.GRADLE_PROPERTIES))).call()
				git.commit().setMessage(message).call()
			}
		}catch (GitAPIException e) {
			throw new GradleException("error committing pom changes: ${e.message}", e)
		}
	}

	Set checkSnapshotDependencies() {
		def snapshotDependencies = [] as Set
		
		project.allprojects.each { project ->
			project.configurations.each { configuration ->
				configuration.allDependencies.each { Dependency dependency ->
					if (!dependency.group.equals(project.group) && ArtifactUtils.isSnapshot(dependency.version)) {
						snapshotDependencies.add("${dependency.group}:${dependency.name}:${dependency.version}")
					}
				}
			}
		}
		return snapshotDependencies
	}
	
	void setupReportHeader(JGitFlowReporter reporter) {
		
	}

	boolean setupConsoleCredentialsProvider(JGitFlowReporter reporter){
		if (null != System.console()) {
			CredentialsProvider.setDefault(new ConsoleCredentialsProvider(prompter));
			return true;
		}

		return false;
	}
	
	private String relativePath(File basedir, File file) {
		String filePath = file.getAbsolutePath()

		String basePath = basedir.getAbsolutePath()

		if (filePath.regionMatches(true, 0, basePath, 0, basePath.length())) {
			filePath = file.getAbsolutePath().substring(basedir.getAbsolutePath().length())

			if (filePath.startsWith(File.separator)) {
				filePath = filePath.substring(1)
			}
		}

		if(System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
			filePath = StringUtils.replace(filePath, "\\","/")
		}

		return filePath
	}
}
