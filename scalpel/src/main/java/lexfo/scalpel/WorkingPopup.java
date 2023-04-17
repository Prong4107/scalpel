package lexfo.scalpel;

import java.awt.*;
import javax.swing.*;

/**
	Provides a blocking wait dialog GUI popup.
*/
public class WorkingPopup {

	private static JDialog waitDialog;

	/**
		Shows a blocking wait dialog.

		@param task The task to run while the dialog is shown.
	*/
	public static void showBlockingWaitDialog(Runnable task) {
		JFrame parent = new JFrame();
		parent.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		JDialog dialog = new JDialog(parent, "Please wait...", true);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

		JLabel label = new JLabel("Processing...");
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
		dialog.add(label, BorderLayout.CENTER);

		dialog.setSize(200, 100);
		dialog.setLocationRelativeTo(parent);

		waitDialog = dialog;

		Thread taskThread = new Thread(() -> {
			try {
				task.run();
			} finally {
				SwingUtilities.invokeLater(() -> waitDialog.dispose());
			}
		});
		taskThread.start();

		SwingUtilities.invokeLater(() -> waitDialog.setVisible(true));
	}
}
