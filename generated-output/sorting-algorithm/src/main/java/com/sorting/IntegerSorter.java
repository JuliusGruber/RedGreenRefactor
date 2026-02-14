package com.sorting;

import java.util.Arrays;

/**
 * Sorts arrays of integers.
 */
public class IntegerSorter {

    /**
     * Sorts the given array of integers in ascending order.
     *
     * @param array the array to sort
     * @return a sorted array
     */
    public int[] sort(int[] array) {
        int[] result = sortedCopyOf(array);
        return result;
    }

    /**
     * Sorts the given array of integers in descending order.
     *
     * @param array the array to sort
     * @return a sorted array in descending order
     */
    public int[] sortDescending(int[] array) {
        int[] result = sortedCopyOf(array);
        reverse(result);
        return result;
    }

    /**
     * Creates a sorted copy of the given array, handling null input.
     *
     * @param array the array to copy and sort
     * @return a sorted copy of the array, or empty array if input is null
     */
    private int[] sortedCopyOf(int[] array) {
        if (array == null) {
            return new int[0];
        }
        int[] copy = Arrays.copyOf(array, array.length);
        Arrays.sort(copy);
        return copy;
    }

    /**
     * Reverses the given array in place.
     *
     * @param array the array to reverse
     */
    private void reverse(int[] array) {
        int left = 0;
        int right = array.length - 1;
        while (left < right) {
            int temp = array[left];
            array[left] = array[right];
            array[right] = temp;
            left++;
            right--;
        }
    }

    /**
     * Sorts the given array of negative numbers by their absolute value.
     *
     * @param array the array to sort (should contain only negative numbers)
     * @return a sorted array by absolute value (smallest absolute value first)
     */
    public int[] sortNegativesByAbsoluteValue(int[] array) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
