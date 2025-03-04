package tech.lin2j.idea.plugin.action;

import com.intellij.openapi.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.lin2j.idea.plugin.event.ApplicationContext;
import tech.lin2j.idea.plugin.model.ConfigHelper;
import tech.lin2j.idea.plugin.model.event.TableRefreshEvent;
import tech.lin2j.idea.plugin.ssh.SshServer;
import tech.lin2j.idea.plugin.ui.dialog.HostSettingsDialog;
import tech.lin2j.idea.plugin.ui.editor.SFTPFileSystem;
import tech.lin2j.idea.plugin.ui.editor.SFTPVirtualFile;
import tech.lin2j.idea.plugin.ui.ftp.FTPConsole;
import tech.lin2j.idea.plugin.uitl.MessagesBundle;
import tech.lin2j.idea.plugin.uitl.UiUtil;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

/**
 * @author linjinjia
 * @date 2024/5/5 12:29
 */
public class HostMoreOpsAction implements ActionListener {
    public static final Logger log = LoggerFactory.getLogger(HostMoreOpsAction.class);

    private final int sshId;
    private final Project project;

    private final JButton parent;

    public HostMoreOpsAction(int sshId, Project project, JButton parent) {
        this.sshId = sshId;
        this.project = project;
        this.parent = parent;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JPopupMenu menu = new JPopupMenu();
        String propertiesText = MessagesBundle.getText("table.action.button.more.properties");
        menu.add(new JMenuItem(new AbstractAction(propertiesText) {
            @Override
            public void actionPerformed(ActionEvent e) {
                SshServer server = ConfigHelper.getSshServerById(sshId);
                new HostSettingsDialog(project, server).show();
            }
        }));

        String sftpText = MessagesBundle.getText("table.action.button.more.sftp");
        menu.add(new JMenuItem(new AbstractAction(sftpText) {
            @Override
            public void actionPerformed(ActionEvent e) {
                SshServer server = ConfigHelper.getSshServerById(sshId);
                try {
                    SFTPVirtualFile SFTPVirtualFile = new SFTPVirtualFile(
                            server.getIp(),
                            project,
                            new FTPConsole(project, server)
                    );
                    SFTPFileSystem.getInstance(project).openEditor(SFTPVirtualFile);
                } catch (IOException ex) {
                    log.error(ex.getMessage(), e);
                }
            }
        }));

        String removeText = MessagesBundle.getText("table.action.button.more.remove");
        menu.add(new JMenuItem(new AbstractAction(removeText) {
            @Override
            public void actionPerformed(ActionEvent e) {
                SshServer server = ConfigHelper.getSshServerById(sshId);
                String specific = "Host: " + server.getIp() + ":" + server.getPort();
                if (UiUtil.deleteConfirm(specific)) {
                    ConfigHelper.removeSshServer(sshId);
                    ApplicationContext.getApplicationContext().publishEvent(new TableRefreshEvent());
                }
            }
        }));
        menu.show(parent, 0, parent.getHeight());
    }
}