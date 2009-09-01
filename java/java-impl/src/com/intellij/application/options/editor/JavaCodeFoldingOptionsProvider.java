/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.application.options.editor;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.BeanConfigurable;

public class JavaCodeFoldingOptionsProvider extends BeanConfigurable<JavaCodeFoldingSettings> implements CodeFoldingOptionsProvider {
  protected JavaCodeFoldingOptionsProvider() {
    super(JavaCodeFoldingSettings.getInstance());
    checkBox("COLLAPSE_ACCESSORS", ApplicationBundle.message("checkbox.collapse.simple.property.accessors"));
    checkBox("COLLAPSE_INNER_CLASSES", ApplicationBundle.message("checkbox.collapse.inner.classes"));
    checkBox("COLLAPSE_ANONYMOUS_CLASSES", ApplicationBundle.message("checkbox.collapse.anonymous.classes"));
    checkBox("COLLAPSE_ANNOTATIONS", ApplicationBundle.message("checkbox.collapse.annotations"));
    checkBox("COLLAPSE_CLOSURES", ApplicationBundle.message("checkbox.collapse.closures"));
    checkBox("COLLAPSE_CONSTRUCTOR_GENERIC_PARAMETERS", ApplicationBundle.message("checkbox.collapse.generic.constructor.parameters"));
    checkBox("COLLAPSE_I18N_MESSAGES", ApplicationBundle.message("checkbox.collapse.i18n.messages"));
  }
}