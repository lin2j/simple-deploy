package tech.lin2j.idea.plugin.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import icons.MyIcons;
import org.jetbrains.annotations.NotNull;
import tech.lin2j.idea.plugin.model.ConfigHelper;
import tech.lin2j.idea.plugin.model.ConfigImportExport;
import tech.lin2j.idea.plugin.model.ExportOptions;
import tech.lin2j.idea.plugin.uitl.FileUtil;
import tech.lin2j.idea.plugin.uitl.ImportExportUtil;
import tech.lin2j.idea.plugin.uitl.MessagesBundle;
import tech.lin2j.idea.plugin.uitl.EasyDeployPluginUtil;

import javax.swing.SwingUtilities;

/**
 * @author linjinjia
 * @date 2024/7/17 21:19
 */
public class ConfigExportAction extends NewUpdateThreadAction {

    private static final Logger log = Logger.getInstance(ConfigExportAction.class);
    private static final String text = MessagesBundle.getText("action.dashboard.export-import.export.text");

    public ConfigExportAction() {
        super(text, text, MyIcons.Actions.Export);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        try {
            String title = MessagesBundle.getText("dialog.ie.password.title");
            String tip = MessagesBundle.getText("dialog.ie.password.export.text");
            String password = Messages.showPasswordDialog(tip, title);
            if (StringUtil.isEmpty(password)) {
                SwingUtilities.invokeLater(() -> {
                    Messages.showWarningDialog("Password is blank", title);
                });
                return;
            }

            String filepath = ConfigHelper.pluginSetting().getDefaultExportImportPath();
            String filename = filepath + "/EasyDeploy@" + EasyDeployPluginUtil.version() + ".json";

            ExportOptions options = ConfigHelper.pluginSetting().getExportOptions();
            ConfigImportExport dto = ImportExportUtil.exportBaseOnOptions(options);
            ImportExportUtil.exportConfigToJsonFile(dto, filename, password);
            FileUtil.openDir(filepath);
        } catch (Exception ex) {
            log.error(ex);
            SwingUtilities.invokeLater(() -> Messages.showErrorDialog("Export failed", "Export Error"));
        }
    }
}