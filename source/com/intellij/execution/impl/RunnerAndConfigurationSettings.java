package com.intellij.execution.impl;

import com.intellij.execution.ExecutionRegistry;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author dyoma
 */
public class RunnerAndConfigurationSettings implements JDOMExternalizable, Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.RunnerAndConfigurationSettings");

  private static final String RUNNER_ELEMENT = "RunnerSettings";
  private static final String CONFIGURATION_ELEMENT = "ConfigurationWrapper";
  private static final String RUNNER_ID = "RunnerId";
  private static final String CONFIGURATION_TYPE_ATTRIBUTE = "type";
  private static final String FACTORY_NAME_ATTRIBUTE = "factoryName";
  private static final String TEMPLATE_FLAG_ATTRIBUTE = "default";

  private final RunManagerImpl myManager;
  private RunConfiguration myConfiguration;
  private boolean myIsTemplate;

  private Map<JavaProgramRunner, RunnerSettings> myRunnerSettings = new HashMap<JavaProgramRunner, RunnerSettings>();
  private Map<JavaProgramRunner, ConfigurationPerRunnerSettings> myConfigurationPerRunnerSettings = new HashMap<JavaProgramRunner, ConfigurationPerRunnerSettings>();

  public RunnerAndConfigurationSettings(RunManagerImpl manager) {
    myManager = manager;
  }

  public RunnerAndConfigurationSettings(RunManagerImpl manager, RunConfiguration configuration, boolean isTemplate) {
    myManager = manager;
    myConfiguration = configuration;
    myIsTemplate = isTemplate;
  }

  public ConfigurationFactory getFactory() {
    return myConfiguration == null ? null : myConfiguration.getFactory();
  }

  public boolean isTemplate() {
    return myIsTemplate;
  }

  public RunConfiguration getConfiguration() {
    return myConfiguration;
  }

  public Factory<RunnerAndConfigurationSettings> createFactory() {
    return new Factory<RunnerAndConfigurationSettings>() {
      public RunnerAndConfigurationSettings create() {
        RunConfiguration configuration = myConfiguration.getFactory().createConfiguration("<unnamed>", myConfiguration);
        RunnerAndConfigurationSettings copy = new RunnerAndConfigurationSettings(myManager, configuration, false);
        return copy;
      }
    };
  }

  public void setName(String name) {
    myConfiguration.setName(name);
  }

  public String getName() {
    return myConfiguration.getName();
  }

  private ConfigurationFactory getFactory(final Element element) {
    final String typeName = element.getAttributeValue(CONFIGURATION_TYPE_ATTRIBUTE);
    String factoryName = element.getAttributeValue(FACTORY_NAME_ATTRIBUTE);
    return myManager.getFactory(typeName, factoryName);
  }

  public void readExternal(Element element) throws InvalidDataException {
    myIsTemplate = "true".equals(element.getAttributeValue(TEMPLATE_FLAG_ATTRIBUTE));
    final ConfigurationFactory factory = getFactory(element);
    if (factory == null) return;

    if (myIsTemplate) {
      myConfiguration = myManager.getConfigurationTemplate(factory).getConfiguration();
    }
    else {
      final String name = element.getAttributeValue("name");
      myConfiguration = myManager.createConfiguration(name, factory).getConfiguration();
    }

    myConfiguration.readExternal(element);
    List runners = element.getChildren(RUNNER_ELEMENT);
    for (Iterator iterator = runners.iterator(); iterator.hasNext();) {
      Element runnerElement = (Element)iterator.next();
      String id = runnerElement.getAttributeValue(RUNNER_ID);
      JavaProgramRunner runner = ExecutionRegistry.getInstance().findRunnerById(id);
      if (runner != null) {
        RunnerSettings settings = new RunnerSettings(runner.createConfigurationData(new InfoProvider(runner)), myConfiguration);
        settings.readExternal(runnerElement);
        myRunnerSettings.put(runner, settings);
      }
    }

    List configurations = element.getChildren(CONFIGURATION_ELEMENT);
    for (Iterator iterator = configurations.iterator(); iterator.hasNext();) {
      Element configurationElement = (Element)iterator.next();
      String id = configurationElement.getAttributeValue(RUNNER_ID);
      JavaProgramRunner runner = ExecutionRegistry.getInstance().findRunnerById(id);
      if (runner != null) {
        ConfigurationPerRunnerSettings settings = new ConfigurationPerRunnerSettings(runner.getInfo(), myConfiguration.createRunnerSettings(new InfoProvider(runner)));
        settings.readExternal(configurationElement);
        myConfigurationPerRunnerSettings.put(runner, settings);
      }
    }
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    final ConfigurationFactory factory = myConfiguration.getFactory();

    element.setAttribute(TEMPLATE_FLAG_ATTRIBUTE, String.valueOf(myIsTemplate));
    if (!myIsTemplate) {
      element.setAttribute("name", myConfiguration.getName());
    }
    element.setAttribute(CONFIGURATION_TYPE_ATTRIBUTE, factory.getType().getComponentName());
    element.setAttribute(FACTORY_NAME_ATTRIBUTE, factory.getName());
    myConfiguration.writeExternal(element);

    for (Iterator<JavaProgramRunner> iterator = myRunnerSettings.keySet().iterator(); iterator.hasNext();) {
      JavaProgramRunner runner = iterator.next();
      RunnerSettings settings = myRunnerSettings.get(runner);
      Element runnerElement = new Element(RUNNER_ELEMENT);
      settings.writeExternal(runnerElement);
      element.addContent(runnerElement);
      runnerElement.setAttribute(RUNNER_ID, runner.getInfo().getId());
    }

    for (Iterator<JavaProgramRunner> iterator = myConfigurationPerRunnerSettings.keySet().iterator(); iterator.hasNext();) {
      JavaProgramRunner runner = iterator.next();
      ConfigurationPerRunnerSettings settings = myConfigurationPerRunnerSettings.get(runner);
      Element runnerElement = new Element(CONFIGURATION_ELEMENT);
      settings.writeExternal(runnerElement);
      element.addContent(runnerElement);
      runnerElement.setAttribute(RUNNER_ID, runner.getInfo().getId());
    }
  }

  public RunnerSettings getRunnerSettings(JavaProgramRunner runner) {
    RunnerSettings settings = myRunnerSettings.get(runner);
    if (settings == null) {
      settings = new RunnerSettings(runner.createConfigurationData(new InfoProvider(runner)), myConfiguration);
      myRunnerSettings.put(runner, settings);
    }
    return settings;
  }
  public ConfigurationPerRunnerSettings getConfigurationSettingsWr(JavaProgramRunner runner) {
    return getConfigurationSettings(runner);
  }

  public ConfigurationPerRunnerSettings getConfigurationSettings(JavaProgramRunner runner) {
    ConfigurationPerRunnerSettings settings = myConfigurationPerRunnerSettings.get(runner);
    if (settings == null) {
      settings = new ConfigurationPerRunnerSettings(runner.getInfo(), myConfiguration.createRunnerSettings(new InfoProvider(runner)));
      myConfigurationPerRunnerSettings.put(runner, settings);
    }
    return settings;
  }

  public ConfigurationType getType() {
    return myConfiguration == null ? null : myConfiguration.getType();
  }

  public RunnerAndConfigurationSettings clone() {
    RunnerAndConfigurationSettings copy = new RunnerAndConfigurationSettings(myManager, myConfiguration.clone(), false);
    copy.importRunnerAndConfigurationSettings(this);
    return copy;
  }

  public void importRunnerAndConfigurationSettings(RunnerAndConfigurationSettings template) {
    try {
      for (Iterator<JavaProgramRunner> iterator = template.myRunnerSettings.keySet().iterator(); iterator.hasNext();) {
        JavaProgramRunner runner = iterator.next();
        RunnerSettings data = new RunnerSettings(runner.createConfigurationData(new InfoProvider(runner)), myConfiguration);
        myRunnerSettings.put(runner, data);
        Element temp = new Element("dummy");
        template.myRunnerSettings.get(runner).writeExternal(temp);
        data.readExternal(temp);
      }

      for (Iterator<JavaProgramRunner> iterator = template.myConfigurationPerRunnerSettings.keySet().iterator(); iterator.hasNext();) {
        JavaProgramRunner runner = iterator.next();
        ConfigurationPerRunnerSettings data = new ConfigurationPerRunnerSettings(runner.getInfo(), myConfiguration.createRunnerSettings(new InfoProvider(runner)));
        myConfigurationPerRunnerSettings.put(runner, data);
        Element temp = new Element("dummy");
        template.myConfigurationPerRunnerSettings.get(runner).writeExternal(temp);
        data.readExternal(temp);
      }
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private class InfoProvider implements ConfigurationInfoProvider {
    private JavaProgramRunner myRunner;

    public InfoProvider(JavaProgramRunner runner) {
      myRunner = runner;
    }

    public JavaProgramRunner getRunner() {
      return myRunner;
    }

    public RunConfiguration getConfiguration() {
      return myConfiguration;
    }

    public RunnerSettings getRunnerSettings() {
      return RunnerAndConfigurationSettings.this.getRunnerSettings(myRunner);
    }

    public ConfigurationPerRunnerSettings getConfigurationSettings() {
      return getConfigurationSettingsWr(myRunner);
    }
  }
}
