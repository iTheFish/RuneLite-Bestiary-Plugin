package com.bestiary.ui;

import com.bestiary.model.BestiaryCollection;
import com.bestiary.model.CreatureRarity;
import com.bestiary.service.BestiaryDataService;
import com.bestiary.service.ProgressionService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The Info tab: a persistent header (live stat boxes + shortcut buttons) over a set of
 * category sub-tabs. Each sub-tab swaps the scrollable content below it (invisible
 * scrollbar), so the reference material reads as tidy sections instead of one long
 * uncategorised wall of text.
 */
public class InfoTab extends JPanel {

    private static final Color ORANGE = new Color(255, 165, 0);
    private static final NumberFormat FMT = NumberFormat.getNumberInstance(Locale.UK);

    private final BestiaryDataService dataService;
    private final ProgressionService  progressionService;
    private final Consumer<DashboardDialog.DashView> openDashboard;
    private final Consumer<DashboardDialog.DashView> exportDashboard;

    // Live stat labels
    private final JLabel speciesVal  = statValue("0");
    private final JLabel capturesVal = statValue("0");
    private final JLabel levelVal    = statValue("1");
    private final JLabel killsVal    = statValue("0");

    // Category sub-tabs
    private final JPanel contentCards = new JPanel(new CardLayout());
    private final List<JToggleButton> catButtons = new ArrayList<>();

    // Header controls that act on the collection — disabled while logged out (the category
    // sub-tabs stay live so the guide/reference is always browsable). The stat boxes and the
    // shortcut buttons share ONE GridBag so every accent lines up to the pixel (see below).
    private JPanel headerControls;
    private final List<JPanel> statBoxes = new ArrayList<>();
    private boolean interactiveEnabled = true;

    /** Wipes the played collection (double-confirmed by the panel). Lives at the bottom of Progress. */
    private final Runnable onReset;
    private JButton resetBtn;

    public InfoTab(BestiaryDataService dataService, ProgressionService progressionService,
                   Runnable openAlbum, Runnable openFavourites, Runnable openRecap,
                   Runnable openCatchRates,
                   Consumer<DashboardDialog.DashView> openDashboard,
                   Consumer<DashboardDialog.DashView> exportDashboard,
                   Runnable onReset) {
        this.onReset            = onReset;
        this.dataService        = dataService;
        this.progressionService = progressionService;
        this.openDashboard      = openDashboard;
        this.exportDashboard    = exportDashboard;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Persistent header: live stats + shortcuts + the category bar
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(new EmptyBorder(6, 6, 4, 6));
        headerControls = buildStatsAndShortcuts(openAlbum, openFavourites, openRecap, openCatchRates);
        header.add(headerControls);
        header.add(Box.createVerticalStrut(8));
        header.add(headerDivider());
        header.add(Box.createVerticalStrut(6));
        header.add(buildSubTabBar());
        add(header, BorderLayout.NORTH);

        // One scrollable card per category
        contentCards.setBackground(ColorScheme.DARK_GRAY_COLOR);
        addCategory(0, this::fillGuide);
        addCategory(1, this::fillCapturing);
        addCategory(2, this::fillCards);
        addCategory(3, this::fillEconomy);
        addCategory(4, this::fillProgress);
        addCategory(5, this::fillAlerts);
        add(contentCards, BorderLayout.CENTER);

        selectCategory(0);
        refresh();
    }

    public void refresh() {
        BestiaryCollection col = dataService.getCollection();
        speciesVal.setText(String.valueOf(col.uniqueSpeciesCount()));
        // "Caught" = lifetime captures (never drops on discard/transfer); held cards show in the header.
        capturesVal.setText(FMT.format(col.lifetimeCaptures));
        levelVal.setText(String.valueOf(dataService.getDisplayLevel()));
        killsVal.setText(FMT.format(col.totalKills()));
    }

    /**
     * Enables/disables the header controls that act on the collection (the clickable stat boxes and
     * the shortcut buttons). The category sub-tabs are left alone so the guide/reference stays
     * browsable while logged out.
     */
    public void setInteractiveEnabled(boolean enabled) {
        interactiveEnabled = enabled;
        // The stat boxes and the shortcut buttons now live in one panel; disable the buttons
        // recursively and grey the (non-button) stat boxes via their tracked references.
        setButtonsEnabled(headerControls, enabled);
        for (JPanel box : statBoxes) {
            box.setEnabled(enabled);
            box.setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
    }

    /**
     * View-mode gating (#48): while browsing another account, Album / Catch Rates / stat-box dashboards
     * stay usable (they reflect the viewed account), but Session Recap and Favourites are disabled —
     * they're about YOUR play/collection, not the viewed one. Call after {@link #setInteractiveEnabled}.
     */
    public void setViewingAnotherAccount(boolean viewing) {
        // "Your play" shortcuts are live only when playing your own account: not while logged out
        // (interactiveEnabled false) and not while viewing someone else's collection.
        boolean enabled = interactiveEnabled && !viewing;
        if (favouritesBtn != null) favouritesBtn.setEnabled(enabled);
        if (recapBtn != null)      recapBtn.setEnabled(enabled);
    }

    /** Recursively enables/disables every button under {@code root} (leaves other components alone). */
    private static void setButtonsEnabled(Container root, boolean enabled) {
        for (Component c : root.getComponents()) {
            if (c instanceof AbstractButton) {
                c.setEnabled(enabled);
            } else if (c instanceof Container) {
                setButtonsEnabled((Container) c, enabled);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Category sub-tabs
    // -------------------------------------------------------------------------

    private static final String[] CATEGORIES = {"Guide", "Capturing", "Cards", "Economy", "Progress", "Alerts"};

    private JPanel buildSubTabBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setOpaque(false);
        bar.setAlignmentX(LEFT_ALIGNMENT);

        // 3 on top, 3 below — keeps labels readable in the narrow side panel.
        JPanel row1 = new JPanel(new GridLayout(1, 3, 4, 0));
        JPanel row2 = new JPanel(new GridLayout(1, 3, 4, 0));
        row1.setOpaque(false); row2.setOpaque(false);
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        row1.setAlignmentX(LEFT_ALIGNMENT); row2.setAlignmentX(LEFT_ALIGNMENT);

        for (int i = 0; i < CATEGORIES.length; i++) {
            final int idx = i;
            JToggleButton b = new JToggleButton(CATEGORIES[i]);
            b.setFont(FontManager.getRunescapeSmallFont());
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setMargin(new Insets(2, 2, 2, 2));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> selectCategory(idx));
            styleTab(b, false);
            catButtons.add(b);
            (i < 3 ? row1 : row2).add(b);
        }

        bar.add(row1);
        bar.add(Box.createVerticalStrut(4));
        bar.add(row2);
        return bar;
    }

    private void selectCategory(int idx) {
        ((CardLayout) contentCards.getLayout()).show(contentCards, "cat" + idx);
        for (int i = 0; i < catButtons.size(); i++) {
            styleTab(catButtons.get(i), i == idx);
            catButtons.get(i).setSelected(i == idx);
        }
    }

    /** Orange rule separating the header block from the category tabs. */
    private static JComponent headerDivider() {
        JSeparator s = new JSeparator();
        s.setForeground(new Color(255, 165, 0, 90));
        s.setBackground(ColorScheme.DARK_GRAY_COLOR);
        s.setAlignmentX(LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        return s;
    }

    private static void styleTab(JToggleButton b, boolean active) {
        b.setOpaque(true);
        b.setBackground(active ? ORANGE : ColorScheme.DARKER_GRAY_COLOR);
        b.setForeground(active ? new Color(30, 30, 30) : ColorScheme.LIGHT_GRAY_COLOR);
    }

    /** Builds a scrollable (invisible scrollbar) content card and registers it under "cat{idx}". */
    private void addCategory(int idx, Consumer<JPanel> fill) {
        JPanel content = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                // Track the viewport width so JTextArea tiles wrap at the panel edge.
                Dimension d = super.getPreferredSize();
                if (getParent() != null) d.width = getParent().getWidth();
                return d;
            }
        };
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(6, 6, 8, 6));
        fill.accept(content);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));
        contentCards.add(scroll, "cat" + idx);
    }

    // -------------------------------------------------------------------------
    // Category content
    // -------------------------------------------------------------------------

    private void fillGuide(JPanel c) {
        c.add(sectionTitle("Your guide to Bestiary"));
        c.add(tile("The short version",
                "Every kill of a roster monster is a chance to capture it as a card, with rolled " +
                "stats, a rarity and a small chance to be shiny. Catch them, level up and fill your album."));
        c.add(tile("Getting started",
                "Nothing to set up, just fight. New catches land in the Cards tab, and you earn XP " +
                "and credits as you go. Overlay, animation and chat options are under Bestiary in " +
                "RuneLite's Config panel.\n\n" +
                "Open the Album to browse your cards. Left-click a card to export it; right-click " +
                "for everything else (favourite, rename, reroll, discard)."));
        c.add(tile("Early levels",
                "Beginner and Easy monsters have the best catch rates, so start there. Hunter's " +
                "Bounty and Salvager's Eye are cheap and boost your credit income, so buy them early. " +
                "Tougher monsters give more XP, even when you miss the catch."));
        c.add(tile("Mid game",
                "Discard duplicates for credits (right-click, or bulk from the Album). Shinies are " +
                "worth a flat +500 when discarded. By level 50 catch rates are much healthier. Click " +
                "a stat box above to see your dashboards."));
        c.add(tile("Late game",
                "Spend credits on the Card Reroller to chase better stats, shinies and rarity " +
                "rank-ups, and save up for the bigger Mechanics unlocks like Fortune's Favour and " +
                "Keen Instinct."));
        c.add(tile("End game",
                "Level 92 is only halfway to 99 in XP. At 99 the Level 99 shop unlocks, and virtual " +
                "levels carry on to 126 (200M XP). Top rarities and shinies stay genuinely rare, so a " +
                "full album is a real flex."));
    }

    private void fillCapturing(JPanel c) {
        c.add(buildRarityTable());
        JPanel catchHint = noteArea("Click 'Catch Rates' above to see your current chances.",
                new Color(205, 205, 205));
        catchHint.setBorder(new EmptyBorder(3, 11, 0, 0));
        c.add(catchHint);
        c.add(Box.createVerticalStrut(8));
        c.add(sectionTitle("How capturing works"));
        c.add(tile("Catch rate",
                "Each kill rolls a capture. The chance depends on the monster's difficulty and your " +
                "Capture Level (level 1 → 99):\n" +
                "• Beginner 25% → 70%\n" +
                "• Easy 20% → 65%\n" +
                "• Medium 15% → 55%\n" +
                "• Hard 10% → 50%\n" +
                "• Elite 5% → 35%\n" +
                "• Boss 3% → 25%\n\n" +
                "Only roster monsters are tracked."));
        c.add(tile("Rarity",
                "A successful capture then rolls its rarity. Each level shifts the odds toward rarer " +
                "results: Mythic goes from 0.1% at level 1 to about 1.5% at 99."));
        c.add(tile("Shiny",
                "A separate roll, so any rarity can be shiny: 0.2% at level 1, rising to 2% at 99 " +
                "(Shiny Charm adds up to +0.5%). Shinies roll near-max stats, get a golden card and " +
                "are always announced in chat."));
        c.add(tile("Shop boosts",
                "Two Mechanics unlocks from the Shop add extra rolls on top:\n" +
                "• Fortune's Favour: a chance for a capture to climb one rarity after it lands.\n" +
                "• Keen Instinct: a chance to roll the capture twice and keep the better result, " +
                "which can even turn a miss into a catch.\n\n" +
                "Once you own them, your chances show in Catch Rates."));
    }

    private void fillCards(JPanel c) {
        c.add(sectionTitle("Reading a card"));
        c.add(tile("Power Level",
                "A card's headline number:\n" +
                "average of the 7 stats + HP ÷ 6 + combat level ÷ 6\n\n" +
                "HP and combat level do most of the work, so a boss scores far higher than a perfect " +
                "goblin. The rolled stats are mostly flavour."));
        c.add(tile("Stats",
                "Attack, Strength, Defence, Magic, Ranged, Agility and Prayer (the last two on a " +
                "smaller scale). Each rolls from the monster's own base value, then rarity lifts it " +
                "toward 99. The ranges overlap, so a lucky Rare can beat an unlucky Epic."));
        c.add(tile("Album",
                "Every capturable monster in one grid. Search or filter by difficulty, or click a " +
                "monster to see all your copies, with sorting, rarity filters and pages."));
        c.add(tile("Card Info",
                "Left-click a card (or right-click → Card info + export) for its details: Overview, " +
                "Odds, a stat Graph and its Reroll history, plus the export options."));
        c.add(tile("Album cover & nicknames",
                "Right-click a card → Set as album cover to choose which card represents that " +
                "monster in the Album. Right-click → Name capture to give a card a nickname (up to " +
                "20 characters)."));
        c.add(tile("Favourites",
                "Right-click a card → Add to Favourites (up to 20). Starred cards appear under " +
                "★ Favourites here, in the Cards tab and in the Album."));
        c.add(tile("Export",
                "Left-click a card (or right-click → Card info + export), then Copy Image or Save " +
                "PNG. Right-click → Copy is a quick clipboard copy, and the Album's Export Page " +
                "saves a whole page as a grid."));
    }

    private void fillEconomy(JPanel c) {
        c.add(sectionTitle("Credits & the shop"));
        c.add(tile("Bestiary Credits",
                "Earned on every capture, scaled by difficulty × rarity (shiny doubles it): about 2 " +
                "for a Beginner Common, up to ~480 for a Boss Mythic. Level-ups pay level × 10, and " +
                "achievements pay one-off rewards. Lifetime totals are on the Economy dashboard."));
        c.add(tile("Card Reroller",
                "Right-click a card → Reroll to re-roll its stats and shiny at the same rarity. Costs " +
                "20 (Beginner Common) up to 1,200 (Boss Mythic); Haggler takes up to 20% off.\n\n" +
                "Non-Mythic cards have a 5% chance to rank up a rarity. Shinies stay shiny, and the " +
                "favourite, nickname and album cover are kept."));
        c.add(tile("Discard",
                "Right-click → Discard to trade a card for its base capture value (shinies +500). " +
                "Discarding is permanent.\n\n" +
                "For bulk clear-outs, use Discard duplicates in the Album: it keeps your best copy of " +
                "each monster and rarity, and can protect favourites, album covers and shinies."));
        c.add(tile("Shop unlocks",
                "Permanent upgrades bought with credits. Each shows your current and next-tier " +
                "bonus before you buy.\n\n" +
                "Progression\n" +
                "• Hunter's Bounty: +2 credits per capture per tier (max +10)\n" +
                "• Salvager's Eye: +2% discard credits per tier (max +10%)\n" +
                "• Hunter's Focus: +5 kill XP per tier (max +25)\n" +
                "• Scholar's Insight: +5% capture XP per tier (max +25%)\n\n" +
                "Mechanics\n" +
                "• Fortune's Favour: +1% per tier that a capture rolls one rarity higher (max 5%)\n" +
                "• Keen Instinct: +2% per tier to roll a capture twice and keep the better result, " +
                "even turning a miss into a catch (max 10%)\n" +
                "• Shiny Charm: +0.1% shiny chance per tier (max +0.5%)\n\n" +
                "Rerolls\n" +
                "• Reroll Shine: +0.1% reroll shiny chance per tier\n" +
                "• Reroll Fortune: +1% reroll rank-up chance per tier\n" +
                "• Haggler: -4% reroll cost per tier (max -20%)\n\n" +
                "Tier 1 of Fortune's Favour and Keen Instinct costs the most, because it's what " +
                "unlocks the roll."));
        c.add(tile("Level 99 shop",
                "Unlocks at Capture Level 99. Find out what's inside when you get there!"));
        c.add(tile("No real-world value",
                "Bestiary is a free, fan-made minigame, and it's all just for fun. Bestiary Credits, " +
                "cards, rarities, shinies and Power Levels live entirely inside this plugin: they have " +
                "no real-world or in-game value, can't be bought, sold or traded for real money, " +
                "RuneScape GP or items, and give no advantage in Old School RuneScape.\n\n" +
                "Bestiary isn't affiliated with or endorsed by Jagex. Old School RuneScape is a trademark " +
                "of Jagex Ltd; all monster names and artwork belong to Jagex and the OSRS Wiki."));
    }

    private void fillProgress(JPanel c) {
        c.add(sectionTitle("Progress & stats"));
        c.add(tile("XP & levels",
                "Capture Level runs 1–99, then virtual levels to 126.\n\n" +
                "Kill XP by difficulty: Beginner 5, Easy 10, Medium 15, Hard 20, Elite 25, Boss 30.\n\n" +
                "Capture XP = combat level × 10 (min 10, capped at combat 100) × rarity: Common 1×, " +
                "Uncommon 2×, Rare 5×, Epic 10×, Legendary 25×, Mythic 50×. A Rare catch of a " +
                "level-50 monster = 2,500 XP."));
        c.add(tile("Achievements",
                "The Progress tab lists every achievement; hover one to see its credit reward. Tick " +
                "'Hide complete' to see only what's left."));
        c.add(tile("Dashboards",
                "Click a stat box at the top for its dashboard (Progression, Economy, Species, " +
                "Caught). Right-click one to copy it as an image."));
        c.add(tile("Multiple accounts",
                "Each account keeps its own collection. Use the dropdown at the top of the panel to " +
                "view another account's collection (read-only). Transfer cards in the Album moves " +
                "cards between your own accounts."));
        c.add(tile("Session Recap",
                "Lists every capture since you logged in. 'Copy Summary' pastes cleanly into Discord."));
        c.add(Box.createVerticalStrut(10));
        c.add(tile("Reset Progress & Collection",
                "Permanently deletes all captures, kill counts, XP, levels and achievements for the " +
                "account you're logged in on. You'll be asked to confirm twice."));
        resetBtn = new JButton("Reset Progress & Collection?");
        resetBtn.setFont(FontManager.getRunescapeSmallFont());
        resetBtn.setBackground(new Color(80, 20, 20));
        resetBtn.setForeground(new Color(220, 100, 100));
        resetBtn.setBorderPainted(false);
        resetBtn.setFocusPainted(false);
        resetBtn.setToolTipText("Permanently delete all captures and progression");
        resetBtn.setAlignmentX(LEFT_ALIGNMENT);
        resetBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        resetBtn.addActionListener(e -> { if (onReset != null) onReset.run(); });
        c.add(resetBtn);
    }

    /** Reset acts on the PLAYED account — only enabled when logged in and not viewing another account. */
    public void setResetEnabled(boolean enabled) {
        if (resetBtn != null) resetBtn.setEnabled(enabled);
    }

    private void fillAlerts(JPanel c) {
        c.add(sectionTitle("Notifications"));
        c.add(tile("Capture overlay",
                "A notification appears on each capture; set its position and width in Config. The " +
                "optional collection-jar animation plays on every kill attempt, and rapid kills queue " +
                "so none are skipped."));
        c.add(tile("Chat notifications",
                "Verbose: one message per capture.\n" +
                "Batched: repeat captures of the same monster and rarity are grouped into one message " +
                "after 9 seconds of quiet.\n\n" +
                "Shinies always announce straight away."));
        c.add(tile("Discord alerts",
                "Paste a Discord channel webhook URL into 'Discord Webhook' in Config to post a card " +
                "image whenever you catch a Legendary or better, or a shiny Epic or better. Leave it " +
                "blank to turn it off. Only the card image and capture details are sent."));
        c.add(tile("Level-up alerts",
                "A gold banner plays when you level up, plus a chat message you can turn off with " +
                "'Notify On Level Up' in Config."));
    }

    // -------------------------------------------------------------------------
    // Live stats strip  (4 boxes in one row)
    // -------------------------------------------------------------------------

    /**
     * Builds the whole header control block — the 2×2 stat boxes and the three shortcut rows — in ONE
     * 2-column {@link GridBagLayout}. Because the stat boxes and the buttons share the same grid, the
     * full-width buttons (Open Album, Session Recap span both columns) end at the exact same pixel as
     * the right-hand stat boxes (Kills, Caught), and Catch Rates lines up with them too — on any panel
     * width. (Two separate panels drift by a pixel because each rounds its column split independently.)
     */
    private JPanel buildStatsAndShortcuts(Runnable openAlbum, Runnable openFavourites,
                                          Runnable openRecap, Runnable openCatchRates) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weightx = 1;

        // --- Stat boxes (rows 0–1). weighty pulls them to equal, taller box height. ---
        g.weighty = 1;
        JPanel level = clickable(statBox("Level", levelVal, false), DashboardDialog.DashView.PROGRESSION);
        g.gridx = 0; g.gridy = 0; g.insets = new Insets(0, 0, 2, 2); panel.add(level, g);
        JPanel kills = clickable(statBox("Kills", killsVal, true), DashboardDialog.DashView.PROGRESSION);
        g.gridx = 1; g.insets = new Insets(0, 2, 2, 0); panel.add(kills, g);
        JPanel species = clickable(statBox("Species", speciesVal, false), DashboardDialog.DashView.SPECIES);
        g.gridx = 0; g.gridy = 1; g.insets = new Insets(2, 0, 6, 2); panel.add(species, g);
        JPanel caught = clickable(statBox("Caught", capturesVal, true), DashboardDialog.DashView.CAUGHT);
        g.gridx = 1; g.insets = new Insets(2, 2, 6, 0); panel.add(caught, g);
        statBoxes.add(level); statBoxes.add(kills); statBoxes.add(species); statBoxes.add(caught);

        // --- Shortcut buttons (rows 2–4). weighty 0 keeps them at natural button height. ---
        g.weighty = 0;
        // Full-width Open Album (spans both columns)
        g.gridx = 0; g.gridy = 2; g.gridwidth = 2; g.insets = new Insets(0, 0, 4, 0);
        panel.add(blockBtn("Open Album", ORANGE, openAlbum, true), g);

        // Favourites + Catch Rates (one column each; 4px gap split as 2px per side)
        g.gridwidth = 1; g.gridy = 3;
        g.gridx = 0; g.insets = new Insets(0, 0, 4, 2);
        favouritesBtn = blockBtn("★ Favourites", new Color(220, 180, 60), openFavourites);
        panel.add(favouritesBtn, g);

        JButton catchBtn = blockBtn(" Catch Rates", new Color(100, 180, 220), openCatchRates, true);
        final int iD = 13;
        catchBtn.setIcon(new Icon() {
            @Override public int getIconWidth()  { return iD; }
            @Override public int getIconHeight() { return iD; }
            @Override public void paintIcon(Component c, Graphics g2raw, int x, int y) {
                Graphics2D g2 = (Graphics2D) g2raw.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillOval(x, y, iD, iD);
                g2.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(new Color(30, 30, 30));
                String ch = "i";
                g2.drawString(ch, x + (iD - fm.stringWidth(ch)) / 2,
                        y + (iD + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        });
        catchBtn.setIconTextGap(3);
        g.gridx = 1; g.insets = new Insets(0, 2, 4, 0);
        panel.add(catchBtn, g);

        // Full-width Session Recap (spans both columns)
        g.gridx = 0; g.gridy = 4; g.gridwidth = 2; g.insets = new Insets(0, 0, 0, 0);
        recapBtn = blockBtn("Session Recap", new Color(120, 200, 120), openRecap, true);
        panel.add(recapBtn, g);

        return panel;
    }

    private JPanel clickable(JPanel panel, DashboardDialog.DashView view) {
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        panel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!interactiveEnabled) return;   // inert while logged out
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (openDashboard != null) openDashboard.accept(view);
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem open = new JMenuItem("Open Dashboard: " + view.label);
                    open.addActionListener(ev -> { if (openDashboard != null) openDashboard.accept(view); });
                    JMenuItem copy = new JMenuItem("Copy " + view.label + " Card");
                    copy.addActionListener(ev -> { if (exportDashboard != null) exportDashboard.accept(view); });
                    menu.add(open);
                    menu.add(copy);
                    menu.show(panel, e.getX(), e.getY());
                }
            }
        });
        return panel;
    }

    private static JPanel statBox(String labelText, JLabel valueLabel, boolean rightAccent) {
        JPanel box = new JPanel(new GridLayout(2, 1, 0, 2));
        box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        box.setBorder(BorderFactory.createCompoundBorder(
                rightAccent ? new MatteBorder(0, 3, 0, 3, ORANGE)
                            : new MatteBorder(0, 3, 0, 0, ORANGE),
                new EmptyBorder(8, 6, 6, 6)));

        JLabel label = new JLabel(labelText, SwingConstants.CENTER);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        box.add(valueLabel);
        box.add(label);
        return box;
    }

    private static JLabel statValue(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(FontManager.getRunescapeBoldFont());
        l.setForeground(ORANGE);
        return l;
    }

    /** Shortcuts disabled while viewing another account (about YOUR play, not the viewed collection). */
    private JButton favouritesBtn;
    private JButton recapBtn;

    /** A chunky, header-style shortcut button (orange left accent, like the stat boxes). */
    private static JButton blockBtn(String text, Color fg, Runnable action) {
        return blockBtn(text, fg, action, false);
    }

    /** Header-style shortcut button; {@code bothAccent} adds an orange bar on both sides like the stat boxes. */
    private static JButton blockBtn(String text, Color fg, Runnable action, boolean bothAccent) {
        JButton btn = new JButton(text);
        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, bothAccent ? 3 : 0, ORANGE),
                new EmptyBorder(4, 6, 4, 6)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> action.run());
        return btn;
    }

    // -------------------------------------------------------------------------
    // Rarity quick-reference table
    // -------------------------------------------------------------------------

    private JPanel buildRarityTable() {
        JPanel outer = new JPanel(new BorderLayout(0, 4));
        outer.setOpaque(false);
        outer.setAlignmentX(LEFT_ALIGNMENT);
        outer.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 3, 0, 0, ORANGE),
                new EmptyBorder(3, 8, 3, 0)));

        JLabel title = new JLabel("Rarity Tiers");
        title.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        title.setForeground(ORANGE);

        JTextArea subtitle = new JTextArea("Catch chance improves with your Capture Level.");
        subtitle.setFont(FontManager.getRunescapeSmallFont());
        subtitle.setForeground(new Color(190, 190, 190));
        subtitle.setBackground(ColorScheme.DARK_GRAY_COLOR);
        subtitle.setOpaque(false);
        subtitle.setEditable(false);
        subtitle.setFocusable(false);
        subtitle.setLineWrap(true);
        subtitle.setWrapStyleWord(true);

        JPanel titleBlock = new JPanel(new BorderLayout(0, 1));
        titleBlock.setOpaque(false);
        titleBlock.add(title,    BorderLayout.NORTH);
        titleBlock.add(subtitle, BorderLayout.CENTER);

        // Pre-compute level-99 normalised percentages
        // Multipliers mirror RarityRoller: COMMON 0.50, UNCOMMON 1.30, RARE 2.00,
        // EPIC 4.00, LEGENDARY 8.00, MYTHIC 12.0
        double[] mult99 = {0.50, 1.30, 2.00, 4.00, 8.00, 12.0};
        CreatureRarity[] rarities = CreatureRarity.values();
        double total99 = 0.0;
        double[] w99 = new double[rarities.length];
        for (int i = 0; i < rarities.length; i++) {
            w99[i] = rarities[i].probability * mult99[i];
            total99 += w99[i];
        }

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);

        rows.add(tableRow("Rarity", "Lv 1", "Lv 99", new Color(200, 200, 200)));

        for (int i = 0; i < rarities.length; i++) {
            CreatureRarity r = rarities[i];
            double pct1  = r.probability * 100;
            double pct99 = w99[i] / total99 * 100;
            String s1  = pct1  >= 10.0 ? String.format("%.0f%%", pct1)  : String.format("%.1f%%", pct1);
            String s99 = pct99 >= 10.0 ? String.format("%.0f%%", pct99) : String.format("%.1f%%", pct99);
            rows.add(tableRow("● " + r.label, s1, s99, r.displayColor));
        }

        outer.add(titleBlock, BorderLayout.NORTH);
        outer.add(rows,       BorderLayout.CENTER);
        return outer;
    }

    private static JPanel tableRow(String col1, String col2, String col3, Color color) {
        JPanel row = new JPanel(new GridLayout(1, 3, 0, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

        JLabel l1 = new JLabel(col1);
        JLabel l2 = new JLabel(col2, SwingConstants.CENTER);
        JLabel l3 = new JLabel(col3, SwingConstants.RIGHT);

        for (JLabel l : new JLabel[]{l1, l2, l3}) {
            l.setFont(FontManager.getRunescapeSmallFont());
            l.setForeground(color);
        }

        row.add(l1);
        row.add(l2);
        row.add(l3);
        return row;
    }

    // -------------------------------------------------------------------------
    // Shared tile / section helpers
    // -------------------------------------------------------------------------

    private static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        l.setForeground(ORANGE);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        return l;
    }

    /** A wrapping, label-style note (JTextArea so long text reflows at the panel width). */
    private static JPanel noteArea(String text, Color colour) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);

        JTextArea a = new JTextArea(text);
        a.setFont(FontManager.getRunescapeSmallFont());
        a.setForeground(colour);
        a.setBackground(ColorScheme.DARK_GRAY_COLOR);
        a.setOpaque(false);
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);

        panel.add(a, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel tile(String term, String definition) {
        // Title on NORTH, JTextArea on CENTER — BorderLayout gives CENTER full width
        // so lineWrap fires correctly without needing a fixed pixel width.
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setBorder(new EmptyBorder(4, 0, 5, 0));

        JLabel termLabel = new JLabel(term);
        termLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        termLabel.setForeground(new Color(255, 200, 80));

        JTextArea defArea = new JTextArea(definition);
        defArea.setFont(FontManager.getRunescapeSmallFont());
        defArea.setForeground(new Color(210, 210, 210));
        defArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
        defArea.setEditable(false);
        defArea.setFocusable(false);
        defArea.setLineWrap(true);
        defArea.setWrapStyleWord(true);
        defArea.setBorder(new EmptyBorder(0, 6, 0, 0));

        panel.add(termLabel, BorderLayout.NORTH);
        panel.add(defArea,   BorderLayout.CENTER);
        return panel;
    }

}
