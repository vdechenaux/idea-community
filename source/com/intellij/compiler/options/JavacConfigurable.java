package com.intellij.compiler.options;

import com.intellij.compiler.JavacSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public class JavacConfigurable implements Configurable{
  private JPanel myPanel;
  private JCheckBox myCbDebuggingInfo;
  private JCheckBox myCbDeprecation;
  private JCheckBox myCbGenerateNoWarnings;
  private JCheckBox myUseGenericsCompilerCheckbox;
  private RawCommandLineEditor myAdditionalOptionsField;
  private JTextField myJavacMaximumHeapField;
  private JavacSettings myJavacSettings;

  public JavacConfigurable(final JavacSettings javacSettings) {
    myJavacSettings = javacSettings;
  }

  public String getDisplayName() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    boolean isModified = false;
    isModified |= ComparingUtils.isModified(myJavacMaximumHeapField, myJavacSettings.MAXIMUM_HEAP_SIZE);

    isModified |= ComparingUtils.isModified(myCbDeprecation, myJavacSettings.DEPRECATION);
    isModified |= ComparingUtils.isModified(myCbDebuggingInfo, myJavacSettings.DEBUGGING_INFO);
    isModified |= ComparingUtils.isModified(myCbGenerateNoWarnings, myJavacSettings.GENERATE_NO_WARNINGS);
    isModified |= ComparingUtils.isModified(myAdditionalOptionsField, myJavacSettings.ADDITIONAL_OPTIONS_STRING);
    isModified |= ComparingUtils.isModified(myUseGenericsCompilerCheckbox, myJavacSettings.USE_GENERICS_COMPILER);
    return isModified;
  }

  public void apply() throws ConfigurationException {

    try {
      myJavacSettings.MAXIMUM_HEAP_SIZE = Integer.parseInt(myJavacMaximumHeapField.getText());
      if(myJavacSettings.MAXIMUM_HEAP_SIZE < 1) {
        myJavacSettings.MAXIMUM_HEAP_SIZE = 128;
      }
    }
    catch(NumberFormatException exception) {
      myJavacSettings.MAXIMUM_HEAP_SIZE = 128;
    }

    myJavacSettings.DEPRECATION =  myCbDeprecation.isSelected();
    myJavacSettings.DEBUGGING_INFO = myCbDebuggingInfo.isSelected();
    myJavacSettings.GENERATE_NO_WARNINGS = myCbGenerateNoWarnings.isSelected();
    myJavacSettings.ADDITIONAL_OPTIONS_STRING = myAdditionalOptionsField.getText();
    myJavacSettings.USE_GENERICS_COMPILER = myUseGenericsCompilerCheckbox.isSelected();
  }

  public void reset() {
    myJavacMaximumHeapField.setText(""+myJavacSettings.MAXIMUM_HEAP_SIZE);
    myCbDeprecation.setSelected(myJavacSettings.DEPRECATION);
    myCbDebuggingInfo.setSelected(myJavacSettings.DEBUGGING_INFO);
    myCbGenerateNoWarnings.setSelected(myJavacSettings.GENERATE_NO_WARNINGS);
    myAdditionalOptionsField.setText(myJavacSettings.ADDITIONAL_OPTIONS_STRING);
    myUseGenericsCompilerCheckbox.setSelected(myJavacSettings.USE_GENERICS_COMPILER);
  }

  public void disposeUIResources() {
  }

}
