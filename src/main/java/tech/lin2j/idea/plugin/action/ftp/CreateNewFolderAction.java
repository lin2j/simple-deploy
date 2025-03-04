package tech.lin2j.idea.plugin.action.ftp;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.lin2j.idea.plugin.action.NewUpdateThreadAction;
import tech.lin2j.idea.plugin.ui.ftp.container.FileTableContainer;
import tech.lin2j.idea.plugin.uitl.MessagesBundle;

import java.io.IOException;

/**
 * @author linjinjia
 * @date 2024/4/4 17:09
 */
public class CreateNewFolderAction extends NewUpdateThreadAction {

    public static final Logger log = LoggerFactory.getLogger(CreateNewFolderAction.class);

    private static final String text = MessagesBundle.getText("action.ftp.new-folder.text");
    private static final String desc = MessagesBundle.getText("action.ftp.new-folder.description");

    private final FileTableContainer container;


    public CreateNewFolderAction(FileTableContainer container) {
        super(text, desc, AllIcons.Actions.NewFolder);
        this.container = container;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        String folderName = Messages.showInputDialog("Folder Name", "Create Folder", AllIcons.Actions.NewFolder);
        if (StringUtil.isEmpty(folderName)) {
            return;
        }
        if (!folderName.startsWith("/")) {
            folderName = "/" + folderName;
        }
        String path = container.getPath() + folderName;
        String err = null;
        boolean success = false;
        try {
            success = container.createNewFolder(path);
            if (success) {
                container.refreshFileList();
            }
        } catch (IOException ex) {
            log.error(ex.getMessage(), e);
            err = ex.getMessage();
        }
        if (!success) {
            Messages.showErrorDialog("create directory failed: " + err , "Create Folder Error");
        }
    }

}