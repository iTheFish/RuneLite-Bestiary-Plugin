package com.bestiary.ui;

import com.bestiary.model.CapturedCreature;

import javax.swing.*;
import java.awt.*;

/**
 * MODELESS standalone "What were the odds?" dialog — a thin wrapper around {@link OddsView}
 * (which is also embedded in the {@link CardDataDialog} "Odds" tab).
 */
public class OddsDialog extends JDialog {

    private static OddsDialog current;

    public static void open(Window owner, CapturedCreature capture) {
        if (current != null && current.isShowing()) current.dispose();
        current = new OddsDialog(owner, capture);
        current.setVisible(true);
    }

    private OddsDialog(Window owner, CapturedCreature capture) {
        super(owner, "What were the odds?", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        OddsView root = new OddsView(capture);
        JScrollPane scroll = new JScrollPane(root,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        Dimension pref = root.getPreferredSize();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.max(360, Math.min(screen.width - 80, pref.width + 30));
        int h = Math.min(screen.height - 120, pref.height + 8);
        scroll.setPreferredSize(new Dimension(w, h));

        setContentPane(scroll);
        pack();
        setMinimumSize(new Dimension(340, 260));
        setLocationRelativeTo(owner);
    }
}
