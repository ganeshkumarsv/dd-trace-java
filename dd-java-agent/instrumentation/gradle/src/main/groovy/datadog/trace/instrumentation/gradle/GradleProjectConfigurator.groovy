package datadog.trace.instrumentation.gradle

import datadog.trace.api.Config
import org.gradle.api.Project

import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern

/**
 * Applies CI Visibility configuration to instrumented Gradle projects:
 * <ul>
 *  <li>configures Java compilation tasks to use DD Javac Plugin</li>
 *  <li>configures forked test processes to run with tracer attached</li>
 * </ul>
 *
 * <p>
 * This class is written in Groovy to circumvent compile-time safety checks,
 * since some Gradle classes that are Java-specific are not available in
 * the classloader that loads this instrumentation code
 * (the classes are available in a child CL, but injecting instrumentation code there
 * is troublesome, since there seems to be no convenient place to hook into).
 *
 * <p>
 * Another reason compile-time checks would introduce unnecessary complexity is
 * that depending on the Gradle version, different calls have to be made
 * to achieve the same result (in particular, when configuring dependencies).
 */
class GradleProjectConfigurator {

  /**
   * Each Groovy Closure in here is a separate class.
   * When adding or removing a closure, be sure to update {@link datadog.trace.instrumentation.gradle.GradleBuildListenerInstrumentation#helperClassNames()}
   */

  public static final GradleProjectConfigurator INSTANCE = new GradleProjectConfigurator()

  void configureTracer(Project project) {
    def closure = { task ->
      if (!GradleUtils.isTestTask(task)) {
        return
      }

      List<String> jvmArgs = new ArrayList<>(task.jvmArgs != null ? task.jvmArgs : Collections.<String> emptyList())

      // propagate to child process all "dd." system properties available in current process
      def systemProperties = System.getProperties()
      for (def e : systemProperties.entrySet()) {
        if (e.key.startsWith(Config.PREFIX)) {
          jvmArgs.add("-D" + e.key + '=' + e.value)
        }
      }

      def ciVisibilityDebugPort = Config.get().ciVisibilityDebugPort
      if (ciVisibilityDebugPort != null) {
        jvmArgs.add(
          "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address="
          + ciVisibilityDebugPort)
      }

      jvmArgs.add("-javaagent:" + Config.get().ciVisibilityAgentJarFile.toPath())

      task.jvmArgs(jvmArgs)
    }

    if (project.tasks.respondsTo("configureEach", Closure)) {
      project.tasks.configureEach closure
    } else {
      // for legacy Gradle versions
      project.tasks.all closure
    }
  }

  void configureCompilerPlugin(Project project, String compilerPluginVersion) {
    def moduleName = getModuleName(project)

    def closure = { task ->
      if (!task.class.name.contains('JavaCompile')) {
        return
      }

      if (!task.hasProperty('options') || !task.options.hasProperty('compilerArgs') || !task.hasProperty('classpath')) {
        // not a JavaCompile task?
        return
      }

      if (task.options.hasProperty('fork') && task.options.fork
        && task.options.hasProperty('forkOptions') && task.options.forkOptions.executable != null) {
        // a non-standard compiler is likely to be used
        return
      }

      def ddJavacPlugin = project.configurations.detachedConfiguration(project.dependencies.create("com.datadoghq:dd-javac-plugin:$compilerPluginVersion"))
      def ddJavacPluginClient = project.configurations.detachedConfiguration(project.dependencies.create("com.datadoghq:dd-javac-plugin-client:$compilerPluginVersion"))

      task.classpath = (task.classpath ?: project.files([])) + ddJavacPluginClient.asFileTree

      if (task.options.hasProperty('annotationProcessorPath')) {
        task.options.annotationProcessorPath = (task.options.annotationProcessorPath ?: project.files([])) + ddJavacPlugin
        task.options.compilerArgs += '-Xplugin:DatadogCompilerPlugin'
      } else {
        // for legacy Gradle versions
        task.options.compilerArgs += ['-processorpath', ddJavacPlugin.asPath, '-Xplugin:DatadogCompilerPlugin']
      }

      if (moduleName != null) {
        task.options.compilerArgs += ['--add-reads', "$moduleName=ALL-UNNAMED"]
      }

      // disable compiler warnings related to annotation processing,
      // since "fail-on-warning" linters might complain about the annotation that the compiler plugin injects
      task.options.compilerArgs += '-Xlint:-processing'
    }

    if (project.tasks.respondsTo("configureEach", Closure)) {
      project.tasks.configureEach closure
    } else {
      // for legacy Gradle versions
      project.tasks.all closure
    }
  }

  private static final Pattern MODULE_NAME_PATTERN = Pattern.compile("\\s*module\\s*((\\w|\\.)+)\\s*\\{")

  private getModuleName(Project project) {
    def dir = project.getProjectDir().toPath()
    def moduleInfo = dir.resolve(Paths.get("src", "main", "java", "module-info.java"))

    if (Files.exists(moduleInfo)) {
      def lines = Files.lines(moduleInfo)
      for (String line : lines) {
        def m = MODULE_NAME_PATTERN.matcher(line)
        if (m.matches()) {
          return m.group(1)
        }
      }
    }
    return null
  }
}
