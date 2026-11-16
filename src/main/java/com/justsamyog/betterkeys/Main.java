package com.justsamyog.betterkeys;
import com.justsamyog.betterkeys.ui.TrayApp;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Font;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (UnsupportedLookAndFeelException ex) {
            System.err.println("Failed to initialize Flat look and feel: " + ex.getMessage());
        }
        UIManager.put("defaultFont", new javax.swing.plaf.FontUIResource("Comic Sans MS", Font.PLAIN, 13));
        java.awt.Color accent = new java.awt.Color(0xF0F0F2);
        UIManager.put("Component.accentColor", accent);
        UIManager.put("Component.focusedBackground", new java.awt.Color(0x1A1A1C));
        UIManager.put("Component.focusColor", accent);
        UIManager.put("Button.focusedBackground", accent);
        UIManager.put("Slider.trackValueColor", accent);
        UIManager.put("Slider.thumbColor", accent);
        UIManager.put("Slider.trackColor", new java.awt.Color(0x333336));
        java.awt.Color sel = new java.awt.Color(0x3C3C42);
        UIManager.put("List.selectionBackground", sel);
        UIManager.put("List.selectionForeground", accent);
        UIManager.put("ComboBox.selectionBackground", sel);
        UIManager.put("ComboBox.selectionForeground", accent);
        UIManager.put("TextField.selectionBackground", sel);
        UIManager.put("TextField.selectionForeground", accent);
        UIManager.put("TextField.caretForeground", accent);
        UIManager.put("EditorPane.selectionBackground", sel);
        UIManager.put("EditorPane.selectionForeground", accent);

        SwingUtilities.invokeLater(() -> {
            TrayApp app = new TrayApp();
            app.start();
            for (String arg : args) {
                if ("--mapper".equals(arg)) {
                    app.openKeyMapper();
                }
            }
        });
    }
}
