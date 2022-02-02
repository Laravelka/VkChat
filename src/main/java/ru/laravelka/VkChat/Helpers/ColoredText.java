package ru.laravelka.VkChat.Helpers;

import net.md_5.bungee.api.ChatColor;

public class ColoredText {
    public static String HexReplace(String message) {
        int start = message.indexOf("#");
        if (start + 6 > message.length()) {
            return message;
        } else {
            int end = message.indexOf("#") + 6;
            String hexcode = message.substring(start + 1, end + 1);

            try {
                Long.parseLong(hexcode, 16);
            } catch (NumberFormatException var7) {
                return message;
            }

            String back = message.substring(start);
            String front = message.substring(0, start);
            back = back.replace("#" + hexcode, "");
            return HexReplace(front + ChatColor.of("#" + hexcode) + back);
        }
    }
}
