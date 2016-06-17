/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.junit.gen5.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.junit.gen5.console.ConsoleRunner

/**
 * @since 5.0
 */
class JUnitPlatformPlugin implements Plugin<Project> {

	private static final String EXTENSION_NAME = 'junitPlatform';
	private static final String TASK_NAME      = 'junitPlatformTest';

	void apply(Project project) {
		def junitExtension = project.extensions.create(EXTENSION_NAME, JUnitPlatformExtension)
		junitExtension.extensions.create('tags', TagsExtension)
		junitExtension.extensions.create('engines', EnginesExtension)

		// Add required JUnit Platform dependencies to a custom configuration that
		// will later be used to create the classpath for the custom task created
		// by this plugin.
		def configuration = project.configurations.maybeCreate('junitPlatform')
		configuration.defaultDependencies { deps ->
			def version = junitExtension.platformVersion
			deps.add(project.dependencies.create("org.junit:junit-launcher:${version}"))
			deps.add(project.dependencies.create("org.junit:junit-console:${version}", {
				exclude(group: 'org.junit', module: 'junit4-engine')
				exclude(group: 'org.junit', module: 'junit5-engine')
				exclude(group: 'org.junit', module: 'junit4-runner')
			}))
		}

		project.afterEvaluate {
			configure(project, junitExtension)
		}
	}

	private void configure(Project project, junitExtension) {
		project.task(
				TASK_NAME,
				type: JavaExec,
				group: 'verification',
				description: 'Runs tests on the JUnit Platform.') { junitTask ->

			junitTask.inputs.property('enableStandardTestTask', junitExtension.enableStandardTestTask)
			junitTask.inputs.property('includedEngines', junitExtension.engines.include)
			junitTask.inputs.property('excludedEngines', junitExtension.engines.exclude)
			junitTask.inputs.property('includedTags', junitExtension.tags.include)
			junitTask.inputs.property('excludedTags', junitExtension.tags.exclude)
			junitTask.inputs.property('classNameFilter', junitExtension.classNameFilter)

			def reportsDir = junitExtension.reportsDir ?: project.file("build/test-results/junit-platform")
			junitTask.outputs.dir reportsDir

			if (junitExtension.logManager) {
				systemProperty 'java.util.logging.manager', junitExtension.logManager
			}

			configureTaskDependencies(project, junitTask, junitExtension)

			// Build the classpath from the user's test runtime classpath and the JUnit
			// Platform modules.
			//
			// Note: the user's test runtime classpath must come first; otherwise, code
			// instrumented by Clover in JUnit's build will be shadowed by JARs pulled in
			// via the junitPlatform configuration... leading to zero code coverage for
			// the respective modules.
			junitTask.classpath = project.sourceSets.test.runtimeClasspath + project.configurations.junitPlatform

			junitTask.main = ConsoleRunner.class.getName()
			junitTask.args buildArgs(project, junitExtension, reportsDir)
		}
	}

	private void configureTaskDependencies(project, junitTask, junitExtension) {
		def testClassesTask = project.tasks.getByName('testClasses')
		junitTask.dependsOn testClassesTask

		def testTask = project.tasks.getByName('test')
		testTask.dependsOn junitTask
		testTask.enabled = junitExtension.enableStandardTestTask
	}

	private ArrayList<String> buildArgs(project, junitExtension, reportsDir) {

		def args = ['--enable-exit-code', '--hide-details', '--all']

		if (junitExtension.classNameFilter) {
			args.add('-n')
			args.add(junitExtension.classNameFilter)
		}

		junitExtension.tags.include.each { tag ->
			args.add('-t')
			args.add(tag)
		}

		junitExtension.tags.exclude.each { tag ->
			args.add('-T')
			args.add(tag)
		}

		junitExtension.engines.include.each { engineId ->
			args.add('-e')
			args.add(engineId)
		}

		junitExtension.engines.exclude.each { engineId ->
			args.add('-E')
			args.add(engineId)
		}

		args.add('-r')
		args.add(reportsDir.getAbsolutePath())

		def rootDirs = []
		project.sourceSets.each { sourceSet ->
			rootDirs.add(sourceSet.output.classesDir)
			rootDirs.add(sourceSet.output.resourcesDir)
			rootDirs.addAll(sourceSet.output.dirs.files)
		}

		rootDirs.each { File root ->
			args.add(root.getAbsolutePath())
		}

		return args
	}

}
