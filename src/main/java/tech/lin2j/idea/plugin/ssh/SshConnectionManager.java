package tech.lin2j.idea.plugin.ssh;

import com.intellij.openapi.diagnostic.Logger;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.LoggerFactory;
import net.schmizz.sshj.connection.channel.direct.DirectConnection;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import tech.lin2j.idea.plugin.domain.model.ConfigHelper;
import tech.lin2j.idea.plugin.enums.AuthType;
import tech.lin2j.idea.plugin.ssh.sshj.SshjConnection;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;

/**
 * @author linjinjia
 * @date 2022/6/25 15:34
 */
public class SshConnectionManager {

    private static final Logger log = Logger.getInstance(SshConnectionManager.class);

    public static SSHClient makeSshClient(SshServer server) throws IOException {
        DefaultConfig defaultConfig = new DefaultConfig();
        defaultConfig.setLoggerFactory(LoggerFactory.DEFAULT);
        SSHClient sshClient = new SSHClient();
        sshClient.addHostKeyVerifier(new PromiscuousVerifier());
        sshClient.setConnectTimeout(5000);
        sshClient.connect(server.getIp(), server.getPort());
        boolean needPemPrivateKey = AuthType.needPemPrivateKey(server.getAuthType());
        if (needPemPrivateKey) {
            KeyProvider keyProvider = sshClient.loadKeys(server.getPemPrivateKey());
            sshClient.authPublickey(server.getUsername(), keyProvider);
        } else {
            sshClient.authPassword(server.getUsername(), server.getPassword());
        }
        return sshClient;
    }

    public static Deque<SSHClient> makeSshClients(SshServer server) throws IOException {
        LinkedList<SshServer> hostChain = new LinkedList<>();
        hostChain.add(server);
        SshServer tmp = server;
        boolean proxyNotFound = false;
        while (tmp.getProxy() != null) {
            SshServer proxy = ConfigHelper.getSshServerById(tmp.getProxy());
            if (proxy == null) {
                log.error("jump server not found: " + tmp.getProxy());
                proxyNotFound = true;
                break;
            }
            hostChain.addFirst(proxy);
            tmp = proxy;
        }
        if (proxyNotFound) {
            throw new RuntimeException("xxx");
        }

        Deque<SSHClient> clients = new LinkedList<>();
        for(SshServer host : hostChain) {
            SSHClient client = new SSHClient();
            client.addHostKeyVerifier(new PromiscuousVerifier());
            client.setConnectTimeout(5000);
            // jump
            if (clients.size() == 0) {
                client.connect(host.getIp(), host.getPort());
            } else {
                DirectConnection tunnel = clients.getLast().newDirectConnection(host.getIp(), host.getPort());
                client.connectVia(tunnel);
            }
            // auth
            boolean needPemPrivateKey = AuthType.needPemPrivateKey(host.getAuthType());
            if (needPemPrivateKey) {
                KeyProvider keyProvider = client.loadKeys(host.getPemPrivateKey());
                client.authPublickey(host.getUsername(), keyProvider);
            } else {
                client.authPassword(host.getUsername(), host.getPassword());
            }

            clients.addLast(client);
        }

        return clients;
    }

    public static SshjConnection makeSshjConnection(SshServer server) throws IOException {
        return new SshjConnection(makeSshClients(server));
    }
}