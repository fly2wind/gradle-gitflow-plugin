package cn.thinkjoy.gradle.plugins

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.atlassian.jgitflow.core.*
import com.atlassian.jgitflow.core.exception.*
import com.atlassian.jgitflow.core.util.GitHelper;
import com.google.common.base.Joiner;

import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.GradleException
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.testing.*
import org.gradle.mvn3.org.apache.maven.artifact.ArtifactUtils


class GitflowPlugin extends PluginHelper implements Plugin<Project> {

	private static final String PLUGIN_GROUP = "Git Flow"

	private boolean sshConsoleInstalled = false;

	private boolean sshSessionInstalled = false;


	void apply(Project project) {
		this.project = project

		GitflowPluginExtension extension = project.getExtensions().create("gitflow", GitflowPluginExtension.class, this)

		project.task('featureStart', description: 'Prepares the project for a new feature. Creates a feature branch and updates gradle with the feature version.', group: PLUGIN_GROUP) << {
		}

		project.task('featureFinish', description: 'Finishes the feature. Builds the project, Merges the feature branch (as per git-flow), and updates gradle to previous develop version.', group: PLUGIN_GROUP) << {
		}

		project.task('releaseStart', description: 'Prepares the project for a release. Creates a release branch and updates gradle with the release version.', group: PLUGIN_GROUP) << {
			JGitFlow flow = JGitFlow.getOrInit(project.rootProject.rootDir, project.gitflow.initContext, "")

			setupCredentialProviders(flow.getReporter());

			startRelease(flow)
		}

		project.task('releaseFinish', description: 'Releases the project. Builds the project, Merges the release branch (as per git-flow), optionally pushes changes and updates gradle to new development version.', group: PLUGIN_GROUP) << {
			JGitFlow flow = JGitFlow.getOrInit(project.rootProject.rootDir, project.gitflow.initContext, "")

			setupCredentialProviders(flow.getReporter());

			// ensure the git origin
			if(project.gitflow.pushReleases ||  !project.gitflow.noTag) {
				ensureOrigin("", flow)
			}

			finishRelease(flow)
		}

		project.task('hotfixStart', description: 'Prepares the project for a hotfix. Creates a hotfix branch and updates gradle with the hotfix version.', group: PLUGIN_GROUP) << {

		}

		project.task('hotfixFinish', description: 'Releases the project. Builds the project, Merges the hotfix branch (as per git-flow), optionally pushes changes and updates gradle to previous version.', group: PLUGIN_GROUP) << {

		}
	}

	void setupCredentialProviders(JGitFlowReporter reporter) {
		if(project.gitflow.allowRemote) {
			return
		}

		if (!sshConsoleInstalled) {
			sshConsoleInstalled = setupConsoleCredentialsProvider(reporter);
		}

	}


	private void startRelease(JGitFlow flow) {
		try {
			// make sure we're on develop
			flow.git().checkout().setName(flow.getDevelopBranchName()).call()

			// make sure the project has the snapshot version
			checkPropertiesForSnapshot()

			// checks to see if your project has any snapshot dependencies.
			if(!project.gitflow.allowSnapshots) {
				Set snapshots = checkSnapshotDependencies()
				if(!snapshots.isEmpty()) {
					throw new GradleException("Cannot start a release due to snapshot dependencies: ${snapshots}")
				}
			}

			// ensure the git origin
			if(project.gitflow.pushReleases ||  !project.gitflow.noTag) {
				ensureOrigin("", flow)
			}

			// get the release version
			String releaseLabel = getReleaseVersion()
			
			// creating a release branch
			flow.releaseStart(releaseLabel).setAllowUntracked(true).setPush(true).setStartCommit("").setScmMessagePrefix("[Gradle GitFlow Plugin]").call()
			
			// update the release version
			updateAllProperties(flow, releaseLabel)
			commitAllProperties(flow.git(), "[Gradle Gitflow Plugin] updating gradle.properties for " + releaseLabel + " release")

			if(project.gitflow.pushReleases) {
				flow.git().push().setRemote("origin").setRefSpecs(new RefSpec(flow.getReleaseBranchPrefix() + releaseLabel)).call();
			}
		}catch (Exception e) {
			throw new GradleException("Error starting release: ${e.message}");
		}
	}

	private void finishRelease(JGitFlow flow) {
		try {
			//do a pull if needed
			if(GitHelper.remoteBranchExists(flow.git(), flow.getDevelopBranchName(), flow.getReporter())) {
				if(project.gitflow.pullDevelop) {
					flow.git().checkout().setName(flow.getDevelopBranchName()).call()
					flow.git().pull().call()
				}
				if(GitHelper.localBranchBehindRemote(flow.git(),flow.getDevelopBranchName(),flow.getReporter())) {
					throw new BranchOutOfDateException("local branch '" + flow.getDevelopBranchName() + "' is behind the remote branch");
				}
			}

			//get the release branch
			List<Ref> releaseBranches = GitHelper.listBranchesWithPrefix(flow.git(), flow.getReleaseBranchPrefix());
			if (releaseBranches.isEmpty()) {
				throw new GradleException("Could not find release branch!");
			}

			//there can be only one
			String releasePrefix = Constants.R_HEADS + flow.getReleaseBranchPrefix();
			String releaseBranch = releaseBranches.get(0).getName()
			
			String releaseLabel = releaseBranch.substring(releaseBranch.indexOf(releasePrefix) + releasePrefix.length());

			String releaseBranchName = flow.getReleaseBranchPrefix() + releaseLabel;

			//make sure we're on the release branch
			flow.git().checkout().setName(releaseBranchName).call()

			//make sure we're not behind remote
			if(GitHelper.remoteBranchExists(flow.git(), releaseBranchName, flow.getReporter())) {
				if(GitHelper.localBranchBehindRemote(flow.git(),releaseBranchName, flow.getReporter())) {
					throw new BranchOutOfDateException("local branch '" + releaseBranchName + "' is behind the remote branch");
				}
			}
			if(GitHelper.remoteBranchExists(flow.git(), flow.getMasterBranchName(), flow.getReporter())) {
				if(project.gitflow.pullMaster) {
					flow.git().checkout().setName(flow.getMasterBranchName()).call();
					flow.git().pull().call();
					flow.git().checkout().setName(releaseBranchName).call();
				}
				if(GitHelper.localBranchBehindRemote(flow.git(),flow.getMasterBranchName(),flow.getReporter())) {
					throw new BranchOutOfDateException("local branch '" + flow.getMasterBranchName() + "' is behind the remote branch");
				}
			}

			//make sure the project has the release version
			checkPropertiesForRelease()

			// Checks to see if your project has any snapshot dependencies.
			if(!project.gitflow.allowSnapshots) {
				Set snapshots = checkSnapshotDependencies()
				if(!snapshots.isEmpty()) {
					throw new GradleException("Cannot start a release due to snapshot dependencies: {snapshots}");
				}
			}

			ReleaseMergeResult mergeResult = flow.releaseFinish(releaseLabel).setPush(true).setKeepBranch(false).setNoTag(project.gitflow.noTag).setSquash(false).setMessage("tagging release ${releaseLabel}").setAllowUntracked(project.gitflow.allowUntracked).setNoMerge(false).setScmMessagePrefix("").call();

			if(!mergeResult.wasSuccessful()) {
				if(mergeResult.masterHasProblems()) {
					project.logger.error("Error merging into " + flow.getMasterBranchName() + ":");
					project.logger.error(mergeResult.getMasterResult().toString());
					project.logger.error("see .git/jgitflow.log for more info");
				}

				if(mergeResult.developHasProblems()) {
					project.logger.error("Error merging into " + flow.getDevelopBranchName() + ":");
					project.logger.error(mergeResult.getDevelopResult().toString());
					project.logger.error("see .git/jgitflow.log for more info");
				}

				throw new GradleException("Error while merging release!");
			}

			//make sure we're on develop
			flow.git().checkout().setName(flow.getDevelopBranchName()).call();

			String developLabel = getDevelopVersion();
			
			// update the release version
			updateAllProperties(flow, developLabel);
			commitAllProperties(flow.git(), "[Gradle Gitflow Plugin] updating gradle.properties for ${developLabel} develop")

			if(project.gitflow.pushReleases) {
				flow.git().push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(new RefSpec(flow.getDevelopBranchName())).call();
			}


		}catch (Exception e) {
			throw new GradleException("Error starting release: ${e.message}");
		}
	}

	private void checkPropertiesForSnapshot() {
		boolean hasSnapshotProject = false
		for(subproject in project.allprojects){
			if(ArtifactUtils.isSnapshot(subproject.version)) {
				hasSnapshotProject = true
				break
			}
		}
		if (!hasSnapshotProject) {
			throw new GradleException("Unable to find snapshot version in the projects!");
		}
	}

	private void checkPropertiesForRelease() {
		boolean hasSnapshotProject = false
		for(subproject in project.allprojects){
			if(ArtifactUtils.isSnapshot(subproject.version)) {
				hasSnapshotProject = true
				break
			}
		}
		if (hasSnapshotProject) {
			throw new GradleException("Unable to find release version in the projects!");
		}
	}

	private void updateAllProperties(JGitFlow flow, String newVersion)
	{
		String oldVersion = project.version

		if (oldVersion != newVersion) {
			for(subproject in project.allprojects){
				subproject.version = newVersion
				
				File propertiesFile = subproject.file(Project.GRADLE_PROPERTIES)
				if (!propertiesFile.file) {
					propertiesFile.append("version=${subproject.version}")
				}else {
					subproject.ant.replace(file: propertiesFile, token: "version=${oldVersion}", value: "version=${newVersion}", failOnNoReplacements: true)
				}
			}
		}
	}
}