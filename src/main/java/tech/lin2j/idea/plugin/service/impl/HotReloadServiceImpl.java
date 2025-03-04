package tech.lin2j.idea.plugin.service.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiJavaFile;
import com.intellij.task.ProjectTaskManager;
import org.apache.commons.compress.utils.IOUtils;
import org.codehaus.plexus.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import tech.lin2j.idea.plugin.service.IHotReloadService;
import tech.lin2j.idea.plugin.ssh.SshStatus;
import tech.lin2j.idea.plugin.ssh.sshj.SshjConnection;
import tech.lin2j.idea.plugin.uitl.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/**
 * @author linjinjia
 * @date 2024/10/20 15:28
 */
public class HotReloadServiceImpl implements IHotReloadService {

    private static final Logger log = Logger.getInstance(HotReloadServiceImpl.class);

    static final String ARTHAS_PACK = "arthas.tar.gz";
    static final String ATTACH_PATTERN = "bash -lc 'java -jar %s -attach-only %s --telnet-port %s --http-port %s'";
    static final String ARTHAS_SERVICE_PATTERN = "http://127.0.0.1:%s/api";
    static final String RETRANSFORM_DATA_PATTERN = "{\"action\": \"exec\",\"command\": \"retransform %s\"}";
    static final String SESSION_PATTERN = "{\"action\": \"exec\",\"command\": \"session\"}";
    static final String CURL_PATTERN = "curl -Ss -XPOST %s -d '%s'";

    @Override
    public boolean isArthasPackExist(SshjConnection conn) throws IOException {
        SshStatus result = conn.execute("ls " + getArthasBootJar());
        if (!result.isSuccess()) {
            if (result.getMessage().contains("No such file")) {
                return false;
            }
            Messages.showErrorDialog(result.getMessage(), "Check Arthas Pack");
        }
        return true;
    }

    @Override
    public void uploadArthasPack(Project project, SshjConnection conn, ProgressIndicator indicator) throws IOException {
        indicator.setText("Check if arthas pack exist");
        indicator.setFraction(0.1f);
        conn.mkdirs(getToolDirectory());
        if (isArthasPackExist(conn)) {
            showBalloonMessage(project, "Arthas Pack", "Arthas Pack already exist");
            indicator.setFraction(1f);
            return;
        }
        indicator.setFraction(0.3f);
        indicator.setText("Copy to local");
        String localPackPath = extractArthasPack();
        if (localPackPath.isEmpty()) {
            showBalloonMessage(project, "Arthas Pack", "Copy of toolkit to local failed, please try again");
            indicator.setFraction(1f);
            return;
        }

        indicator.setFraction(0.5f);
        indicator.setText("Upload arthas pack");
        conn.upload(localPackPath, getToolDirectory());

        indicator.setFraction(0.8f);
        indicator.setText("Extract arthas pack");
        SshStatus unpackResult = conn.execute("cd " + getToolDirectory() + "&& tar -xzvf " + getRemotePackPath());
        if (unpackResult.isSuccess()) {
            showBalloonMessage(project, "Arthas Pack", "Tool kit initialization successful");
        } else {
            showBalloonMessage(project, "Arthas Pack", unpackResult.getMessage());
        }
        indicator.setFraction(1f);
    }

    @Override
    public List<String> listJavaProcess(SshjConnection conn) throws IOException {
        SshStatus result = conn.execute("bash -lc 'jps'");
        if (result.isSuccess()) {
            return List.of(result.getMessage().split("\n"));
        }
        Messages.showErrorDialog(result.getMessage(), "Fetch Java Process");
        return List.of();
    }

    @Override
    public boolean isAttached(SshjConnection conn, int pid, int httpPort) throws IOException {
        String url = String.format(ARTHAS_SERVICE_PATTERN, httpPort);
        String curlCmd = String.format(CURL_PATTERN, url, SESSION_PATTERN);
        String pipeCmd = curlCmd + " | awk -F 'javaPid\\\":' '{print $2}' | awk -F ',' '{print $1}'";
        SshStatus result = conn.execute(pipeCmd);
        if (result.isSuccess()) {
            String remotePid = result.getMessage().replaceAll("\n", "");
            return Objects.equals(pid + "", remotePid);
        }
        return false;
    }

    @Override
    public void attachRemoteJavaProcess(Project project, SshjConnection conn, int pid, int httpPort) throws IOException {
        String arthasCmd = String.format(ATTACH_PATTERN, getArthasBootJar(), pid, httpPort + 1, httpPort);
        log.info(arthasCmd);
        SshStatus result = conn.execute(arthasCmd);
        if (result.isSuccess()) {
            conn.mkdirs(getProcDirectory());
            createPidFile(conn, pid, httpPort);
            String msg = String.format("Attach process [%s] successful", pid);
            showBalloonMessage(project, "Attach Remote Java Process", msg);
        } else {
            showBalloonMessage(project, "Attach Process Failed", result.getMessage());
        }
    }

    @Override
    public Integer getAttachedHttpPort(SshjConnection conn, int pid) throws IOException {
        String cmd = String.format("cat %s", getProcDirectory() + pid);
        SshStatus result = conn.execute(cmd);
        if (result.isSuccess()) {
            String port = result.getMessage().replaceAll("\n", "");
            return Integer.parseInt(port);
        }
        return null;
    }


    @Override
    public void compileAndRetransformClass(Project project,
                                           SshjConnection conn,
                                           PsiJavaFile psiFile,
                                           int httpPort) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Class retransform") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(false);
                ProjectTaskManager instance = ProjectTaskManager.getInstance(project);
                Promise<ProjectTaskManager.Result> promise = instance.compile(psiFile.getVirtualFile());
                promise.then(result -> {
                    if (result.hasErrors()) {
                        // 编译报错
                        return result.hasErrors();
                    }
                    // class 文件查找
                    String classFilename = psiFile.getName().replace(".java", ".class");
                    String classPackage = psiFile.getPackageName().replaceAll("\\.", "/");
                    String classFilePath = classPackage + "/" + classFilename;
                    Module[] modules = ModuleManager.getInstance(project).getModules();
                    for (Module module : modules) {
                        String outputPath = CompilerPaths.getModuleOutputPath(module, false);
                        String classFile = outputPath + "/" + classFilePath;
                        File cf = new File(classFile);
                        if (!cf.exists()) {
                            continue;
                        }
                        // 发送文件到服务器
                        indicator.setText("Upload target class file: " + classFilePath);
                        indicator.setFraction(0.1f);
                        String targetClass = sendClassFile(conn, module, cf.getAbsolutePath(), classPackage, classFilename);

                        indicator.setFraction(0.5f);
                        indicator.setText("Retransform target class file");
                        retransform(project, conn, targetClass, httpPort);
                        break;
                    }
                    indicator.setFraction(1f);
                    return result.hasErrors();
                });
            }
        });
    }

    @Override
    public void retransform(Project project, SshjConnection conn, String targetClass, int httpPort) {
        String url = String.format(ARTHAS_SERVICE_PATTERN, httpPort);
        String arthasCmd = String.format(RETRANSFORM_DATA_PATTERN, getHotClassDirectory() + targetClass);
        String curlCmd = String.format(CURL_PATTERN, url, arthasCmd);

        log.info(curlCmd);
        try {
            SshStatus result = conn.execute(curlCmd);
            if (!result.isSuccess()) {
                showBalloonMessage(project, "Arthas Retransform Failed", result.getMessage());
            } else {
                showBalloonMessage(project, "Arthas Retransform Success", "Class update successfully");
            }
        } catch (IOException e) {
            showBalloonMessage(project, "Arthas Retransform Failed", e.getMessage());
        }
    }

    @Override
    public String getArthasBootJar() {
        return getToolDirectory() + "arthas-bin/arthas-boot.jar";
    }

    private String getToolDirectory() {
        return "/tmp/EasyDeploy/tool/";
    }

    private String getProcDirectory() {
        return "/tmp/EasyDeploy/proc/";
    }

    private String getHotClassDirectory() {
        return "/tmp/EasyDeploy/classes/";
    }

    private String getRemotePackPath() {
        return getToolDirectory() + ARTHAS_PACK;
    }

    private String sendClassFile(SshjConnection conn, Module module,
                                 String localClassFilePath, String classPackage, String classFilename) {
        try {
            String remoteDir = getHotClassDirectory() + module.getName() + "/" + classPackage;
            conn.mkdirs(remoteDir);
            conn.upload(localClassFilePath, remoteDir);
            return module.getName() + "/" + classPackage + "/" + classFilename;
        } catch (IOException e) {
            return "";
        }
    }

    private void createPidFile(SshjConnection conn, int pid, int httpPort) throws IOException {
        String cmd = String.format("echo '%s' > %s", httpPort, getProcDirectory() + pid);
        SshStatus result = conn.execute(cmd);
        if (!result.isSuccess()) {
            log.error("create pid file failed: " + result.getMessage());
        }
    }

    private String extractArthasPack() throws IOException {
        // create directory
        String edDir = FileUtil.getHomeDir() + "/.easy-deploy/";
        File edDirFile = new File(edDir);
        if (!edDirFile.exists()) {
            FileUtils.mkdir(edDir);
        }
        // create zip file
        String localPackPath = edDir + ARTHAS_PACK;
        File localPackFile = new File(localPackPath);
        if (localPackFile.exists()) {
            return localPackPath;
        }
        boolean ignored = localPackFile.createNewFile();
        // copy file
        try (InputStream packStream = getClass().getClassLoader().getResourceAsStream(ARTHAS_PACK);
             OutputStream localPackStream = new FileOutputStream(localPackFile)) {
            if (packStream == null) {
                return "";
            }
            IOUtils.copy(packStream, localPackStream, 2048);
            localPackStream.flush();
        }
        return localPackPath;
    }

    private void showBalloonMessage(Project project, String title, String content) {
        PluginNotificationService service = ApplicationManager.getApplication().getService(PluginNotificationService.class);
        service.showNotification(project, title, content);
    }
}
