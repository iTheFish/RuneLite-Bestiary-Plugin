package com.bestiary.util;

import java.io.File;

import javax.imageio.ImageIO;

/**
 * Dev-only tool: writes the {@link PanelIcon} to a PNG so the repo-root {@code icon.png} (the Plugin
 * Hub listing image) always matches the in-client sidebar icon. Lives in {@code src/test} so it never
 * ships in the plugin jar. Run after replacing the bundled logo:
 * {@code java -cp build/classes/java/main;build/resources/main;build/classes/java/test com.bestiary.util.PanelIconExport icon.png}
 */
public final class PanelIconExport {

    private PanelIconExport() {}

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        String out = args.length > 0 ? args[0] : "icon.png";
        // The Plugin Hub caps the listing icon at 48x72px, so render at 48. The in-client sidebar
        // button doesn't use this file — it calls render(s) at the nav-button size directly.
        ImageIO.write(PanelIcon.render(48), "png", new File(out));
    }
}
