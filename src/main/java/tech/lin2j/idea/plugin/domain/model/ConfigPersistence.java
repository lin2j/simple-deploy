package tech.lin2j.idea.plugin.domain.model;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author linjinjia
 * @date 2022/4/24 17:52
 */
@State(
        name = "SimpleDeployConfig",
        storages = @Storage(value = "deploy-helper-settings.xml")
)
public class ConfigPersistence implements PersistentStateComponent<ConfigPersistence>, Serializable {

    private List<SshServer> sshServers;

    private List<Command> commands;

    private List<SshUpload> sshUploads;

    private List<UploadProfile> uploadProfiles;

    @Override
    public @Nullable ConfigPersistence getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ConfigPersistence state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public List<SshServer> getSshServers() {
        if (sshServers == null) {
            sshServers = new CopyOnWriteArrayList<>();
        }
        return sshServers;
    }

    public void setSshServers(List<SshServer> sshServers) {
        this.sshServers = sshServers;
    }

    public List<Command> getCommands() {
        if (commands == null) {
            commands = new CopyOnWriteArrayList<>();
        }
        return commands;
    }

    public void setCommands(List<Command> commands) {
        this.commands = commands;
    }

    public List<SshUpload> getSshUploads() {
        if (sshUploads == null) {
            sshUploads = new CopyOnWriteArrayList<>();
        }
        return sshUploads;
    }

    public void setSshUploads(List<SshUpload> sshUploads) {
        this.sshUploads = sshUploads;
    }

    public List<UploadProfile> getUploadProfiles() {
        if (uploadProfiles == null) {
            uploadProfiles = new CopyOnWriteArrayList<>();
        }
        return uploadProfiles;
    }

    public void setUploadProfiles(List<UploadProfile> uploadProfiles) {
        this.uploadProfiles = uploadProfiles;
    }
}