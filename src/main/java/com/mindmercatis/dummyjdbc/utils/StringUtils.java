package com.mindmercatis.dummyjdbc.utils;

/**
 * String Utils Class
 *
 * @author WhatAKitty
 **/
public class StringUtils {

    public static String join(String[] strs, String sep) {
        if (strs == null || strs.length == 0) {
            return "";
        }

        if (sep == null) {
            sep = ",";
        }

        StringBuilder sb = new StringBuilder();
        for (String str : strs) {
            sb.append(sep).append(str);
        }
        return sb.substring(1).toString();
    }

    private StringUtils() {}

}
