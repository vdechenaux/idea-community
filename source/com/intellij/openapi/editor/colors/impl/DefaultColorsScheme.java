/**
 * @author Yura Cangea
 */
package com.intellij.openapi.editor.colors.impl;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;

import java.awt.*;

public class DefaultColorsScheme extends AbstractColorsScheme {
  private String myName;

  public DefaultColorsScheme(DefaultColorSchemesManager defaultColorSchemesManager) {
    super(null, defaultColorSchemesManager);
  }

  public TextAttributes getAttributes(TextAttributesKey key) {
    if (key == null) return null;
    TextAttributes attrs = myAttributesMap.get(key);
    return attrs != null ? attrs : key.getDefaultAttributes();
  }

  public Color getColor(ColorKey key) {
    if (key == null) return null;
    Color color = myColorsMap.get(key);
    return color != null ? color : key.getDefaultColor();
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    super.readExternal(parentNode);
    myName = parentNode.getAttributeValue("name");
  }

  public String getName() {
    return myName;
  }

  public void setAttributes(TextAttributesKey key, TextAttributes attributes) {
  }

  public void setColor(ColorKey key, Color color) {
  }

  public void setFont(EditorFontType key, Font font) {
  }

  public Object clone() {
    EditorColorsScheme newScheme = new EditorColorsSchemeImpl(this, myDefaultColorSchemesManager);
    newScheme.setEditorFontSize(myEditorFontSize);
    newScheme.setLineSpacing(myLineSpacing);
    newScheme.setEditorFontName((String)myValuesMap.get(EDITOR_FONT_NAME));
    newScheme.setName("Default");
    return newScheme;
  }

}
