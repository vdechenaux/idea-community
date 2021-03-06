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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrConstructorImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class GroovyPsiElementFactoryImpl extends GroovyPsiElementFactory {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementFactoryImpl");

  Project myProject;

  public GroovyPsiElementFactoryImpl(Project project) {
    myProject = project;
  }

  private static final String DUMMY = "dummy.";

  @NotNull
  public PsiElement createReferenceNameFromText(String refName) {
    PsiFile file = createGroovyFile("a." + refName);
    GrTopStatement statement = ((GroovyFileBase) file).getTopStatements()[0];
    if (!(statement instanceof GrReferenceExpression)) return null;
    final PsiElement element = ((GrReferenceExpression)statement).getReferenceNameElement();
    if (element == null) {
      throw new IncorrectOperationException("Incorrect reference name: " + refName + ".");
    }
    return element;
  }

  public PsiElement createDocMemberReferenceNameFromText(String idText) {
    GrDocMemberReference reference = createDocMemberReferenceFromText("Foo", idText);
    assert reference != null : "DocMemberReference ponts to null";
    return reference.getReferenceNameElement();
  }

  public GrDocMemberReference createDocMemberReferenceFromText(String className, String text) {
    PsiFile file = createGroovyFile("/** @see " + className + "#" + text + " */");
    PsiElement element = file.getFirstChild();
    assert element instanceof GrDocComment;
    GrDocTag tag = PsiTreeUtil.getChildOfType(element, GrDocTag.class);
    assert tag != null : "Doc tag points to null";
    return PsiTreeUtil.getChildOfType(tag, GrDocMemberReference.class);
  }

  public GrDocReferenceElement createDocReferenceElementFromFQN(String qName) {
    PsiFile file = createGroovyFile("/** @see " + qName + " */");
    PsiElement element = file.getFirstChild();
    assert element instanceof GrDocComment;
    GrDocTag tag = PsiTreeUtil.getChildOfType(element, GrDocTag.class);
    assert tag != null : "Doc tag points to null";
    return PsiTreeUtil.getChildOfType(tag, GrDocReferenceElement.class);
  }

  public GrCodeReferenceElement createReferenceElementFromText(String refName, final PsiElement context) {
    PsiFile file = createGroovyFile("(" + refName + " " + ")foo", false, context);
    GrTypeElement typeElement = ((GrTypeCastExpression) ((GroovyFileBase) file).getTopStatements()[0]).getCastTypeElement();
    return ((GrClassTypeElement) typeElement).getReferenceElement();
  }

  public GrReferenceExpression createReferenceExpressionFromText(String idText) {
    PsiFile file = createGroovyFile(idText);
    return (GrReferenceExpression) ((GroovyFileBase) file).getTopStatements()[0];
  }

  public GrReferenceExpression createReferenceExpressionFromText(String idText, PsiElement context) {
    PsiFile file = createGroovyFile(idText, false, context);
    return (GrReferenceExpression) ((GroovyFileBase) file).getTopStatements()[0];
  }


  public GrExpression createExpressionFromText(String text) {
    GroovyFileImpl file = (GroovyFileImpl) createGroovyFile(text);
    assert file.getTopStatements()[0] instanceof GrExpression;
    return (GrExpression) ((GroovyFileBase) file).getTopStatements()[0];
  }

  public GrVariableDeclaration createVariableDeclaration(@Nullable String[] modifiers,
                                                         @Nullable GrExpression initializer,
                                                         @Nullable PsiType type,
                                                         String... identifiers) {
    StringBuffer text = writeModifiers(modifiers);
    if (modifiers == null || modifiers.length == 0) {
      text.append("def ");
    }
    if (type != null) {
      type = TypesUtil.unboxPrimitiveTypeWrapper(type);
      final String typeText = getTypeText(type);
      text.append(typeText).append(" ");
    }

    for (int i = 0; i < identifiers.length; i++) {
      if (i > 0) text.append(", ");
      String identifier = identifiers[i];
      text.append(identifier);
    }
    GrExpression expr;

    if (initializer != null) {
      if (initializer instanceof GrApplicationStatement) {
        expr = createMethodCallByAppCall(((GrApplicationStatement)initializer));
      }
      else {
        expr = initializer;
      }
      text.append(" = ").append(expr.getText());
    }

    PsiFile file = createGroovyFile(text.toString());
    GrTopStatement[] topStatements = ((GroovyFileBase)file).getTopStatements();
    if (topStatements.length == 0 || !(topStatements[0] instanceof GrVariableDeclaration)) {
      throw new RuntimeException("Invalid arguments, text = " + text.toString());
    }

    final GrVariableDeclaration declaration = (GrVariableDeclaration)topStatements[0];
    if (declaration.getTypeElementGroovy() != null) {
      declaration.getModifierList().setModifierProperty(GrModifier.DEF, false);
    }
    return declaration;
  }

  @Override
  public GrEnumConstant createEnumConstantFromText(String text) {
    GroovyFile file = (GroovyFile)createGroovyFile("enum E{" + text + "}");
    final GrEnumTypeDefinition enumClass = (GrEnumTypeDefinition)file.getClasses()[0];
    return enumClass.getEnumConstants()[0];
  }

  public GrVariableDeclaration createFieldDeclaration(String[] modifiers, String identifier, GrExpression initializer, PsiType type) {
    final String varDeclaration = createVariableDeclaration(modifiers, initializer, type, identifier).getText();

    final GroovyFileBase file = (GroovyFileBase) createGroovyFile("class A { " + varDeclaration + "}");
    final GrTypeDefinitionBody body = file.getTypeDefinitions()[0].getBody();
    return (GrVariableDeclaration) body.getMemberDeclarations()[0];
  }

  @Override
  public GrVariableDeclaration createFieldDeclarationFromText(String text) {
    final GroovyFile file = (GroovyFile)createGroovyFile("class X{\n" + text + "\n}");
    final PsiClass psiClass = file.getClasses()[0];
    return (GrVariableDeclaration)psiClass.getFields()[0].getParent();
  }

  private StringBuffer writeModifiers(String[] modifiers) {
    StringBuffer text = new StringBuffer();
    if (!(modifiers == null || modifiers.length == 0)) {
      for (String modifier : modifiers) {
        text.append(modifier);
        text.append(" ");
      }
    }
    return text;
  }

  private String getTypeText(PsiType type) {
    if (!(type instanceof PsiArrayType)) {
      final String canonical = type.getCanonicalText();
      return canonical != null ? canonical : type.getPresentableText();
    } else {
      return getTypeText(((PsiArrayType) type).getComponentType()) + "[]";
    }
  }

  @Nullable
  public GrTopStatement createTopElementFromText(String text) {
    PsiFile dummyFile = PsiFileFactory.getInstance(myProject).createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        text);
    final PsiElement firstChild = dummyFile.getFirstChild();
    if (!(firstChild instanceof GrTopStatement)) return null;

    return (GrTopStatement) firstChild;
  }

  public GrClosableBlock createClosureFromText(String closureText) throws IncorrectOperationException {
    PsiFile psiFile = createDummyFile(closureText);
    ASTNode node = psiFile.getFirstChild().getNode();
    if (node.getElementType() != GroovyElementTypes.CLOSABLE_BLOCK)
      throw new IncorrectOperationException("Invalid all text");
    return (GrClosableBlock) node.getPsi();
  }

  private GroovyFileImpl createDummyFile(String text, boolean physical) {
    final String fileName = "DUMMY__." + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension();
    final long stamp = System.currentTimeMillis();
    final PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
    return (GroovyFileImpl) factory.createFileFromText(fileName, GroovyFileType.GROOVY_FILE_TYPE, text, stamp, physical);
  }

  private GroovyFileImpl createDummyFile(String s) {
    return createDummyFile(s, false);
  }

  public GrParameter createParameter(String name, @Nullable String typeText, @Nullable String initializer, GroovyPsiElement context)
    throws IncorrectOperationException {
    StringBuilder fileText = new StringBuilder();
    fileText.append("def foo(");
    if (typeText != null) {
      fileText.append(typeText).append(" ");
    } else {
      fileText.append("def ");
    }
    fileText.append(name);
    if (initializer != null && initializer.length() > 0) {
      fileText.append(" = ").append(initializer);
    }
    fileText.append("){}");
    GroovyFileImpl groovyFile = createDummyFile(fileText.toString());
    groovyFile.setContext(context);

    ASTNode node = groovyFile.getFirstChild().getNode();
    if (node.getElementType() != GroovyElementTypes.METHOD_DEFINITION) {
      throw new IncorrectOperationException("Invalid all text");
    }
    return ((GrMethod)node.getPsi()).getParameters()[0];
  }

  public GrCodeReferenceElement createTypeOrPackageReference(String qName) {
    final GroovyFileBase file = createDummyFile("def " + qName + " i");
    LOG
      .assertTrue(file.getTopStatements().length == 1 && (GrVariableDeclaration)file.getTopStatements()[0] instanceof GrVariableDeclaration,
                  qName);
    GrVariableDeclaration varDecl = (GrVariableDeclaration) file.getTopStatements()[0];
    final GrClassTypeElement typeElement = (GrClassTypeElement) varDecl.getTypeElementGroovy();
    assert typeElement != null;
    return typeElement.getReferenceElement();
  }

  public GrTypeDefinition createTypeDefinition(String text) throws IncorrectOperationException {
    final GroovyFileBase file = createDummyFile(text);
    final GrTypeDefinition[] classes = file.getTypeDefinitions();
    if (classes.length != 1) throw new IncorrectOperationException("Incorrect type definition text");
    return classes[0];
  }

  @NotNull
  public GrTypeElement createTypeElement(String typeText, final PsiElement context) throws IncorrectOperationException {
    final GroovyFileBase file = createGroovyFile("def " + typeText + " someVar", false, context);

    GrTopStatement[] topStatements = file.getTopStatements();

    if (topStatements == null || topStatements.length == 0) throw new IncorrectOperationException("");
    GrTopStatement statement = topStatements[0];

    if (!(statement instanceof GrVariableDeclaration)) throw new IncorrectOperationException("");
    GrVariableDeclaration decl = (GrVariableDeclaration) statement;
    final GrTypeElement element = decl.getTypeElementGroovy();
    if (element == null) throw new IncorrectOperationException("");

    return element;
  }

  public GrTypeElement createTypeElement(PsiType type) throws IncorrectOperationException {
    final String typeText = getTypeText(type);
    if (typeText == null)
      throw new IncorrectOperationException("Cannot create type element: cannot obtain text for type");
    return createTypeElement(typeText);
  }

  public GrParenthesizedExpression createParenthesizedExpr(GrExpression newExpr) {
    return ((GrParenthesizedExpression) getInstance(myProject).createExpressionFromText("(" + newExpr.getText() + ")"));
  }

  public PsiElement createStringLiteral(String text) {
    return ((GrReferenceExpression) createDummyFile("a.'" + text + "'").getTopStatements()[0]).getReferenceNameElement();
  }

  public PsiElement createModifierFromText(String name) {
    final GroovyFileBase file = createDummyFile(name + "\"foo\"() {}");
    return file.getTopLevelDefinitions()[0].getFirstChild().getFirstChild();
  }

  public GrCodeBlock createMethodBodyFromText(String text) {
    final GroovyFileBase file = createDummyFile("def foo () {" + text + "}");
    final GrMethod method = (GrMethod) file.getTopLevelDefinitions()[0];
    return method.getBlock();
  }

  public GrVariableDeclaration createSimpleVariableDeclaration(String name, String typeText) {
    String classText;
    if (Character.isLowerCase(typeText.charAt(0))) {
      classText = "class A { def " + typeText + " " + name + "}";
    } else {
      classText = "class A { " + typeText + " " + name + "}";
    }

    GroovyFileBase file = (GroovyFileBase) createGroovyFile(classText);
    final GrTypeDefinitionBody body = file.getTypeDefinitions()[0].getBody();
    return (GrVariableDeclaration) body.getMemberDeclarations()[0];
  }

  public GrReferenceElement createPackageReferenceElementFromText(String newPackageName) {
    return ((GrPackageDefinition) createDummyFile("package " + newPackageName).getTopStatements()[0]).getPackageReference();
  }

  public PsiElement createDotToken(String newDot) {
    return createReferenceExpressionFromText("a" + newDot + "b").getDotToken();
  }

  public GrConstructorImpl createConstructorFromText(@NotNull String constructorName,
                                                     @Nullable String[] paramTypes,
                                                     String[] paramNames,
                                                     String body,
                                                     PsiElement context) {
    final String text = generateMethodText(null, constructorName, null, paramTypes, paramNames, body, true);

    GroovyFileImpl file = createDummyFile("class " + constructorName + "{" + text + "}");
    file.setContext(context);
    GrTopLevelDefinition definition = file.getTopLevelDefinitions()[0];
    assert definition != null && definition instanceof GrClassDefinition;
    final PsiMethod constructor = ((GrClassDefinition) definition).getMethods()[0];
    assert constructor instanceof GrConstructorImpl;
    return ((GrConstructorImpl) constructor);
  }

  @Override
  public GrLabel createLabel(@NotNull String name) {
    GroovyFileBase file = createDummyFile(name + ": println()");
    GrTopStatement definition = file.getTopStatements()[0];
    assert definition instanceof GrLabeledStatement;
    return ((GrLabeledStatement)definition).getLabel();
  }

  public GrMethod createMethodFromText(@NotNull String methodText, PsiElement context) {
    GroovyFileImpl file = createDummyFile(methodText);
    if (context != null) {
      file.setContext(context);
    }
    GrTopLevelDefinition[] definitions = file.getTopLevelDefinitions();
    if (definitions.length != 1) {
      throw new IncorrectOperationException("Can't create method from text: '" + file.getText() + "'");
    }
    GrTopLevelDefinition definition = definitions[0];
    if (!(definition instanceof GrMethod)) {
      throw new IncorrectOperationException("Can't create method from text: '" + file.getText() + "'");
    }
    return ((GrMethod)definition);
  }

  @Override
  public GrAnnotation createAnnotationFromText(String annoText) {
    return createMethodFromText(annoText + " void foo() {}", null).getModifierList().getAnnotations()[0];
  }

  public PsiFile createGroovyFile(String idText) {
    return createGroovyFile(idText, false, null);
  }

  public GroovyFile createGroovyFile(String idText, boolean isPhysical, PsiElement context) {
    GroovyFileImpl file = createDummyFile(idText, isPhysical);
    file.setContext(context);
    return file;
  }

  public PsiElement createWhiteSpace() {
    PsiFile dummyFile = PsiFileFactory.getInstance(myProject).createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        " ");
    return dummyFile.getFirstChild();
  }

  @NotNull
  public PsiElement createLineTerminator(int length) {

    String text = length <= 1 ? "\n" : "";
    if (length > 1) {
      StringBuffer buffer = new StringBuffer();
      for (; length > 0; length--) {
        buffer.append("\n");
      }
      text = buffer.toString();
    }

    PsiFile dummyFile = PsiFileFactory.getInstance(myProject).createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        text);
    PsiElement child = dummyFile.getFirstChild();
    assert child != null;
    return child;
  }

  public GrArgumentList createExpressionArgumentList(GrExpression... expressions) {
    StringBuffer text = new StringBuffer();
    text.append("ven (");
    for (GrExpression expression : expressions) {
      text.append(expression.getText()).append(", ");
    }
    if (expressions.length > 0) {
      text.delete(text.length() - 2, text.length());
    }
    text.append(")");
    PsiFile file = createGroovyFile(text.toString());
    assert file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrMethodCallExpression);
    return (((GrMethodCallExpression) file.getChildren()[0])).getArgumentList();
  }

  public GrNamedArgument createNamedArgument(@NotNull final String name, final GrExpression expression) {
    StringBuffer text = new StringBuffer();
    text.append("foo (").append(name).append(":").append(expression.getText()).append(")");
    PsiFile file = createGroovyFile(text.toString());
    assert file.getChildren()[0] != null;
    GrCall call = (GrCall)file.getChildren()[0];
    return call.getArgumentList().getNamedArguments()[0];
  }

  public GrStatement createStatementFromText(String text) {
    PsiFile file = createGroovyFile(text);
    assert ((GroovyFileBase) file).getTopStatements()[0] instanceof GrStatement;
    return (GrStatement) ((GroovyFileBase) file).getTopStatements()[0];
  }

  public GrBlockStatement createBlockStatement(@NonNls GrStatement... statements) {
    StringBuffer text = new StringBuffer();
    text.append("while (true) { \n");
    for (GrStatement statement : statements) {
      text.append(statement.getText()).append("\n");
    }
    text.append("}");
    PsiFile file = createGroovyFile(text.toString());
    assert file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrWhileStatement);
    return (GrBlockStatement) ((GrWhileStatement) file.getChildren()[0]).getBody();
  }

  public GrMethodCallExpression createMethodCallByAppCall(GrApplicationStatement callExpr) {
    StringBuffer text = new StringBuffer();
    text.append(callExpr.getInvokedExpression().getText());
    text.append("(");
    final GrCommandArgumentList argumentList = callExpr.getArgumentList();
    if (argumentList != null) text.append(argumentList.getText());
    text.append(")");
    PsiFile file = createGroovyFile(text.toString());
    assert file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrMethodCallExpression);
    return ((GrMethodCallExpression)file.getChildren()[0]);
  }

  public GrImportStatement createImportStatementFromText(String qName, boolean isStatic, boolean isOnDemand, String alias) {
    final String text = "import " + (isStatic ? "static " : "") + qName + (isOnDemand ? ".*" : "") +
        (alias != null && alias.length() > 0 ? " as " + alias : "");

    PsiFile dummyFile = PsiFileFactory.getInstance(myProject).createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        text);
    return ((GrImportStatement) dummyFile.getFirstChild());
  }

  public GrImportStatement createImportStatementFromText(@NotNull String text) {
    PsiFile dummyFile = PsiFileFactory.getInstance(myProject).createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        text);
    return ((GrImportStatement) dummyFile.getFirstChild());
  }


  private static String generateMethodText(String modifier,
                                           String name,
                                           String type,
                                           String[] paramTypes,
                                           String[] paramNames,
                                           String body,
                                           boolean isConstructor) {
    StringBuilder builder = new StringBuilder();

    if (modifier != null){
      builder.append(modifier);
      builder.append(" ");
    }

    if (!isConstructor) {
      builder.append("def ");
    }

    //This is for constructor creation
    if (type != null) {
      builder.append(type);
      builder.append(" ");
    }

    builder.append(name);
    builder.append("(");

    for (int i = 0; i < paramNames.length; i++) {
      String paramType = paramTypes == null ? null : paramTypes[i];

      if (i > 0) builder.append(", ");

      if (paramType != null) {
        builder.append(paramType);
        builder.append(" ");
      }
      builder.append(paramNames[i]);
    }

    builder.append(")");
    if (body != null) {
      builder.append(body);
    } else {
      builder.append("{");
      builder.append("}");
    }

    return builder.toString();
  }

  public GrMethod createMethodFromText(String modifier, String name, @Nullable String type, String[] paramTypes, PsiElement context) {
    PsiType psiType;
    List<PsiType> res = new ArrayList<PsiType>();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);

    for (String paramType : paramTypes) {
      try {
        psiType = factory.createTypeElement(paramType).getType();
      }
      catch (IncorrectOperationException e) {
        psiType = PsiType.getJavaLangObject(PsiManager.getInstance(myProject), ProjectScope.getAllScope(myProject));
      }
      res.add(psiType);
    }

    String[] paramNames = QuickfixUtil.getMethodArgumentsNames(myProject, res.toArray(new PsiType[res.size()]));
    final String text = generateMethodText(modifier, name, type, paramTypes, paramNames, null, false);
    return createMethodFromText(text, context);
  }

  public GrDocComment createDocCommentFromText(String text) {
    StringBuilder builder = new StringBuilder();
    builder.append(text);
    return (GrDocComment)createGroovyFile(text).getFirstChild();
  }

  @Override
  public GrDocTag createDocTagFromText(String text) {
    final GrDocComment docComment = createDocCommentFromText("/**" + text + "*/");
    return docComment.getTags()[0];
  }

  @Override
  public GrConstructorInvocation createConstructorInvocation(String text) {
    GroovyFile file = (GroovyFile)createGroovyFile("class Foo{ def Foo(){" + text + "}}");
    return ((GrConstructor)file.getClasses()[0].getConstructors()[0]).getChainingConstructorInvocation();
  }

  @Override
  public PsiReferenceList createThrownList(PsiClassType[] exceptionTypes) {
    if (exceptionTypes.length == 0) {
      return createMethodFromText("def foo(){}", null).getThrowsList();
    }
    String[] types = new String[exceptionTypes.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = exceptionTypes[i].getCanonicalText();
    }
    final String end = StringUtil.join(types, ",");
    return createMethodFromText("def foo() throws " + end + "{}", null).getThrowsList();
  }

  @Override
  public GrCatchClause createCatchClause(PsiClassType type, String parameterName) {
    StringBuffer buffer = new StringBuffer("try{} catch(");
    if (type == null) {
      buffer.append("Throwable ");
    }
    else {
      buffer.append(type.getCanonicalText()).append(" ");
    }
    buffer.append(parameterName).append("){\n}");
    final GrTryCatchStatement statement = (GrTryCatchStatement)createStatementFromText(buffer.toString());
    return statement.getCatchClauses()[0];
  }

  @Override
  public GrArgumentList createArgumentList() {
    return ((GrCall)createExpressionFromText("foo()")).getArgumentList();
  }

  public GrArgumentList createArgumentListFromText(String argListText) {
    try {
      return ((GrCall)createExpressionFromText("foo " + argListText)).getArgumentList();
    }
    catch (IncorrectOperationException e) {
      LOG.debug(argListText);
      throw e;
    }
  }

  @Override
  public GrExtendsClause createExtendsClause() {
    final GrTypeDefinition typeDefinition = createTypeDefinition("class A extends B {}");
    final GrExtendsClause clause = typeDefinition.getExtendsClause();
    clause.getReferenceElements()[0].delete();
    return clause;
  }

  @Override
  public GrImplementsClause createImplementsClause() {
    final GrTypeDefinition typeDefinition = createTypeDefinition("class A implements B {}");
    final GrImplementsClause clause = typeDefinition.getImplementsClause();
    clause.getReferenceElements()[0].delete();
    return clause;
  }

}
