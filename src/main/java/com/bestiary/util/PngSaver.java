package com.bestiary.util;

import net.runelite.client.util.Filepath;

import javax.imageio.ImageIO;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Shared "Save PNG" flow for every export button: a save dialog (RuneLite's {@link Filepath.Chooser})
 * then a PNG write to the file the player picked. One place so all exports behave the same.
 */
public final class PngSaver {

    private PngSaver() {}

    /**
     * Asks where to save {@code img}, suggesting {@code fileName}, and writes it as PNG. ".png" is
     * added when the chosen name has no extension. Must be called on the EDT.
     *
     * @return true if saved; false if the player cancelled
     * @throws IOException if the write failed
     */
    public static boolean save(Component parent, String fileName, BufferedImage img) throws IOException {
        List<Filepath> picked = new Filepath.Chooser()
                .setIsSave()
                .setAcceptsFiles()
                .addExtensionFilter("PNG image", "png")
                .setDefaultExtension("png")
                .setFileName(safeName(fileName))
                .showDialog(parent);
        if (picked.isEmpty()) return false;
        try (OutputStream out = picked.get(0).openOutputStream()) {
            ImageIO.write(img, "png", out);
        }
        return true;
    }

    /** Replaces characters a file name can't contain, so a suggestion is never rejected outright. */
    private static String safeName(String name) {
        String s = name.replaceAll("[\\x00-\\x1f<>:\"/\\\\|?*~]", "_").trim();
        return s.isEmpty() ? "bestiary.png" : s;
    }
}
