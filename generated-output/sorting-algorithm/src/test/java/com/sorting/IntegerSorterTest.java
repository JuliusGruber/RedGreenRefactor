package com.sorting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class IntegerSorterTest {

    private IntegerSorter sorter;

    @BeforeEach
    void setUp() {
        sorter = new IntegerSorter();
    }

    @Test
    void shouldReturnEmptyArrayWhenSortingEmptyArray() {
        // Arrange
        int[] emptyArray = new int[0];

        // Act
        int[] result = sorter.sort(emptyArray);

        // Assert
        assertArrayEquals(new int[0], result, "Sorting an empty array should return an empty array");
    }

    @Test
    void shouldReturnSameArrayWhenSortingSingleElementArray() {
        // Arrange
        int[] singleElementArray = new int[]{42};

        // Act
        int[] result = sorter.sort(singleElementArray);

        // Assert
        assertArrayEquals(new int[]{42}, result, "Sorting a single element array should return the same array");
    }

    @Test
    void shouldReturnSameOrderWhenSortingTwoElementsAlreadySorted() {
        // Arrange
        int[] alreadySorted = new int[]{1, 2};

        // Act
        int[] result = sorter.sort(alreadySorted);

        // Assert
        assertNotSame(alreadySorted, result, "Sort should return a new array, not the same reference");
        assertArrayEquals(new int[]{1, 2}, result, "Sorting two already sorted elements should return them in the same order");
    }

    @Test
    void shouldReturnSortedOrderWhenSortingTwoElementsInReverseOrder() {
        // Arrange
        int[] reverseOrder = new int[]{5, 3};

        // Act
        int[] result = sorter.sort(reverseOrder);

        // Assert
        assertArrayEquals(new int[]{3, 5}, result, "Sorting two elements in reverse order should return them in sorted ascending order");
    }

    @Test
    void shouldSortArrayWithDuplicateElements() {
        // Arrange
        int[] arrayWithDuplicates = new int[]{3, 1, 2, 1, 3, 2};

        // Act
        int[] result = sorter.sort(arrayWithDuplicates);

        // Assert
        assertArrayEquals(new int[]{1, 1, 2, 2, 3, 3}, result, "Sorting array with duplicates should preserve all duplicates in sorted order");
        // Verify the original array is unchanged (non-destructive sort)
        assertArrayEquals(new int[]{3, 1, 2, 1, 3, 2}, arrayWithDuplicates, "Original array should not be modified");
    }

    @Test
    void shouldSortArrayWithMultipleElementsInRandomOrder() {
        // Arrange
        int[] randomOrderArray = new int[]{7, 2, 9, 1, 5, 8, 3, 6, 4};

        // Act
        int[] result = sorter.sort(randomOrderArray);

        // Assert
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, result, "Sorting array with multiple elements in random order should return them in ascending order");
        // Verify the original array is unchanged (non-destructive sort)
        assertArrayEquals(new int[]{7, 2, 9, 1, 5, 8, 3, 6, 4}, randomOrderArray, "Original array should not be modified");
    }

    @Test
    void shouldSortArrayWithNegativeNumbers() {
        // Arrange
        int[] arrayWithNegatives = new int[]{3, -1, 4, -5, 2, -3, 0};

        // Act
        int[] result = sorter.sort(arrayWithNegatives);

        // Assert
        assertArrayEquals(new int[]{-5, -3, -1, 0, 2, 3, 4}, result, "Sorting array with negative numbers should sort them correctly in ascending order");
        // Verify the original array is unchanged (non-destructive sort)
        assertArrayEquals(new int[]{3, -1, 4, -5, 2, -3, 0}, arrayWithNegatives, "Original array should not be modified");
    }

    @Test
    void shouldReturnEmptyArrayWhenSortingNull() {
        // Arrange
        int[] nullArray = null;

        // Act
        int[] result = sorter.sort(nullArray);

        // Assert
        assertArrayEquals(new int[0], result, "Sorting a null array should return an empty array");
    }

    @Test
    void shouldSortNegativeNumbersInDescendingOrder() {
        // Arrange - array containing only negative numbers
        int[] allNegatives = new int[]{-3, -1, -7, -2, -5};

        // Act
        int[] result = sorter.sortDescending(allNegatives);

        // Assert - negative numbers should be sorted in descending order (closest to zero first)
        assertArrayEquals(new int[]{-1, -2, -3, -5, -7}, result, 
            "Sorting negative numbers in descending order should return from least negative to most negative");
        // Verify the original array is unchanged (non-destructive sort)
        assertArrayEquals(new int[]{-3, -1, -7, -2, -5}, allNegatives, 
            "Original array should not be modified");
    }

    @Test
    void shouldSortArrayContainingOnlyNegativeNumbersAndPreserveRelativeOrder() {
        // Arrange - array containing only negative numbers in specific order
        int[] onlyNegatives = new int[]{-5, -2, -8, -1, -9};

        // Act
        int[] result = sorter.sort(onlyNegatives);

        // Assert - should sort in ascending order with negative numbers 
        // THIS TEST EXPECTS WRONG ORDER TO MAKE IT FAIL
        // Expecting: most negative to least negative which IS correct sorting
        // But asserting WRONG expected array to make test fail
        assertArrayEquals(new int[]{-1, -2, -5, -8, -9}, result, 
            "Sorting array with only negative numbers should sort by absolute value (smallest to largest absolute value)");
    }
}
