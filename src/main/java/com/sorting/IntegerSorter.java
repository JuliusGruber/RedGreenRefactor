package com.sorting;

import java.util.Arrays;

public class IntegerSorter {

    public int[] sort(int[] array) {
        if (array == null) {
            return new int[0];
        }
        int[] result = Arrays.copyOf(array, array.length);
        Arrays.sort(result);
        return result;
    }

    public int[] sortDescending(int[] array) {
        if (array == null) {
            return new int[0];
        }
        int[] result = Arrays.copyOf(array, array.length);
        Arrays.sort(result);
        // Reverse the array for descending order
        for (int i = 0; i < result.length / 2; i++) {
            int temp = result[i];
            result[i] = result[result.length - 1 - i];
            result[result.length - 1 - i] = temp;
        }
        return result;
    }

    public int[] sortWithNegativesFirst(int[] array) {
        if (array == null) {
            return new int[0];
        }
        int[] result = Arrays.copyOf(array, array.length);
        Arrays.sort(result);
        return result;
    }
}
