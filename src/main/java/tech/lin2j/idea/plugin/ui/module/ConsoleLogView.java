package tech.lin2j.idea.plugin.ui.module;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBSplitter;
import tech.lin2j.idea.plugin.ssh.CommandLog;

import javax.swing.*;
import java.awt.*;

/**
 *
 * @author linjinjia
 * @date 2024/11/27 22:23
 */
public class ConsoleLogView extends SimpleToolWindowPanel implements CommandLog, Disposable {
    private static final String ID = "CommandLogImpl";
    private static final String TITLE = "Command Log";

    private final Project project;
    private ConsoleViewImpl console;
    private JPanel root;

    public ConsoleLogView(Project project) {
        super(false, true);
        this.project = project;
        initRoot();

        JBSplitter splitter = new JBSplitter(false);
        splitter.setSplitterProportionKey("main.splitter.key");
        splitter.setFirstComponent(root);
        splitter.setProportion(0.3f);
        setContent(splitter);
    }

    public JPanel getRoot() {
        return root;
    }

    public ConsoleView getConsoleView() {
        return console;
    }

    private void initRoot() {
        root = new JPanel(new BorderLayout());
        initConsoleView();
        assert console != null;
        project.putUserData(COMMAND_LOG_KEY, this);
        root.add(console, BorderLayout.CENTER);
    }

    /**
     * Code to initialize the console window and its toolbar.
     */
    private void initConsoleView() {
        this.console = new ConsoleViewImpl(project, false);
        //
        final RunContentDescriptor descriptor = new RunContentDescriptor(console, null, root, TITLE);
        Disposer.register(this, descriptor);

        // must call getComponent before createConsoleActions()
        final JComponent consoleViewComponent = console.getComponent();

        // action like 'Clean All'
        final DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.addAll(console.createConsoleActions());

        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ID, actionGroup, false);
        toolbar.setTargetComponent(consoleViewComponent);

        final JComponent ui = descriptor.getComponent();
        ui.add(consoleViewComponent, BorderLayout.CENTER);
        ui.add(toolbar.getComponent(), BorderLayout.WEST);

        // Add a border to make things look nicer.
        consoleViewComponent.setBorder(BorderFactory.createEmptyBorder());
    }

    @Override
    public void dispose() {

    }

    @Override
    public ConsoleViewImpl getConsole() {
        return console;
    }

    @Override
    public void print(String msg, ConsoleViewContentType contentType) {
        console.print(msg, contentType);
    }
}
