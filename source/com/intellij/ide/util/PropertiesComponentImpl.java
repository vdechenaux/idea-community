/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.util;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;

import java.util.Iterator;

public class PropertiesComponentImpl extends PropertiesComponent implements JDOMExternalizable, ProjectComponent, ApplicationComponent {
  private HashMap<String, String> myMap = new HashMap<String, String>();


  public String getComponentName() {
    return "PropertiesComponent";
  }

  PropertiesComponentImpl() {}

  public void disposeComponent() {}
  public void initComponent() {}
  public void projectClosed() {}
  public void projectOpened() {}
  public void moduleAdded() {}

  public void readExternal(Element parentNode) throws InvalidDataException {
    for (Iterator iterator = parentNode.getChildren("property").iterator(); iterator.hasNext();) {
      Element e = (Element)iterator.next();

      String name = e.getAttributeValue("name");
      String value = e.getAttributeValue("value");

      if (name != null) {
        myMap.put(name, value);
      }
    }
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    for(Iterator iterator = myMap.keySet().iterator(); iterator.hasNext();){
      String key = (String)iterator.next();
      String value = myMap.get(key);
      if (value != null) {
        Element element = new Element("property");
        element.setAttribute("name", key);
        element.setAttribute("value", value);
        parentNode.addContent(element);
      }
    }
  }

  public String getValue(String name) {
    return myMap.get(name);
  }

  public void setValue(String name, String value) {
    myMap.put(name, value);
  }

  public boolean isTrueValue(String name) {
    return Boolean.valueOf(getValue(name)).booleanValue();
  }

}
