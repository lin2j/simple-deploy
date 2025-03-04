package tech.lin2j.idea.plugin.file;

import com.google.common.util.concurrent.AtomicDouble;
import com.intellij.openapi.util.text.StringUtil;
import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.xfer.TransferListener;
import tech.lin2j.idea.plugin.ssh.CommandLog;
import tech.lin2j.idea.plugin.ui.ftp.ProgressTable;
import tech.lin2j.idea.plugin.ui.table.ProgressCell;
import tech.lin2j.idea.plugin.uitl.FileTransferSpeed;

import javax.swing.table.DefaultTableModel;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * @author linjinjia
 * @date 2024/4/13 16:09
 */
public class ProgressTableTransferListener implements TransferListener {
    private final String relPath;
    private final CommandLog console;
    private final ProgressCell progressCell;

    private final FileTransferSpeed fileTransferSpeed = new FileTransferSpeed();

    public ProgressTableTransferListener(String relPath, ProgressCell cell, CommandLog console) {
        if (!relPath.endsWith("/")) {
            relPath += "/";
        }
        this.relPath = relPath;
        this.progressCell = cell;
        this.console = console;
    }

    @Override
    public TransferListener directory(String name) {
        return new ProgressTableTransferListener(relPath + name + "/", progressCell, console);
    }

    @Override
    public StreamCopier.Listener file(final String name, final long size) {
        final String path = relPath + name;
        console.info("Transfer file: " + path + ", Size: " + StringUtil.formatFileSize(size) + "\n");
        AtomicDouble prePercent = new AtomicDouble(0);
        AtomicLong preTransferred = new AtomicLong();
        return transferred -> {
            String speed = fileTransferSpeed.accept(transferred);

            long finalSize = size;
            long finalTransferred = transferred;

            if (progressCell.isDirectoryRow()) {
                finalSize = progressCell.getDirectorySize();
                long len = transferred - preTransferred.get();
                progressCell.addTransferred(len);
                finalTransferred = progressCell.getTransferred();
                preTransferred.set(transferred);
            }

            // update progress cell of output table
            double percent = 0;
            if (finalSize > 0) {
                percent = finalTransferred / (double) finalSize;
            }
            boolean completed = Math.abs(1 - percent) < 1e-6;
            boolean step = Math.abs(percent - prePercent.get() - 0.01) > 1e-6;
            if (step || completed) {
                DefaultTableModel tableModel = (DefaultTableModel) progressCell.getTableModel();
                int row = progressCell.getRow();
                prePercent.set(percent);
                progressCell.getColorProgressBar().setFraction(percent);
                tableModel.fireTableCellUpdated(row, ProgressTable.PROGRESS_COL);
                // speed
                tableModel.setValueAt(speed, row, ProgressTable.SPEED_COL);
            }

            // update log progress of log panel
            double fileProgress = 0;
            if (size > 0) {
                fileProgress = transferred / (double) size;
            }
            boolean fileCompleted = Math.abs(1 - fileProgress) < 1e-6;
            printProgress((int) (fileProgress * 100), fileCompleted, speed);

        };
    }

    private void printProgress(int complete, boolean completed, String speed) {
        StringBuilder sb = new StringBuilder("[");
        Stream.generate(() -> '#').limit(complete).forEach(sb::append);
        Stream.generate(() -> '_').limit(100 - complete).forEach(sb::append);
        sb.append("] ");
        if (completed) {
            sb.append("complete, speed: ").append(speed).append("\n");
        } else {
            sb.append(complete).append("% , speed: ").append(speed);
        }
        console.print("\r");
        console.print(sb.toString());
    }
}