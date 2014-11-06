package cn.thinkjoy.gradle.plugins

import com.atlassian.jgitflow.core.InitContext

class GitflowPluginExtension {

	private final GitflowPlugin plugin;

	InitContext initContext = new InitContext()

	boolean updateDependencies = true

	boolean pushFeatures = false
	boolean pushReleases = true
	boolean pushHotfixes = false

	boolean pullMaster = false
	boolean pullDevelop = false

	boolean noTag = false
	boolean noDeploy = true
	boolean noBuild = false

	boolean allowSnapshots = false
	boolean allowUntracked = true
	boolean allowRemote = false

	boolean interactive = true

	public GitflowPluginExtension(GitflowPlugin plugin) {
		this.plugin = plugin
	}
}