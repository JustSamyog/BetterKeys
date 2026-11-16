package com.justsamyog.betterkeys.input;
import com.justsamyog.betterkeys.audio.AudioEngine;
import com.justsamyog.betterkeys.model.Soundpack;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalInputListener implements NativeKeyListener, NativeMouseInputListener {

    private final AudioEngine audio;
    private Soundpack soundpack;
    private volatile boolean enabled = true;
    private volatile Runnable globalMousePress;

    private final Map<String, Long> lastPressTime = new ConcurrentHashMap<>();
    private final Set<Integer> heldKeys = ConcurrentHashMap.newKeySet();
    private static final long DEBOUNCE_MS = 200;

    public GlobalInputListener(AudioEngine audio, Soundpack soundpack) {
        this.audio = audio;
        this.soundpack = soundpack;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setSoundpack(Soundpack soundpack) {
        this.soundpack = soundpack;
        lastPressTime.clear();
    }

    private String pickSound(String id) {
        if (soundpack == null) {
            return null;
        }
        if (soundpack.getSoundPath(id) != null) {
            return id;
        }
        return soundpack.getRandomKeySound();
    }

    private void playSound(String id) {
        if (id != null) {
            audio.play(id);
        }
    }

    private boolean canPlay(String vc) {
        long now = System.currentTimeMillis();
        Long last = lastPressTime.get(vc);
        if (last == null) return true;
        return (now - last) >= DEBOUNCE_MS;
    }

    private void recordPress(String vc) {
        lastPressTime.put(vc, System.currentTimeMillis());
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (!enabled || soundpack == null) return;
        if (!heldKeys.add(e.getKeyCode())) return;
        String vc = keyCodeToVC(e.getKeyCode());
        if (vc == null) return;
        if (!canPlay(vc)) return;
        recordPress(vc);

        String mapped = soundpack.randomVariant(vc);
        if (mapped != null) {
            audio.play(mapped);
            return;
        }
        switch (e.getKeyCode()) {
            case NativeKeyEvent.VC_SPACE -> playSound(pickSound("space_press"));
            case NativeKeyEvent.VC_ENTER -> playSound(pickSound("enter_press"));
            case NativeKeyEvent.VC_BACKSPACE -> playSound(pickSound("backspace_press"));
            case NativeKeyEvent.VC_TAB -> playSound(pickSound("tab_press"));
            case NativeKeyEvent.VC_ESCAPE -> playSound(pickSound("escape_press"));
            default -> playSound(soundpack.getRandomKeySound());
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        heldKeys.remove(e.getKeyCode());
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
    }

    @Override
    public void nativeMouseClicked(NativeMouseEvent e) {
    }

    @Override
    public void nativeMousePressed(NativeMouseEvent e) {
        if (enabled && e.getButton() != NativeMouseEvent.NOBUTTON) {
            playSound(soundpack != null ? soundpack.randomVariant("mouse_click") : "mouse_click");
        }
        Runnable r = globalMousePress;
        if (r != null) r.run();
    }

    public void setGlobalMousePress(Runnable r) {
        this.globalMousePress = r;
    }

    @Override
    public void nativeMouseReleased(NativeMouseEvent e) {
    }

    public static String keyCodeToVC(int keyCode) {
        return switch (keyCode) {
            case NativeKeyEvent.VC_A -> "VC_A";
            case NativeKeyEvent.VC_B -> "VC_B";
            case NativeKeyEvent.VC_C -> "VC_C";
            case NativeKeyEvent.VC_D -> "VC_D";
            case NativeKeyEvent.VC_E -> "VC_E";
            case NativeKeyEvent.VC_F -> "VC_F";
            case NativeKeyEvent.VC_G -> "VC_G";
            case NativeKeyEvent.VC_H -> "VC_H";
            case NativeKeyEvent.VC_I -> "VC_I";
            case NativeKeyEvent.VC_J -> "VC_J";
            case NativeKeyEvent.VC_K -> "VC_K";
            case NativeKeyEvent.VC_L -> "VC_L";
            case NativeKeyEvent.VC_M -> "VC_M";
            case NativeKeyEvent.VC_N -> "VC_N";
            case NativeKeyEvent.VC_O -> "VC_O";
            case NativeKeyEvent.VC_P -> "VC_P";
            case NativeKeyEvent.VC_Q -> "VC_Q";
            case NativeKeyEvent.VC_R -> "VC_R";
            case NativeKeyEvent.VC_S -> "VC_S";
            case NativeKeyEvent.VC_T -> "VC_T";
            case NativeKeyEvent.VC_U -> "VC_U";
            case NativeKeyEvent.VC_V -> "VC_V";
            case NativeKeyEvent.VC_W -> "VC_W";
            case NativeKeyEvent.VC_X -> "VC_X";
            case NativeKeyEvent.VC_Y -> "VC_Y";
            case NativeKeyEvent.VC_Z -> "VC_Z";
            case NativeKeyEvent.VC_0 -> "VC_0";
            case NativeKeyEvent.VC_1 -> "VC_1";
            case NativeKeyEvent.VC_2 -> "VC_2";
            case NativeKeyEvent.VC_3 -> "VC_3";
            case NativeKeyEvent.VC_4 -> "VC_4";
            case NativeKeyEvent.VC_5 -> "VC_5";
            case NativeKeyEvent.VC_6 -> "VC_6";
            case NativeKeyEvent.VC_7 -> "VC_7";
            case NativeKeyEvent.VC_8 -> "VC_8";
            case NativeKeyEvent.VC_9 -> "VC_9";
            case NativeKeyEvent.VC_SPACE -> "VC_SPACE";
            case NativeKeyEvent.VC_ENTER -> "VC_ENTER";
            case NativeKeyEvent.VC_BACKSPACE -> "VC_BACKSPACE";
            case NativeKeyEvent.VC_TAB -> "VC_TAB";
            case NativeKeyEvent.VC_ESCAPE -> "VC_ESCAPE";
            case NativeKeyEvent.VC_SHIFT -> "VC_SHIFT";
            case NativeKeyEvent.VC_CONTROL -> "VC_CONTROL";
            case NativeKeyEvent.VC_ALT -> "VC_ALT";
            case NativeKeyEvent.VC_F1 -> "VC_F1";
            case NativeKeyEvent.VC_F2 -> "VC_F2";
            case NativeKeyEvent.VC_F3 -> "VC_F3";
            case NativeKeyEvent.VC_F4 -> "VC_F4";
            case NativeKeyEvent.VC_F5 -> "VC_F5";
            case NativeKeyEvent.VC_F6 -> "VC_F6";
            case NativeKeyEvent.VC_F7 -> "VC_F7";
            case NativeKeyEvent.VC_F8 -> "VC_F8";
            case NativeKeyEvent.VC_F9 -> "VC_F9";
            case NativeKeyEvent.VC_F10 -> "VC_F10";
            case NativeKeyEvent.VC_F11 -> "VC_F11";
            case NativeKeyEvent.VC_F12 -> "VC_F12";
            case NativeKeyEvent.VC_UP -> "VC_UP";
            case NativeKeyEvent.VC_DOWN -> "VC_DOWN";
            case NativeKeyEvent.VC_LEFT -> "VC_LEFT";
            case NativeKeyEvent.VC_RIGHT -> "VC_RIGHT";
            case NativeKeyEvent.VC_HOME -> "VC_HOME";
            case NativeKeyEvent.VC_END -> "VC_END";
            case NativeKeyEvent.VC_PAGE_UP -> "VC_PAGE_UP";
            case NativeKeyEvent.VC_PAGE_DOWN -> "VC_PAGE_DOWN";
            case NativeKeyEvent.VC_INSERT -> "VC_INSERT";
            case NativeKeyEvent.VC_DELETE -> "VC_DELETE";
            case NativeKeyEvent.VC_MINUS -> "VC_MINUS";
            case NativeKeyEvent.VC_EQUALS -> "VC_EQUALS";
            case NativeKeyEvent.VC_OPEN_BRACKET -> "VC_OPEN_BRACKET";
            case NativeKeyEvent.VC_CLOSE_BRACKET -> "VC_CLOSE_BRACKET";
            case NativeKeyEvent.VC_SEMICOLON -> "VC_SEMICOLON";
            case NativeKeyEvent.VC_QUOTE -> "VC_QUOTE";
            case NativeKeyEvent.VC_BACK_SLASH -> "VC_BACK_SLASH";
            case NativeKeyEvent.VC_COMMA -> "VC_COMMA";
            case NativeKeyEvent.VC_PERIOD -> "VC_PERIOD";
            case NativeKeyEvent.VC_SLASH -> "VC_SLASH";
            case NativeKeyEvent.VC_BACKQUOTE -> "VC_BACKQUOTE";
            case NativeKeyEvent.VC_CAPS_LOCK -> "VC_CAPS_LOCK";
            case NativeKeyEvent.VC_NUM_LOCK -> "VC_NUM_LOCK";
            case NativeKeyEvent.VC_PAUSE -> "VC_PAUSE";
            case NativeKeyEvent.VC_PRINTSCREEN -> "VC_PRINTSCREEN";
            case NativeKeyEvent.VC_SCROLL_LOCK -> "VC_SCROLL_LOCK";
            case NativeKeyEvent.VC_META -> "VC_META";
            case NativeKeyEvent.VC_CONTEXT_MENU -> "VC_CONTEXT_MENU";
            default -> null;
        };
    }

    public static boolean registerHook() {
        try {
            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook();
            }
            return true;
        } catch (NativeHookException ex) {
            System.err.println("Failed to register global input hook: " + ex.getMessage());
            return false;
        }
    }

    public static void addListeners(NativeKeyListener keyListener, NativeMouseInputListener mouseListener) {
        try {
            GlobalScreen.addNativeKeyListener(keyListener);
            GlobalScreen.addNativeMouseListener(mouseListener);
        } catch (Exception ex) {
            System.err.println("Failed to add native listeners: " + ex.getMessage());
        }
    }

    public static void removeListeners(NativeKeyListener keyListener, NativeMouseInputListener mouseListener) {
        try {
            GlobalScreen.removeNativeKeyListener(keyListener);
            GlobalScreen.removeNativeMouseListener(mouseListener);
        } catch (Exception ignored) {
        }
    }

    public static void unregisterHook() {
        try {
            if (GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.unregisterNativeHook();
            }
        } catch (Exception ignored) {
        }
    }
}
