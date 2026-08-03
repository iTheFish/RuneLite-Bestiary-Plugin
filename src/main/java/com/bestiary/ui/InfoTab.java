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
    // sub-tabs stay live so the guide/reference is always browsable).
    private JPanel statsStrip;
    private JPanel shortcutRow;
    private boolean interactiveEnabled = true;

    public InfoTab(BestiaryDataService dataService, ProgressionService progressionService,
                   Runnable openAlbum, Runnable openFavourites, Runnable openRecap,
                   Runnable openCatchRates,
                   Consumer<DashboardDialog.DashView> openDashboard,
                   Consumer<DashboardDialog.DashView> exportDashboard) {
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
        statsStrip  = buildStatsStrip();
        shortcutRow = buildShortcutRow(openAlbum, openFavourites, openRecap, openCatchRates);
        header.add(statsStrip);
        header.add(Box.createVerticalStrut(6));
        header.add(shortcutRow);
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
        setButtonsEnabled(shortcutRow, enabled);
        for (Component c : statsStrip.getComponents()) {
            c.setEnabled(enabled);
            c.setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
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
                "Bestiary turns your everyday kills into a collectible card game. Every monster you " +
                "fight is a chance to 'capture' it as a card, rolled with its own stats, a rarity, " +
                "and a small chance to be shiny. Catch them, level up, and build an album to show off.\n\n" +
                "It can feel like a lot at first, but it clicks quickly, and before long you'll be on " +
                "your way to 99 Bestiary and sharing sick cards with your friends. The other Info " +
                "tabs go deep on every system; this one is the quick tour."));
        c.add(tile("Starting off",
                "There's nothing you have to configure to begin, just fight monsters. In the RuneLite " +
                "Config panel under Bestiary you can tune the capture overlay, animations and chat " +
                "notifications to taste (level-up alerts are on by default).\n\n" +
                "What you'll notice straight away: a capture notification (with an optional " +
                "collection-jar animation) on each kill, new catches landing in the Cards tab, your " +
                "Capture Level ticking up, and Bestiary Credits building. Only catalogued roster " +
                "monsters are tracked.\n\n" +
                "To find and share your cards, open the Album (button at the top), click a card for " +
                "its details, right-click to Favourite it, and use Export to save or copy a card " +
                "image to post to your friends."));
        c.add(tile("Early levels",
                "Low-level monsters have the best catch rates, so beginner and easy mobs are the " +
                "quickest way to fill your album and bank early captures. Two shop unlocks are worth " +
                "grabbing as soon as you can: Hunter's Bounty (more credits per capture) and " +
                "Salvager's Eye (more credits when you discard). Both are cheap and snowball your " +
                "credit income for everything else.\n\n" +
                "Remember catching pays cards, credits and XP, while high-level kills give steady XP " +
                "even without a catch, so mix in tougher monsters if you're chasing levels."));
        c.add(tile("Mid game",
                "You've probably found the Favourites star and the card export by now, so copy your " +
                "best cards and share them around. You're likely sitting on a stack of duplicates " +
                "too: use Discard (right-click a card, or bulk-discard from the Album) to turn them " +
                "into credits and keep your album tidy. Caught a shiny you don't need? Either upgrade " +
                "that shiny Uncommon or discard it for a guaranteed +500 credits.\n\n" +
                "Around level 50 your catch rates are much healthier (beginners near 50%, mediums " +
                "near 35%). How's your RNG treating you? Tap any stat box at the top to open the " +
                "dashboards and see your rarity spread, species progress and economy at a glance."));
        c.add(tile("Late mid-game",
                "Credits piling up? Time to spend them. The Card Reroller re-rolls a card's stats " +
                "and shiny at the same monster and rarity, a shot at a better roll, a shiny, or even " +
                "a rarity rank-up (raised by the Reroll Fortune and Reroll Shine unlocks). Rerolled " +
                "cards keep a history you can view, and the Economy dashboard tracks your reroll " +
                "activity.\n\n" +
                "This is the stage to chase perfect versions of your favourite monsters and hunt " +
                "shinies in earnest, while you wait on the pricier shop unlocks."));
        c.add(tile("End game",
                "Did that level 92 achievement bring back some memories? You're deep in it now, but " +
                "level 92 is only halfway to 99 in XP. The last stretch is all about the best " +
                "XP-per-hour and the rarest catches.\n\n" +
                "Catch rates cap out (beginner 70%, boss 25%), high-value captures are capped at the " +
                "combat-100 XP scale, and top rarities and shinies stay genuinely rare, so the " +
                "album's final slots and the flashiest cards are a real flex. Keep at it: 99 " +
                "Bestiary and a full album await."));
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
                "Each kill rolls a capture attempt. The chance depends on two things: " +
                "the monster's difficulty tier and your current Capture Level.\n\n" +
                "Beginner (cows, goblins): 25% at level 1, rising to 70% at level 99.\n" +
                "Easy (skeletons, hobgoblins): 20% → 65%.\n" +
                "Medium (fire giants, bloodvelds): 15% → 55%.\n" +
                "Hard (hellhounds, gargoyles): 10% → 50%.\n" +
                "Elite (Adamant/Rune dragons): 5% → 35%.\n" +
                "Boss (Cerberus, Callisto, etc.): 3% → 25%.\n\n" +
                "Only catalogued roster monsters are tracked — off-roster NPCs are ignored."));
        c.add(tile("Rarity",
                "When a capture succeeds, a second weighted roll picks the rarity. " +
                "At level 1 the weights match the base percentages in the table above; " +
                "each level shifts weight toward rarer outcomes.\n\n" +
                "Example: Mythic goes from 0.1% at level 1 to ~1.5% at level 99 — " +
                "about 15× more likely. Common drops from ~75% to ~46% over the same range."));
        c.add(tile("Shiny",
                "After rarity, a third independent roll decides whether the capture is shiny. " +
                "It is orthogonal to rarity — any rarity can be shiny, from a Common to a Mythic.\n\n" +
                "The chance scales with your level: 0.2% at level 1 up to 2% at level 99. The " +
                "Shiny Charm shop unlock adds up to +0.5% on top (a separate Reroll Shine unlock " +
                "raises shiny odds when you reroll).\n\n" +
                "A shiny always rolls near-max stats (the top of its band), gets a golden card " +
                "with twinkling sparkles, and is announced in chat with a ✦ SHINY ✦ marker."));
    }

    private void fillCards(JPanel c) {
        c.add(sectionTitle("Reading a card"));
        c.add(tile("Power Level",
                "Power Level is a card's headline number. It blends two factual OSRS attributes of the " +
                "monster — its Hitpoints and its combat level — with the seven rolled stats.\n\n" +
                "Power Level = average of the 7 stats + HP ÷ 6 + combat level ÷ 6.\n\n" +
                "The stat average stays on the 1–99 scale, while HP and combat level are added " +
                "separately (equal weight) so they separate the difficulty tiers: HP adds ~+13 at 80 HP " +
                "and ~+165 at 1000 HP; combat level adds ~+21 at 124 and ~+233 at 1400. " +
                "The rolled stats are mostly flavour — difficulty drives power."));
        c.add(tile("Stats",
                "Every capture rolls seven stats — Attack, Strength, Defence, Magic, Ranged, Agility " +
                "and Prayer (Prayer and Agility roll on a smaller scale).\n\n" +
                "Each stat is rolled from that monster's own base value for it — so a hard-hitting " +
                "monster tends to roll high Attack/Strength, a caster high Magic, and so on. Rarity " +
                "then lifts the whole roll toward 99 (higher rarity = higher lift), and shiny cards " +
                "anchor to the very top of the band plus a bonus. Within each band there's random " +
                "wiggle room, and the bands overlap — so a lucky Rare can beat an unlucky Epic."));
        c.add(tile("Album",
                "A full album grid of every capturable species. Open it via 'Open Album' (in all " +
                "Collection views and on this tab).\n\n" +
                "Clicking a species card opens a detail view of all your captures of it, paginated " +
                "(8 / 12 / 16 per page) with a sort dropdown and rarity filter. Each catalog card " +
                "shows the species image, combat level, difficulty tier, and rarity dots for " +
                "rarities you have caught. Search or filter by difficulty to narrow the catalog."));
        c.add(tile("Favourites",
                "Right-click any card or row → 'Add to Favourites' to star it (up to 20). " +
                "Remove a star the same way.\n\n" +
                "The ★ Favourites button in the Collection header shows all starred cards. In the " +
                "Album, a ★ Favourites shortcut opens a detail view of every starred capture."));
        c.add(tile("Export",
                "Left-click a card, or right-click → 'Card info + export'. The card window opens on " +
                "its Export tab: a scaled preview with Copy Image / Save PNG. Each footer shows the " +
                "card's unique ID, the player who captured it, a 'Rerolled N times' line if it has " +
                "been rerolled, and the OSRS | Bestiary stamp.\n\n" +
                "Right-click → 'Copy' still copies the card straight to the clipboard without opening " +
                "the window. In the Album detail view, 'Export Page' saves the current page as a grid."));
    }

    private void fillEconomy(JPanel c) {
        c.add(sectionTitle("Credits & the shop"));
        c.add(tile("Bestiary Credits",
                "You earn Bestiary Credits on every successful capture. The award scales with " +
                "difficulty × rarity, and a shiny doubles it.\n\n" +
                "Rough guide: a Beginner Common is worth a couple of credits; a Boss Mythic is " +
                "worth about 480 (960 if shiny).\n\n" +
                "You also earn credits from progression: every Capture Level pays a bounty of " +
                "level × 10 (Lv50 = 500), and each achievement grants a one-off reward scaled to " +
                "its grind — from 10 for favouriting a card up to tens of thousands for the big " +
                "milestones.\n\n" +
                "Spend them in the Shop on the Card Reroller and passive unlocks below. Your lifetime " +
                "earned and spent totals are tracked in the Economy dashboard."));
        c.add(tile("Card Reroller",
                "Right-click a card → 'Reroll (shop)…' to re-roll its stats and shiny at the " +
                "same monster and rarity — a chance to improve a roll or hit a shiny.\n\n" +
                "The cost scales with the card's difficulty × rarity (shiny doesn't change it): from " +
                "20 credits for a Beginner Common up to 1,200 for a Boss Mythic.\n\n" +
                "A shiny stays shiny; a non-shiny gets a fresh shiny roll (raised by the Reroll Shine " +
                "shop unlock). Non-Mythic cards have a 5% base chance to rank up one rarity " +
                "(raised by the Reroll Fortune shop unlock). Your " +
                "favourite, nickname and album cover are kept. A rerolled card is marked " +
                "'Rerolled N times' and shows a before/after result with a 'What were the odds?' " +
                "breakdown — remember those odds describe a raw pull, not a rerolled card."));
        c.add(tile("Discard",
                "Don't want a card? Right-click → 'Discard…' to trade it for credits — the refund is " +
                "its base capture value, and shinies are worth a guaranteed +500 credits. From the Album you can " +
                "multi-select to discard several at once.\n\n" +
                "Discarding is permanent: the card is removed from your collection."));
        c.add(tile("Shop",
                "The Shop tab is where credits are spent. It offers the Card Reroller (right-click a " +
                "card) and the passive unlocks below, grouped into Progression and Rerolls " +
                "categories; more tools are on the way."));
        c.add(tile("Passive unlocks",
                "The Shop tab sells permanent passive upgrades in two categories (cost rises per " +
                "tier):\n\n" +
                "Progression:\n" +
                "• Hunter's Bounty — +2 credits per tier (up to +10) added to every capture reward.\n" +
                "• Salvager's Eye — +2% per tier to credits earned from discarding cards.\n" +
                "• Hunter's Focus — +5 XP per tier (up to +25) added to every kill's XP.\n" +
                "• Scholar's Insight — +5% per tier (up to +25%) to the XP from every capture.\n" +
                "• Shiny Charm — +0.1% per tier (up to +0.5%) to your capture shiny chance.\n\n" +
                "Rerolls:\n" +
                "• Reroll Shine — +0.1% per tier to the shiny chance when you reroll a card.\n" +
                "• Reroll Fortune — +1% per tier to the chance a reroll ranks a card up one rarity.\n\n" +
                "Each card shows its current bonus and what the next tier upgrades it to before you buy."));
        c.add(tile("No real-world value",
                "Bestiary is a free, fan-made minigame — it's all just for fun. Bestiary Credits, cards, " +
                "rarities, shinies and Power Levels live entirely inside this plugin: they have no " +
                "real-world or in-game value, can't be bought, sold or traded for real money, RuneScape " +
                "GP or items, and give no advantage in Old School RuneScape.\n\n" +
                "Bestiary isn't affiliated with or endorsed by Jagex. Old School RuneScape is a trademark " +
                "of Jagex Ltd; all monster names and artwork belong to Jagex and the OSRS Wiki."));
    }

    private void fillProgress(JPanel c) {
        c.add(sectionTitle("Progress & stats"));
        c.add(tile("XP & levels",
                "You earn experience from kills and captures. Your Capture Level runs 1–99 " +
                "(with virtual levels beyond).\n\n" +
                "Kill XP is a flat amount by difficulty tier: Beginner 5, Easy 10, Medium 15, " +
                "Hard 20, Elite 25, Boss 30. Only monsters in the roster award XP.\n\n" +
                "Capture XP is a much bigger bonus: base × rarity multiplier, where base = the " +
                "monster's combat level × 10 (minimum 10), capped at combat level 100. Multipliers: " +
                "Common 1×, Uncommon 2×, Rare 5×, Epic 10×, Legendary 25×, Mythic 50×.\n\n" +
                "Examples: a Common catch of a level-2 mob = 20 XP; a Rare catch of a level-50 mob = " +
                "500 × 5 = 2,500 XP. The cap means any monster level 100+ pays the same ceiling — up " +
                "to Mythic 50,000. For reference, level 99 is 13,034,431 XP.\n\n" +
                "So low-level mobs are best caught for cards + credits, while high-level kills are a " +
                "steady XP source even when you don't land the catch."));
        c.add(tile("Dashboards",
                "The four stat boxes at the top of this tab are clickable — each opens a dashboard: " +
                "Progression, Economy, Species and Caught.\n\n" +
                "They break down your collection with bar charts and top-10 tables. The Economy " +
                "dashboard shows lifetime credits earned/spent, reroll activity and your owned shop " +
                "upgrades. Right-click a box to copy that dashboard as a shareable card image."));
        c.add(tile("Session Recap",
                "A button on the Progress tab shows every capture made since you last logged in, " +
                "with rarity (colour-coded), Power Level, region and time, plus a rarity summary.\n\n" +
                "'Copy Summary' places the list on your clipboard as a code block so it pastes " +
                "cleanly into Discord."));
    }

    private void fillAlerts(JPanel c) {
        c.add(sectionTitle("Notifications & data"));
        c.add(tile("Capture overlay",
                "A small notification panel appears on screen each time a capture succeeds. " +
                "Position and width are configurable in the RuneLite Config panel under Bestiary.\n\n" +
                "An optional collection-jar animation can play on every kill attempt before the " +
                "result is revealed — toggle 'Show Capture Animation' in Config. Rapid kills queue " +
                "so every result still plays."));
        c.add(tile("Chat notifications",
                "Two modes, selected in Config under 'Chat Notification Mode':\n\n" +
                "Verbose — one message per capture with rarity, NPC name, kill number and Power " +
                "Level. The kill number keeps messages unique (RuneLite drops duplicates).\n\n" +
                "Batched — repeated NPC+rarity kills are held for 9 seconds of inactivity then sent " +
                "as one summary (e.g. '3× Common Goblin captured!  Kill #42  PWR:28, 35, 41'). " +
                "Shinies always announce immediately."));
        c.add(tile("Level-up alerts",
                "When your Capture Level increases, a gold banner plays on the overlay and a message " +
                "is sent to your chatbox.\n\n" +
                "The chat message is controlled by 'Notify On Level Up' in Config (on by default) — " +
                "turn it off if you only want the on-screen banner."));
        c.add(tile("Reset Progress & Collection",
                "The 'Reset Progress & Collection?' button on the Progress tab permanently deletes all " +
                "captures, kill counts, XP, levels and achievements. You are asked to confirm twice."));
    }

    // -------------------------------------------------------------------------
    // Live stats strip  (4 boxes in one row)
    // -------------------------------------------------------------------------

    private JPanel buildStatsStrip() {
        JPanel strip = new JPanel(new GridLayout(2, 2, 4, 4));
        strip.setOpaque(false);
        strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        strip.setAlignmentX(LEFT_ALIGNMENT);

        strip.add(clickable(statBox("Level",   levelVal,    false), DashboardDialog.DashView.PROGRESSION));
        strip.add(clickable(statBox("Kills",   killsVal,    true),  DashboardDialog.DashView.PROGRESSION));
        strip.add(clickable(statBox("Species", speciesVal,  false), DashboardDialog.DashView.SPECIES));
        strip.add(clickable(statBox("Caught",  capturesVal, true),  DashboardDialog.DashView.CAUGHT));

        return strip;
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
                    JMenuItem open = new JMenuItem("Open Dashboard — " + view.label);
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

    private JPanel buildShortcutRow(Runnable openAlbum, Runnable openFavourites,
                                            Runnable openRecap, Runnable openCatchRates) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setAlignmentX(LEFT_ALIGNMENT);

        // Full-width Open Album (top), Favourites + Catch Rates (middle), full-width Session Recap (bottom).
        JPanel albumRow = new JPanel(new GridLayout(1, 1));
        albumRow.setOpaque(false);
        albumRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        albumRow.setAlignmentX(LEFT_ALIGNMENT);
        albumRow.add(blockBtn("Open Album", ORANGE, openAlbum, true));

        JPanel midRow = new JPanel(new GridLayout(1, 2, 4, 0));
        midRow.setOpaque(false);
        midRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        midRow.setAlignmentX(LEFT_ALIGNMENT);
        favouritesBtn = blockBtn("★ Favourites", new Color(220, 180, 60), openFavourites);
        midRow.add(favouritesBtn);
        JButton catchBtn = blockBtn(" Catch Rates", new Color(100, 180, 220), openCatchRates, true);
        final int iD = 13;
        catchBtn.setIcon(new Icon() {
            @Override public int getIconWidth()  { return iD; }
            @Override public int getIconHeight() { return iD; }
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
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
        midRow.add(catchBtn);

        JPanel recapRow = new JPanel(new GridLayout(1, 1));
        recapRow.setOpaque(false);
        recapRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        recapRow.setAlignmentX(LEFT_ALIGNMENT);
        recapBtn = blockBtn("Session Recap", new Color(120, 200, 120), openRecap, true);
        recapRow.add(recapBtn);

        container.add(albumRow);
        container.add(Box.createVerticalStrut(4));
        container.add(midRow);
        container.add(Box.createVerticalStrut(4));
        container.add(recapRow);
        return container;
    }

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
