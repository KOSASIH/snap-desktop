package org.esa.snap.gui;

import com.bc.ceres.core.Assert;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.io.BeamFileChooser;
import org.esa.beam.util.io.FileUtils;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.prefs.Preferences;

/**
 * Utility class which is used to display various commonly and frequently used message dialogs.
 *
 * @author Marco, Norman
 * @since 2.0
 */
@NbBundle.Messages({
        "LBL_Information=Information",
        "LBL_Question=Question",
        "LBL_Message=Message",
        "LBL_Warning=Warning",
        "LBL_Error=Error",
        "LBL_DoNotShowThisMessage=Don't show this message anymore.",
        "LBL_QuestionRemember=Remember my decision and don't ask again."
})
public class SnapDialogs {

    public enum Answer {
        YES,
        NO,
        CANCELLED
    }

    private static final String PREF_KEY_SUFFIX_DECISION = ".decision";
    private static final String PREF_KEY_SUFFIX_DONTSHOW = ".dontShow";
    private static final String PREF_VALUE_YES = "yes";
    private static final String PREF_VALUE_NO = "no";
    private static final String PREF_VALUE_TRUE = "true";

    /**
     * Displays a modal dialog with the provided information message text.
     *
     * @param message        The message text to be displayed.
     * @param preferencesKey If not {@code null}, a checkbox is displayed, and if checked the dialog will not be displayed again which lets users store the answer
     */
    public static void showInformation(String message, String preferencesKey) {
        showInformation(Bundle.LBL_Information(), message, preferencesKey);
    }

    /**
     * Displays a modal dialog with the provided information message text.
     *
     * @param title          The dialog title. May be {@code null}.
     * @param message        The information message text to be displayed.
     * @param preferencesKey If not {@code null}, a checkbox is displayed, and if checked the dialog will not be displayed again which lets users store the answer
     */
    public static void showInformation(String title, String message, String preferencesKey) {
        showMessage(title != null ? title : Bundle.LBL_Information(), message, JOptionPane.INFORMATION_MESSAGE, preferencesKey);
    }

    /**
     * Displays a modal dialog with the provided warning message text.
     *
     * @param message The information message text to be displayed.
     */
    public static void showWarning(String message) {
        showWarning(null, message, null);
    }

    /**
     * Displays a modal dialog with the provided warning message text.
     *
     * @param title          The dialog title. May be {@code null}.
     * @param message        The warning message text to be displayed.
     * @param preferencesKey If not {@code null}, a checkbox is displayed, and if checked the dialog will not be displayed again which lets users store the answer
     */
    public static void showWarning(String title, String message, String preferencesKey) {
        showMessage(title != null ? title : Bundle.LBL_Warning(), message, JOptionPane.WARNING_MESSAGE, preferencesKey);
    }

    /**
     * Displays a modal dialog with the provided message text.
     *
     * @param title          The dialog title. May be {@code null}.
     * @param message        The message text to be displayed.
     * @param messageType    The type of the message.
     * @param preferencesKey If not {@code null}, a checkbox is displayed, and if checked the dialog will not be displayed again which lets users store the answer
     */
    public static void showMessage(String title, String message, int messageType, String preferencesKey) {
        title = getDialogTitle(title != null ? title : Bundle.LBL_Message());
        if (preferencesKey != null) {
            String decision = getPreferences().get(preferencesKey + PREF_KEY_SUFFIX_DONTSHOW, "");
            if (decision.equals(PREF_VALUE_TRUE)) {
                return;
            }
            JPanel panel = new JPanel(new BorderLayout(4, 4));
            panel.add(new JLabel(message), BorderLayout.CENTER);
            JCheckBox dontShowCheckBox = new JCheckBox(Bundle.LBL_DoNotShowThisMessage(), false);
            panel.add(dontShowCheckBox, BorderLayout.SOUTH);
            NotifyDescriptor d = new NotifyDescriptor(panel, title, NotifyDescriptor.DEFAULT_OPTION, messageType, null, null);
            DialogDisplayer.getDefault().notify(d);
            boolean storeResult = dontShowCheckBox.isSelected();
            if (storeResult) {
                getPreferences().put(preferencesKey + PREF_KEY_SUFFIX_DONTSHOW, PREF_VALUE_TRUE);
            }
        } else {
            NotifyDescriptor d = new NotifyDescriptor(message, title, NotifyDescriptor.DEFAULT_OPTION, messageType, null, null);
            DialogDisplayer.getDefault().notify(d);
        }
    }

    /**
     * Displays a modal dialog which requests a decision from the user.
     *
     * @param title          The dialog title. May be {@code null}.
     * @param message        The question text to be displayed.
     * @param allowCancel    If {@code true}, the dialog also offers a cancel button.
     * @param preferencesKey If not {@code null}, a checkbox is displayed, and if checked the dialog will not be displayed again which lets users store the answer
     * @return {@link Answer#YES}, {@link Answer#NO}, or {@link Answer#CANCELLED}.
     */
    public static Answer requestDecision(String title, String message, boolean allowCancel, String preferencesKey) {
        Object result;
        boolean storeResult;
        int optionType = allowCancel ? NotifyDescriptor.YES_NO_CANCEL_OPTION : NotifyDescriptor.YES_NO_OPTION;
        title = getDialogTitle(title != null ? title : Bundle.LBL_Question());
        if (preferencesKey != null) {
            String decision = getPreferences().get(preferencesKey + PREF_KEY_SUFFIX_DECISION, "");
            if (decision.equals(PREF_VALUE_YES)) {
                return Answer.YES;
            } else if (decision.equals(PREF_VALUE_NO)) {
                return Answer.NO;
            }
            JPanel panel = new JPanel(new BorderLayout(4, 4));
            panel.add(new JLabel(message), BorderLayout.CENTER);
            JCheckBox decisionCheckBox = new JCheckBox(Bundle.LBL_QuestionRemember(), false);
            panel.add(decisionCheckBox, BorderLayout.SOUTH);
            NotifyDescriptor d = new NotifyDescriptor.Confirmation(panel, title, optionType);
            result = DialogDisplayer.getDefault().notify(d);
            storeResult = decisionCheckBox.isSelected();
        } else {
            NotifyDescriptor d = new NotifyDescriptor.Confirmation(message, title, optionType);
            result = DialogDisplayer.getDefault().notify(d);
            storeResult = false;
        }
        if (NotifyDescriptor.YES_OPTION.equals(result)) {
            if (storeResult) {
                getPreferences().put(preferencesKey + PREF_KEY_SUFFIX_DECISION, PREF_VALUE_YES);
            }
            return Answer.YES;
        } else if (NotifyDescriptor.NO_OPTION.equals(result)) {
            if (storeResult) {
                getPreferences().put(preferencesKey + PREF_KEY_SUFFIX_DECISION, PREF_VALUE_NO);
            }
            return Answer.NO;
        } else {
            return Answer.CANCELLED;
        }
    }

    /**
     * Displays a modal dialog with the provided error message text.
     *
     * @param message The error message text to be displayed.
     */
    public static void showError(String message) {
        showError(null, message);
    }

    /**
     * Displays a modal dialog with the provided error message text.
     *
     * @param title   The dialog title. May be {@code null}.
     * @param message The error message text to be displayed.
     */
    public static void showError(String title, String message) {
        NotifyDescriptor nd = new NotifyDescriptor(message,
                                                   title != null ? title : Bundle.LBL_Error(),
                                                   JOptionPane.OK_OPTION,
                                                   NotifyDescriptor.ERROR_MESSAGE,
                                                   null,
                                                   null);
        DialogDisplayer.getDefault().notify(nd);
    }

    /**
     * Opens a standard file-open dialog box.
     *
     * @param title          a dialog-box title
     * @param dirsOnly       whether or not to select only directories
     * @param fileFilter     the file filter to be used, can be <code>null</code>
     * @param preferencesKey the key under which the last directory the user visited is stored
     * @return the file selected by the user or <code>null</code> if the user canceled file selection
     */
    public final File requestFileForOpen(String title,
                                         boolean dirsOnly,
                                         FileFilter fileFilter,
                                         String preferencesKey) {
        Assert.notNull(preferencesKey, "preferencesKey");

        String lastDir = getPreferences().get(preferencesKey, SystemUtils.getUserHomeDir().getPath());
        File currentDir = new File(lastDir);

        BeamFileChooser fileChooser = new BeamFileChooser();
        fileChooser.setCurrentDirectory(currentDir);
        if (fileFilter != null) {
            fileChooser.setFileFilter(fileFilter);
        }
        fileChooser.setDialogTitle(getDialogTitle(title));
        fileChooser.setFileSelectionMode(dirsOnly ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        int result = fileChooser.showOpenDialog(SnapApp.getDefault().getMainFrame());
        if (fileChooser.getCurrentDirectory() != null) {
            getPreferences().put(preferencesKey, fileChooser.getCurrentDirectory().getPath());
        }
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (file == null || file.getName().equals("")) {
                return null;
            }
            return file;
        }
        return null;
    }

    /**
     * Opens a standard save dialog box.
     *
     * @param title            A dialog-box title.
     * @param dirsOnly         Whether or not to select only directories.
     * @param fileFilter       The file filter to be used, can be <code>null</code>.
     * @param defaultExtension The extension used as default.
     * @param fileName         The initial filename.
     * @param preferenceKey    The key under which the last directory the user visited is stored.
     * @return The file selected by the user or <code>null</code> if the user cancelled the file selection.
     */
    public static File requestFileForSave(String title,
                                          boolean dirsOnly,
                                          FileFilter fileFilter,
                                          String defaultExtension,
                                          String fileName,
                                          String preferenceKey) {

        // Loop while the user does not want to overwrite a selected, existing file
        // or if the user presses "Cancel"
        //
        File file = null;
        while (file == null) {
            file = requestFileForSave2(title, dirsOnly, fileFilter, defaultExtension, fileName, preferenceKey);
            if (file == null) {
                return null; // Cancelled
            } else if (file.exists()) {
                Answer answer = requestDecision(getDialogTitle(title),
                                                MessageFormat.format(
                                                        "The file ''{0}'' already exists.\nDo you wish to overwrite it?",
                                                        file),
                                                true, null);
                if (answer == Answer.CANCELLED) {
                    return null;
                } else if (answer == Answer.NO) {
                    file = null; // No, do not overwrite, let user select another file
                }
            }
        }
        return file;
    }

    private static File requestFileForSave2(String title,
                                            boolean dirsOnly,
                                            FileFilter fileFilter,
                                            String defaultExtension,
                                            final String fileName,
                                            final String preferenceKey) {

        Assert.notNull(preferenceKey, "preferenceKey");

        String lastDir = getPreferences().get(preferenceKey, SystemUtils.getUserHomeDir().getPath());
        File currentDir = new File(lastDir);

        BeamFileChooser fileChooser = new BeamFileChooser();
        fileChooser.setCurrentDirectory(currentDir);
        if (fileFilter != null) {
            fileChooser.setFileFilter(fileFilter);
        }
        if (fileName != null) {
            fileChooser.setSelectedFile(new File(FileUtils.exchangeExtension(fileName, defaultExtension)));
        }
        fileChooser.setDialogTitle(getDialogTitle(title));
        fileChooser.setFileSelectionMode(dirsOnly ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);

        int result = fileChooser.showSaveDialog(SnapApp.getDefault().getMainFrame());
        if (fileChooser.getCurrentDirectory() != null) {
            getPreferences().put(preferenceKey, fileChooser.getCurrentDirectory().getPath());
        }
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (file == null || file.getName().equals("")) {
                return null;
            }
            String path = file.getPath();
            if (defaultExtension != null) {
                if (!path.toLowerCase().endsWith(defaultExtension.toLowerCase())) {
                    path = path.concat(defaultExtension);
                }
            }
            return new File(path);
        }
        return null;
    }


    private static String getDialogTitle(String titleText) {
        return MessageFormat.format("{0} - {1}", SnapApp.getDefault().getInstanceName(), titleText);
    }

    private static Preferences getPreferences() {
        return SnapApp.getDefault().getPreferences();
    }
}
