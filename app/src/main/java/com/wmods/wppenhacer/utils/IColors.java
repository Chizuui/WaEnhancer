package com.wmods.wppenhacer.utils;

import android.graphics.Color;

import java.util.HashMap;
import java.util.Map;

public class IColors {
    public static HashMap<String, String> colors = new HashMap<>();


    public static HashMap<String, String> alphacolors = new HashMap<>();

    public static final HashMap<String, String> backgroundColors = new HashMap<>();
    public static final HashMap<String, String> primaryColors = new HashMap<>();
    public static final HashMap<String, String> textColors = new HashMap<>();

    public static int parseColor(String str) {
        return Color.parseColor(str);
    }

    public static String toString(int i) {
        var color = Integer.toHexString(i);
        if (color.length() == 7) {
            color = "0" + color;
        } else if (color.length() == 1) {
            color = "00000000";
        }
        return "#" + color;
    }


    public static final HashMap<Integer, Integer> intColors = new HashMap<>();

    public static int getFromIntColor(int color, HashMap<String, String> colors, boolean isBackground) {
        // Skip colors with transparency (alpha < 255)
        if (((color >> 24) & 0xFF) != 0xFF) return color;

        Integer cached = intColors.get(color);
        if (cached != null) return cached;

        int result = color;
        for (Map.Entry<String, String> entry : colors.entrySet()) {
            try {
                int keyColor = parseColor(entry.getKey());
                if (keyColor == color) {
                    // If it's a primary color but used for background, check if we should skip it
                    // This fixes the 'orange selection' in WA Messenger
                    if (isBackground && primaryColors.containsKey(entry.getKey())) {
                        continue;
                    }
                    result = parseColor(entry.getValue());
                    break;
                }
            } catch (Exception ignored) {}
        }
        
        intColors.put(color, result);
        return result;
    }

    public static void initColors() {
        primaryColors.clear();
        textColors.clear();
        backgroundColors.clear();
        colors.clear();
        intColors.clear();

        // primary colors
        primaryColors.put("#ff00a884", "#ff00a884");
        primaryColors.put("#ff1da457", "#ff1da457");
        primaryColors.put("#ff21c063", "#ff21c063");
        primaryColors.put("#ff1daa61", "#ff1daa61");
        primaryColors.put("#ff25d366", "#ff25d366");
        primaryColors.put("#ffd9fdd3", "#ffd9fdd3");
        primaryColors.put("#ff1b864b", "#ff1b864b");
        primaryColors.put("#ff144d37", "#ff144d37");
        primaryColors.put("#ff1b8755", "#ff1b8755");
        primaryColors.put("#ff15603e", "#ff15603e");

        // text colors
        textColors.put("#ffeaedee", "#ffeaedee");
        textColors.put("#fff7f8fa", "#fff7f8fa");

        // background colors - ONLY keep core background colors
        backgroundColors.put("#ff0b141a", "#ff111b21");
        backgroundColors.put("#ff111b21", "#ff111b21");
        backgroundColors.put("#ff000000", "#ff000000");
        backgroundColors.put("#ff0a1014", "#ff0a1014");
    }
}
