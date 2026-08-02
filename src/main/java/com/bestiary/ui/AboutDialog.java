package com.bestiary.ui;

import com.bestiary.BestiaryPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * MODELESS "About Bestiary" dialog: the version log (what landed in each release, v1.0 being the
 * most extensive) and a thank-you note. Opened by clicking the version label in the panel footer.
 *
 * <p>The GitHub and Discord links are live; Patreon stays a disabled stub until it's set up.
 */
public class AboutDialog extends JDialog {

    private static final Color ORANGE = new Color(255, 165, 0);
    private static final String GITHUB_URL  = "https://github.com/iTheFish/RuneLite-Bestiary-Plugin";
    private static final String DISCORD_URL = "https://discord.gg/2HWSHH4mS5";
    private static AboutDialog current;

    public static void open(Window owner) {
        if (current != null && current.isShowing()) current.dispose();
        current = new AboutDialog(owner);
    }

    /** One release's entry in the version log. */
    private static final class Release {
        final String version, subtitle;
        final String[][] sections;   // { heading, "line 1\nline 2..." }
        Release(String version, String subtitle, String[][] sections) {
            this.version = version; this.subtitle = subtitle; this.sections = sections;
        }
    }

    // Newest first. Future releases get prepended above v1.0.
    private static final Release[] RELEASES = {
        new Release("v1.0", "First release", new String[][]{
            {"Capturing", "Every kill rolls a catch chance (by difficulty tier and your Capture Level), "
                + "then a weighted rarity from Common to Mythic — plus an independent shiny roll."},
            {"Cards & Power Level", "Each capture is a card with 7 rolled stats and the monster's "
                + "real Hitpoints, combat class and species. Its headline Power Level blends the stats "
                + "with HP and combat level, so bosses outclass trash mobs."},
            {"Album", "A searchable, filterable catalogue of 200+ monsters with per-monster detail pages, "
                + "paginated capture views and completion tracking by difficulty and species."},
            {"Collection & export", "Grouped, by-monster and individual views; favourites; rich detail "
                + "dialogs with per-stat bands and personal bests; and card export to a shareable image "
                + "with a unique ID and owner stamp."},
            {"Progression", "A Capture Level (1–99, with virtual levels beyond), XP from both kills and "
                + "captures, and 50+ achievements."},
            {"Economy & Shop", "Earn Bestiary Credits per capture and spend them on passive upgrades, the "
                + "Card Reroller (re-roll a card's stats and shiny), or discard duplicates for credits."},
            {"Multiple accounts", "Each account keeps its own collection; browse any known account "
                + "read-only, and transfer cards between your own accounts."},
            {"Dashboards & recap", "Shareable Progression, Economy, Species and Caught dashboards, plus a "
                + "per-session recap of kills, XP, credits and captures."},
            {"Overlay & alerts", "An on-screen capture animation, level-up banners, and configurable "
                + "chat notifications."},
        }),
    };

    private AboutDialog(Window owner) {
        super(owner, "About Bestiary", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildLogScroll(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(root);
        setSize(new Dimension(420, 560));
        setMinimumSize(new Dimension(360, 380));
        setLocationRelativeTo(owner);
        setVisible(true);
        toFront();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 4, 0, 0, ORANGE), new EmptyBorder(12, 14, 12, 14)));

        JLabel title = new JLabel("Bestiary  " + BestiaryPlugin.VERSION);
        title.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
        title.setForeground(ORANGE);
        title.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);

        JLabel tagline = new JLabel("A card-collection layer for Old School RuneScape.");
        tagline.setFont(FontManager.getRunescapeSmallFont());
        tagline.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        tagline.setAlignmentX(LEFT_ALIGNMENT);
        header.add(Box.createVerticalStrut(3));
        header.add(tagline);
        return header;
    }

    private JScrollPane buildLogScroll() {
        JPanel log = new JPanel();
        log.setLayout(new BoxLayout(log, BoxLayout.Y_AXIS));
        log.setBackground(ColorScheme.DARK_GRAY_COLOR);
        log.setBorder(new EmptyBorder(10, 14, 10, 14));

        for (Release r : RELEASES) {
            JLabel ver = new JLabel(r.version + "  ·  " + r.subtitle);
            ver.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
            ver.setForeground(Color.WHITE);
            ver.setAlignmentX(LEFT_ALIGNMENT);
            ver.setBorder(new EmptyBorder(4, 0, 6, 0));
            log.add(ver);

            for (String[] sec : r.sections) {
                JLabel head = new JLabel("• " + sec[0]);
                head.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
                head.setForeground(ORANGE);
                head.setAlignmentX(LEFT_ALIGNMENT);
                head.setBorder(new EmptyBorder(4, 0, 1, 0));
                log.add(head);

                JTextArea body = new JTextArea(sec[1]);
                body.setEditable(false);
                body.setLineWrap(true);
                body.setWrapStyleWord(true);
                body.setFocusable(false);
                body.setFont(FontManager.getRunescapeSmallFont());
                body.setForeground(new Color(200, 200, 200));
                body.setBackground(ColorScheme.DARK_GRAY_COLOR);
                body.setBorder(new EmptyBorder(0, 10, 0, 0));
                body.setAlignmentX(LEFT_ALIGNMENT);
                log.add(body);
            }
        }

        JScrollPane sp = new JScrollPane(log,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        footer.setBorder(new EmptyBorder(10, 14, 12, 14));

        JTextArea thanks = new JTextArea("Thank you for installing Bestiary — I hope it makes every "
                + "kill a little more exciting. Happy hunting! - Fish");
        thanks.setEditable(false);
        thanks.setLineWrap(true);
        thanks.setWrapStyleWord(true);
        thanks.setFocusable(false);
        thanks.setFont(FontManager.getRunescapeSmallFont());
        thanks.setForeground(new Color(255, 210, 120));
        thanks.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        thanks.setAlignmentX(LEFT_ALIGNMENT);
        footer.add(thanks);

        // Community links — GitHub & Discord are live; Patreon stubbed until it exists.
        JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        links.setOpaque(false);
        links.setAlignmentX(LEFT_ALIGNMENT);

        JButton github = linkButton("GitHub");
        github.setToolTipText(GITHUB_URL);
        github.addActionListener(e -> LinkBrowser.browse(GITHUB_URL));
        links.add(github);

        JButton discord = linkButton("Discord");
        discord.setToolTipText(DISCORD_URL);
        discord.addActionListener(e -> LinkBrowser.browse(DISCORD_URL));
        links.add(discord);

        JButton patreon = linkButton("Patreon");
        patreon.setEnabled(false);
        patreon.setToolTipText("Coming soon");
        links.add(patreon);

        footer.add(Box.createVerticalStrut(8));
        footer.add(links);

        JLabel soon = new JLabel("Patreon coming soon.");
        soon.setFont(FontManager.getRunescapeSmallFont());
        soon.setForeground(new Color(120, 120, 120));
        soon.setAlignmentX(LEFT_ALIGNMENT);
        footer.add(Box.createVerticalStrut(4));
        footer.add(soon);
        return footer;
    }

    private static JButton linkButton(String name) {
        JButton b = new JButton(name);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setFocusPainted(false);
        return b;
    }
}
