package com.intellij.util.config;

import com.intellij.openapi.util.Comparing;

public class StringProperty extends ValueProperty<String> {
  public StringProperty(String name, String defaultValue) {
    super(name, defaultValue);
  }

  public boolean areEqual(String value1, String value2) {
    return Comparing.strEqual(value1, value2, true);
  }
}
