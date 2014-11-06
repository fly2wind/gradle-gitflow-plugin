# Gradle Gitflow Plugin

This plugin for Gradle allows you use the git flow to release your project to repository. To use it, simply include the required JARs via `buildscript {}` and 'apply' the plugin:

```groovy
buildscript {
  repositories {
    //your repo
  }
  dependencies {
    classpath 'cn.thinkjoy.gradle.plugins:gradle-gitflow-plugin:1.0.1'
  }
}

version = '1.0.0'
group "example"

apply plugin: "gitflow"

```
You must specify the `version` property before executing any gitflow commands.

only implements follow fetures:
* gradle releaseStart   
* gradle releaseFinish 

This plugin is migrate from the gitflow-maven-plugin. 

