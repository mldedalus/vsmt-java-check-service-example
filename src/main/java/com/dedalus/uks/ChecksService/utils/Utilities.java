package com.dedalus.uks.ChecksService.utils;

import java.util.Arrays;

public class Utilities {

public static String SNOMED_SYSTEM_URI = "http://snomed.info/sct";
public static String ISSUE_DETAIL_SYSTEM_URI = "http://vsmt.dedalus.eu/issue-detail";

    // Method to convert a string to hex
    public static String toHex(String input) {
        StringBuilder hexString = new StringBuilder();
        for (char c : input.toCharArray()) {
            hexString.append(String.format("%02x", (int) c));
        }
        return hexString.toString();
    }

    public static boolean containsIgnoreCase(String[] array, String target) {
        return Arrays.stream(array)
                 .anyMatch(s -> s.equalsIgnoreCase(target));
    }
}


