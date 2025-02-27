package datadog.trace.api.civisibility.events;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

public interface TestEventsHandler {

  void onTestModuleStart();

  void onTestModuleFinish();

  void onTestSuiteStart(
      String testSuiteName,
      @Nullable String testFramework,
      @Nullable String testFrameworkVersion,
      @Nullable Class<?> testClass,
      @Nullable Collection<String> categories,
      boolean parallelized);

  void onTestSuiteFinish(String testSuiteName, @Nullable Class<?> testClass);

  void onTestSuiteSkip(String testSuiteName, Class<?> testClass, @Nullable String reason);

  void onTestSuiteFailure(String testSuiteName, Class<?> testClass, @Nullable Throwable throwable);

  void onTestStart(
      String testSuiteName,
      String testName,
      @Nullable String testFramework,
      @Nullable String testFrameworkVersion,
      @Nullable String testParameters,
      @Nullable Collection<String> categories,
      @Nullable Class<?> testClass,
      @Nullable Method testMethod);

  void onTestSkip(
      String testSuiteName,
      Class<?> testClass,
      String testName,
      @Nullable String testParameters,
      @Nullable String reason);

  void onTestFailure(
      String testSuiteName,
      Class<?> testClass,
      String testName,
      @Nullable String testParameters,
      @Nullable Throwable throwable);

  void onTestFinish(
      String testSuiteName, Class<?> testClass, String testName, @Nullable String testParameters);

  void onTestIgnore(
      String testSuiteName,
      String testName,
      @Nullable String testFramework,
      @Nullable String testFrameworkVersion,
      @Nullable String testParameters,
      @Nullable List<String> categories,
      @Nullable Class<?> testClass,
      @Nullable Method testMethod,
      @Nullable String reason);

  interface Factory {
    TestEventsHandler create(
        String component, String testFramework, String testFrameworkVersion, Path path);
  }
}
