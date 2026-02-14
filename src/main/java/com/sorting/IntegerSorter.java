package com.sorting;

import java.util.Arrays;

public class IntegerSorter {

    public int[] sort(int[] array) {
        int[] result = Arrays.copyOf(array, array.length);
        Arrays.sort(result);
        return result;
    }
}
