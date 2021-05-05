package net.preibisch.aws.gui;

import com.bigdistributor.aws.spimloader.AWSSpimLoader;
import com.bigdistributor.aws.ng.NeuroglancerInput;
import com.bigdistributor.aws.ng.NeuroglancerWebViewer;
import fiji.util.gui.GenericDialogPlus;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URISyntaxException;

public class NeuroglancerPopup extends JMenuItem implements ExplorerWindowSetable {
    private static final long serialVersionUID = 5234649267634013390L;
    public static boolean showWarning = true;
    ExplorerWindow<? extends AbstractSpimData<? extends AbstractSequenceDescription<?, ?, ?>>, ?> panel;

    public NeuroglancerPopup() {
        super("Show data in neuroglancer");
        this.addActionListener(new NeuroglancerPopup.MyActionListener());
    }

    public JMenuItem setExplorerWindow(ExplorerWindow<? extends AbstractSpimData<? extends AbstractSequenceDescription<?, ?, ?>>, ?> panel) {
        this.panel = panel;
        return this;
    }

    public class MyActionListener implements ActionListener {
        public MyActionListener() {
        }

        public void actionPerformed(ActionEvent e) {
            if (NeuroglancerPopup.this.panel == null) {
                IOFunctions.println("Panel not set for " + this.getClass().getSimpleName());
            } else if (!SpimData2.class.isInstance(NeuroglancerPopup.this.panel.getSpimData())) {
                IOFunctions.println("Only supported for SpimData2 objects: " + this.getClass().getSimpleName());
            }else{
                String url = AWSSpimLoader.get().getN5uri();
                final GenericDialogPlus gd = new GenericDialogPlus("AWS Input");
                gd.addFileField("View path: ", "setup0/timepoint0/s0", 45);
                gd.showDialog();

                if (gd.wasCanceled())
                    return;

                String setupPath = gd.getNextString();

                try {
                    new NeuroglancerWebViewer( new NeuroglancerInput(url, setupPath)).openInBrowser();
                } catch (URISyntaxException | IOException uriSyntaxException) {
                    uriSyntaxException.printStackTrace();
                    return;
                }
            }
        }
    }
}
