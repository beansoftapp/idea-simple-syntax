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

/**
 * @author cdr
 */
package com.intellij.lang.properties.references;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.ide.util.TreeFileChooserFactory;
import com.intellij.lang.properties.LastSelectedPropertiesFileStore;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.TextFieldWithHistory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class I18nizeQuickFixDialog extends DialogWrapper implements I18nizeQuickFixModel {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeQuickFixDialog");

  private JTextField myValue;
  private JComboBox myKey;
  private final TextFieldWithHistory myPropertiesFile;
  protected JPanel myPanel;
  private JCheckBox myUseResourceBundle;
  protected final Project myProject;
  protected final PsiFile myContext;

  private JPanel myPropertiesFilePanel;
  protected JPanel myExtensibilityPanel;

  protected final String myDefaultPropertyValue;
  protected final DialogCustomization myCustomization;

  public static class DialogCustomization {
    private final String title;
    private final boolean suggestExistingProperties;
    private final boolean focusValueComponent;
    private final List<PropertiesFile> propertiesFiles;
    private final String suggestedName;

    public DialogCustomization(String title, boolean suggestExistingProperties, boolean focusValueComponent,
                               List<PropertiesFile> propertiesFiles,
                               String suggestedName) {
      this.title = title;
      this.suggestExistingProperties = suggestExistingProperties;
      this.focusValueComponent = focusValueComponent;
      this.propertiesFiles = propertiesFiles;
      this.suggestedName = suggestedName;
    }

    public DialogCustomization() {
      this(null, true, false, null, null);
    }

    public String getSuggestedName() {
      return suggestedName;
    }
  }

  public I18nizeQuickFixDialog(@NotNull Project project,
                               @NotNull final PsiFile context,
                               String defaultPropertyValue,
                               DialogCustomization customization
                               ) {
    this(project, context, defaultPropertyValue, customization, false);
  }

  protected I18nizeQuickFixDialog(@NotNull Project project,
                               @NotNull final PsiFile context,
                               String defaultPropertyValue,
                               DialogCustomization customization,
                               boolean ancestorResponsible) {
    super(false);
    myProject = project;
    myContext = context;

    myDefaultPropertyValue = defaultPropertyValue;
    myCustomization = customization != null ? customization:new DialogCustomization();
    setTitle(myCustomization.title != null ? myCustomization.title:CodeInsightBundle.message("i18nize.dialog.title"));

    myPropertiesFile = new TextFieldWithHistory();
    myPropertiesFile.setHistorySize(-1);
    myPropertiesFilePanel.add(GuiUtils.constructFieldWithBrowseButton(myPropertiesFile, new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        TreeFileChooserFactory chooserFactory = TreeFileChooserFactory.getInstance(myProject);
        TreeFileChooser fileChooser = chooserFactory.createFileChooser(
          CodeInsightBundle.message("i18nize.dialog.property.file.chooser.title"), getPropertiesFile(), StdFileTypes.PROPERTIES, null);
        fileChooser.showDialog();
        PsiFile selectedFile = fileChooser.getSelectedFile();
        if (selectedFile == null) return;
        myPropertiesFile.setText(FileUtil.toSystemDependentName(selectedFile.getVirtualFile().getPath()));
      }
    }), BorderLayout.CENTER);

    myPropertiesFile.addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        propertiesFileChanged();
        somethingChanged();
      }
    });

    getKeyTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        somethingChanged();
      }
    });

    myValue.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        somethingChanged();
      }
    });


    @NonNls final String KEY = "I18NIZE_DIALOG_USE_RESOURCE_BUNDLE";
    final boolean useBundleByDefault =
      !PropertiesComponent.getInstance().isValueSet(KEY) || PropertiesComponent.getInstance().isTrueValue(KEY);
    myUseResourceBundle.setSelected(useBundleByDefault);
    myUseResourceBundle.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        PropertiesComponent.getInstance().setValue(KEY, Boolean.valueOf(myUseResourceBundle.isSelected()).toString());
      }
    });

    if (!ancestorResponsible) init();
  }

  @Override
  protected void init() {
    populatePropertiesFiles();
    propertiesFileChanged();
    somethingChanged();
    setKeyValueEditBoxes();

    super.init();
  }

  private JTextField getKeyTextField() {
    return (JTextField)myKey.getEditor().getEditorComponent();
  }

  @NotNull
  protected List<String> getExistingValueKeys(String value) {
    if(!myCustomization.suggestExistingProperties) {
      return Collections.emptyList();
    }
    final ArrayList<String> result = new ArrayList<String>();

    // check if property value already exists among properties file values and suggest corresponding key
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      for (Property property : propertiesFile.getProperties()) {
        if (Comparing.strEqual(property.getValue(), value)) {
          result.add(0, property.getUnescapedKey());
        }
      }
    }
    return result;
  }

  protected String suggestPropertyKey(String value) {
    if (myCustomization.suggestedName != null) {
      return myCustomization.suggestedName;
    }

    // suggest property key not existing in this file
    String key = defaultSuggestPropertyKey(value);
    if (key == null) {
      final StringBuilder result = new StringBuilder();
      boolean insertDotBeforeNextWord = false;
      for (int i = 0; i < value.length(); i++) {
        final char c = value.charAt(i);
        if (Character.isLetterOrDigit(c)) {
          if (insertDotBeforeNextWord) {
            result.append('.');
          }
          result.append(Character.toLowerCase(c));
          insertDotBeforeNextWord = false;
        }
        else if (c == '&') {   //do not insert dot if there is letter after the amp
          if (insertDotBeforeNextWord) continue;
          if (i == value.length() - 1) {
            continue;
          }
          if (Character.isLetter(value.charAt(i + 1))) {
            continue;
          }
          insertDotBeforeNextWord = true;
        }
        else {
          if (result.length() > 0) {
            insertDotBeforeNextWord = true;
          }
        }
      }
      key = result.toString();
    }

    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      if (propertiesFile.findPropertyByKey(key) == null) return key;

      int suffix = 1;
      while (propertiesFile.findPropertyByKey(key + suffix) != null) {
        suffix++;
      }
      return key + suffix;
    }
    else {
      return key;
    }
  }

  protected String defaultSuggestPropertyKey(String value) {
    return null;  
  }

  private void propertiesFileChanged() {
    PropertiesFile propertiesFile = getPropertiesFile();
    boolean hasResourceBundle =
      propertiesFile != null && propertiesFile.getResourceBundle().getPropertiesFiles(propertiesFile.getProject()).size() > 1;
    myUseResourceBundle.setEnabled(hasResourceBundle);
  }

  private void setKeyValueEditBoxes() {
    final List<String> existingValueKeys = getExistingValueKeys(myDefaultPropertyValue);

    if (existingValueKeys.isEmpty()) {
      getKeyTextField().setText(suggestPropertyKey(myDefaultPropertyValue));
    }
    else {
      for (String key : existingValueKeys) {
        myKey.addItem(key);
      }
      myKey.setSelectedItem(existingValueKeys.get(0));
    }


    myValue.setText(myDefaultPropertyValue);
  }

  protected void somethingChanged() {
    setOKActionEnabled(!StringUtil.isEmptyOrSpaces(getKey()));
  }

  private void populatePropertiesFiles() {
    List<String> paths = suggestPropertiesFiles();
    final String lastUrl = suggestLastSelectedFileUrl();
    final String lastPath = lastUrl != null ? FileUtil.toSystemDependentName(VfsUtil.urlToPath(lastUrl)) : null;
    Collections.sort(paths, new Comparator<String>() {
      public int compare(final String path1, final String path2) {
        if (lastPath != null && lastPath.equals(path1)) return -1;
        if (lastPath != null && lastPath.equals(path2)) return 1;
        int r = LastSelectedPropertiesFileStore.getUseCount(path2) - LastSelectedPropertiesFileStore.getUseCount(path1);
        return r == 0 ? path1.compareTo(path2) : r;
      }
    });
    myPropertiesFile.setHistory(paths);
    if (lastPath != null) {
      myPropertiesFile.setSelectedItem(lastPath);
    }
    if (myPropertiesFile.getSelectedIndex() == -1 && !paths.isEmpty()) {
      myPropertiesFile.setText(paths.get(0));
    }
  }

  private String suggestLastSelectedFileUrl() {
    return LastSelectedPropertiesFileStore.getInstance().suggestLastSelectedPropertiesFileUrl(myContext);
  }

  private void saveLastSelectedFile() {
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile != null) {
      LastSelectedPropertiesFileStore.getInstance().saveLastSelectedPropertiesFile(myContext, propertiesFile);
    }
  }

  protected List<String> suggestPropertiesFiles() {
    if (myCustomization.propertiesFiles != null && myCustomization.propertiesFiles.size() > 0 ) {
      ArrayList<String> list = new ArrayList<String>();
      for (PropertiesFile propertiesFile : myCustomization.propertiesFiles) {
        final VirtualFile virtualFile = propertiesFile.getVirtualFile();
        if (virtualFile != null) {
          list.add(virtualFile.getPath());
        }
      }
      return list;
    }
    return defaultSuggestPropertiesFiles();
  }

  protected List<String> defaultSuggestPropertiesFiles() {
    return I18nUtil.defaultGetPropertyFiles(myProject);
  }

  protected PropertiesFile getPropertiesFile() {
    String path = FileUtil.toSystemIndependentName(myPropertiesFile.getText());
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
    if (virtualFile != null) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
      if (psiFile instanceof PropertiesFile) return (PropertiesFile)psiFile;
    }
    return null;
  }

  private boolean createPropertiesFileIfNotExists() {
    if (getPropertiesFile() != null) return true;
    final String path = FileUtil.toSystemIndependentName(myPropertiesFile.getText());
    if (StringUtil.isEmptyOrSpaces(path)) {
      String message = CodeInsightBundle.message("i18nize.empty.file.path", myPropertiesFile.getText());
      Messages.showErrorDialog(myProject, message, CodeInsightBundle.message("i18nize.error.creating.properties.file"));
      myPropertiesFile.requestFocusInWindow();
      return false;
    }
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(path);
    if (fileType != StdFileTypes.PROPERTIES) {
      String message = CodeInsightBundle.message("i18nize.cant.create.properties.file.because.its.name.is.associated",
                                                 myPropertiesFile.getText(), fileType.getDescription());
      Messages.showErrorDialog(myProject, message, CodeInsightBundle.message("i18nize.error.creating.properties.file"));
      myPropertiesFile.requestFocusInWindow();
      return false;
    }

    final VirtualFile virtualFile;
    try {
      final File file = new File(path).getCanonicalFile();
      FileUtil.createParentDirs(file);
      final IOException[] e = new IOException[1];
      virtualFile = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
        public VirtualFile compute() {
          VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.getParentFile());
          try {
            if (dir == null) {
              throw new IOException("Error creating directory structure for file '" + path + "'");
            }
            return dir.createChildData(this, file.getName());
          }
          catch (IOException e1) {
            e[0] = e1;
          }
          return null;
        }
      });
      if (e[0] != null) throw e[0];
    }
    catch (IOException e) {
      Messages.showErrorDialog(myProject, e.getLocalizedMessage(), CodeInsightBundle.message("i18nize.error.creating.properties.file"));
      return false;
    }

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
    return psiFile instanceof PropertiesFile;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myCustomization.focusValueComponent ? myValue:myKey;
  }

  public void dispose() {
    saveLastSelectedFile();
    super.dispose();
  }

  protected void doOKAction() {
    if (!createPropertiesFileIfNotExists()) return;
    Collection<PropertiesFile> propertiesFiles = getAllPropertiesFiles();
    for (PropertiesFile propertiesFile : propertiesFiles) {
      Property existingProperty = propertiesFile.findPropertyByKey(getKey());
      final String propValue = myValue.getText();
      if (existingProperty != null && !Comparing.strEqual(existingProperty.getValue(), propValue)) {
        Messages.showErrorDialog(myProject, CodeInsightBundle.message("i18nize.dialog.error.property.already.defined.message", getKey(),
                                                                      propertiesFile.getName()),
                                            CodeInsightBundle.message("i18nize.dialog.error.property.already.defined.title"));
        return;
      }
    }

    super.doOKAction();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  public void doHelpAction() {
    HelpManager.getInstance().invokeHelp("editing.propertyFile.i18nInspection");
  }


  public JComponent getValueComponent() {
    return myValue;
  }

  public String getValue() {
    return myValue.getText();
  }

  public String getKey() {
    return getKeyTextField().getText();
  }

  public boolean hasValidData() {
    assert !ApplicationManager.getApplication().isUnitTestMode();
    show();

    return getExitCode() == OK_EXIT_CODE;
  }

  private boolean isUseResourceBundle() {
    return myUseResourceBundle.isEnabled() && myUseResourceBundle.isSelected();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.codeInsight.i18n.I18nizeQuickFixDialog";
  }

  public Collection<PropertiesFile> getAllPropertiesFiles() {
    PropertiesFile propertiesFile = getPropertiesFile();
    if (propertiesFile == null) return Collections.emptySet();
    Collection<PropertiesFile> propertiesFiles;
    if (isUseResourceBundle()) {
      propertiesFiles = propertiesFile.getResourceBundle().getPropertiesFiles(myProject);
    }
    else {
      propertiesFiles = Collections.singleton(propertiesFile);
    }
    return propertiesFiles;
  }
}
