/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 7, 2002
 * Time: 8:27:04 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.testFramework.*;
import com.intellij.tests.ExternalClasspathClassLoader;
import com.intellij.util.ArrayUtil;
import junit.framework.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings({"HardCodedStringLiteral", "CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public class TestAll implements Test {
  static {
    Logger.setFactory(TestLoggerFactory.getInstance());
  }

  private final TestCaseLoader myTestCaseLoader;
  private long myStartTime = 0;
  private boolean myInterruptedByOutOfTime = false;
  private long myLastTestStartTime = 0;
  private String myLastTestClass;
  private int myRunTests = -1;
  private boolean mySavingMemorySnapshot;

  private static final int SAVE_MEMORY_SNAPSHOT = 1;
  private static final int START_GUARD = 2;
  private static final int RUN_GC = 4;
  private static final int CHECK_MEMORY = 8;
  private static final int FILTER_CLASSES = 16;

  public static int ourMode = SAVE_MEMORY_SNAPSHOT /*| START_GUARD | RUN_GC | CHECK_MEMORY*/ | FILTER_CLASSES;
  private int myLastTestTestMethodCount = 0;
  public static final int MAX_FAILURE_TEST_COUNT = 150;

  @Override
  public int countTestCases() {
    List<Class> classes = myTestCaseLoader.getClasses();

    int count = 0;

    for (final Object aClass : classes) {
      Class testCaseClass = (Class)aClass;
      Test test = getTest(testCaseClass);
      if (test != null) count += test.countTestCases();
    }

    return count;
  }

  private void beforeFirstTest() {
    if ((ourMode & START_GUARD) != 0) {
      Thread timeAndMemoryGuard = new Thread() {
        @Override
        public void run() {
          log("Starting Time and Memory Guard");
          while (true) {
            try {
              try {
                Thread.sleep(10000);
              }
              catch (InterruptedException e) {
                e.printStackTrace();
              }
              // check for time spent on current test
              if (myLastTestStartTime != 0) {
                long currTime = System.currentTimeMillis();
                long secondsSpent = (currTime - myLastTestStartTime) / 1000L;
                Thread currentThread = getCurrentThread();
                if (!mySavingMemorySnapshot) {
                  if (secondsSpent > PlatformTestCase.ourTestTime * myLastTestTestMethodCount) {
                    UsefulTestCase.printThreadDump();
                    log("Interrupting current Test (out of time)! Test class: "+ myLastTestClass +" Seconds spent = " + secondsSpent);
                    myInterruptedByOutOfTime = true;
                    if (currentThread != null) {
                      currentThread.interrupt();
                      if (!currentThread.isInterrupted()) {
                        //noinspection deprecation
                        currentThread.stop(new RuntimeException("Current Test Interrupted: OUT OF TIME!"));
                      }

                      break;
                    }
                  }
                }
              }
            }
            catch (Exception e) {
              e.printStackTrace();
            }
          }
          log("Time and Memory Guard finished.");
        }
      };
      timeAndMemoryGuard.setDaemon(true);
      timeAndMemoryGuard.start();
    }
    myStartTime = System.currentTimeMillis();
  }

  private static Thread getCurrentThread() {
    if (PlatformTestCase.ourTestThread != null) {
      return PlatformTestCase.ourTestThread;
    }
    else return LightPlatformTestCase.ourTestThread;
  }

  private void addErrorMessage(TestResult testResult, String message) {
    String processedTestsMessage = myRunTests <= 0 ? "None of tests was run" : myRunTests + " tests processed";
    try {
      testResult.startTest(this);
      testResult.addError(this, new Throwable(processedTestsMessage + " before: " + message));
      testResult.endTest(this);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void run(final TestResult testResult) {
    List<Class> classes = myTestCaseLoader.getClasses();
    int totalTests = classes.size();
    for (final Class aClass : classes) {
      runNextTest(testResult, totalTests, aClass);
      if (testResult.shouldStop()) break;
    }
    tryGc(10);
  }

  private void runNextTest(final TestResult testResult, int totalTests, Class testCaseClass) {
    myRunTests++;
    if (!checkAvaliableMemory(35, testResult)) {
      testResult.stop();
      return;
    }
    if (testResult.errorCount() + testResult.failureCount() > MAX_FAILURE_TEST_COUNT) {
      addErrorMessage(testResult, "Too many errors. Tests stopped. Total " + myRunTests + " of " + totalTests + " tests run");
      testResult.stop();
      return;
    }
    if (myStartTime == 0) {
      boolean ourClassLoader = getClass().getClassLoader().getClass().getName().startsWith("com.intellij.");
      if (!ourClassLoader) {
        beforeFirstTest();
      }
    }
    else {
      if (myInterruptedByOutOfTime) {
        addErrorMessage(testResult,
                        "Current Test Interrupted: OUT OF TIME! Class = " + myLastTestClass + " Total " + myRunTests + " of " +
                        totalTests +
                        " tests run");
        testResult.stop();
        return;
      }
    }

    log("\nRunning " + testCaseClass.getName());
    final Test test = getTest(testCaseClass);

    if (test == null) return;

    myLastTestClass = null;

    myLastTestClass = testCaseClass.getName();
    myLastTestStartTime = System.currentTimeMillis();
    myLastTestTestMethodCount = test.countTestCases();

    try {
      test.run(testResult);
    }
    catch (Throwable t) {
      if (t instanceof OutOfMemoryError) {
        if ((ourMode & SAVE_MEMORY_SNAPSHOT) != 0) {
          try {
            mySavingMemorySnapshot = true;
            log("OutOfMemoryError detected. Saving memory snapshot started");
          }
          finally {
            log("Saving memory snapshot finished");
            mySavingMemorySnapshot = false;
          }
        }
      }
      testResult.addError(test, t);
    }
  }

  private boolean checkAvaliableMemory(int neededMemory, TestResult testResult) {
    if ((ourMode & CHECK_MEMORY) == 0) return true;

    boolean possibleOutOfMemoryError = possibleOutOfMemory(neededMemory);
    if (possibleOutOfMemoryError) {
      tryGc(5);
      possibleOutOfMemoryError = possibleOutOfMemory(neededMemory);
      if (possibleOutOfMemoryError) {
        log("OutOfMemoryError: dumping memory");
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        String errorMessage = "Too much memory used. Total: " + total + " free: " + free + " used: " + (total - free) + "\n";
        addErrorMessage(testResult, errorMessage);
      }
    }
    return !possibleOutOfMemoryError;
  }

  private static boolean possibleOutOfMemory(int neededMemory) {
    Runtime runtime = Runtime.getRuntime();
    long maxMemory = runtime.maxMemory();
    long realFreeMemory = runtime.freeMemory() + (maxMemory - runtime.totalMemory());
    long meg = 1024 * 1024;
    long needed = neededMemory * meg;
    return realFreeMemory < needed;
  }

  private static Test getTest(Class testCaseClass) {
    if ((testCaseClass.getModifiers() & Modifier.PUBLIC) == 0) return null;

    try {
      Method suiteMethod = testCaseClass.getMethod("suite", ArrayUtil.EMPTY_CLASS_ARRAY);
      return (Test)suiteMethod.invoke(null, ArrayUtil.EMPTY_CLASS_ARRAY);
    }
    catch (NoSuchMethodException e) {
      if (TestRunnerUtil.isJUnit4TestClass(testCaseClass)) {
        return new JUnit4TestAdapter(testCaseClass);
      }
      return new TestSuite(testCaseClass){
        @Override
        public void addTest(Test test) {
          if (!(test instanceof TestCase))  {
            super.addTest(test);
          } else {
            Method method = findTestMethod((TestCase)test);
            if (method == null || !TestCaseLoader.isBombed(method)) {
              super.addTest(test);
            }
          }

        }

        private Method findTestMethod(final TestCase testCase) {
          try {
            return testCase.getClass().getMethod(testCase.getName());
          }
          catch (NoSuchMethodException e1) {
            return null;
          }
        }
      };
    }
    catch (Exception e) {
      System.err.println("Failed to execute suite ()");
      e.printStackTrace();
    }

    return null;
  }

  private static String[] getClassRoots() {
    String testRoots = System.getProperty("test.roots");
    if (testRoots != null) {
      System.out.println("Collecting tests from roots specified by test.roots property: " + testRoots);
      return testRoots.split(";");
    }
    final String[] roots = ExternalClasspathClassLoader.getRoots();
    if (roots != null) {
      System.out.println("Collecting tests from roots specified by classpath.file property: " + Arrays.toString(roots));
      return roots;
    }
    else {
      final ClassLoader loader = TestAll.class.getClassLoader();
      if (loader instanceof URLClassLoader) {
        final URL[] urls = ((URLClassLoader)loader).getURLs();
        final String[] classLoaderRoots = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
          classLoaderRoots[i] = VfsUtil.urlToPath(VfsUtil.convertFromUrl(urls[i]));
        }
        System.out.println("Collecting tests from classloader: " + Arrays.toString(classLoaderRoots));
        return classLoaderRoots;
      }
      return System.getProperty("java.class.path").split(File.pathSeparator);
    }
  }

  public TestAll(String packageRoot) throws Throwable {
    this(packageRoot, getClassRoots());
  }

  public TestAll(String packageRoot, String... classRoots) throws IOException, ClassNotFoundException {
    myTestCaseLoader = new TestCaseLoader((ourMode & FILTER_CLASSES) != 0 ? "tests/testGroups.properties" : "");
    myTestCaseLoader.addFirstTest(Class.forName("_FirstInSuiteTest"));
    myTestCaseLoader.addLastTest(Class.forName("_LastInSuiteTest"));

    for (String classRoot : classRoots) {
      int oldCount = myTestCaseLoader.getClasses().size();
      ClassFinder classFinder = new ClassFinder(new File(FileUtil.toSystemDependentName(classRoot)), packageRoot);
      myTestCaseLoader.loadTestCases(classFinder.getClasses());
      int newCount = myTestCaseLoader.getClasses().size();
      if (newCount != oldCount) {
        System.out.println("Loaded " + (newCount - oldCount) + " tests from class root " + classRoot);
      }
    }

    if (myTestCaseLoader.getClasses().size() == 1) {
      myTestCaseLoader.clearClasses();
    }

    log("Number of test classes found: " + myTestCaseLoader.getClasses().size());
    myTestCaseLoader.checkClassesExist();
  }

  private static void log(String message) {
    TeamCityLogger.info(message);
  }

  // [myakovlev] Do not delete - it is for debugging
  public static void tryGc(int times) {
    if ((ourMode & RUN_GC) == 0) return;

    for (int qqq = 1; qqq < times; qqq++) {
      try {
        Thread.sleep(qqq * 1000);
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
      System.gc();
      //long mem = Runtime.getRuntime().totalMemory();
      log("Runtime.getRuntime().totalMemory() = " + Runtime.getRuntime().totalMemory());
    }
  }

}
