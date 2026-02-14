package com.sorting;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IntegerSorterTest {

    @Test
    void shouldReturnEmptyArrayWhenSortingEmptyArray() {
        // Arrange
        IntegerSorter sorter = new IntegerSorter();
        int[] emptyArray = new int[0];

        // Act
        int[] result = sorter.sort(emptyArray);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldSortArrayWithDuplicateElements() {
        // Arrange
        IntegerSorter sorter = new IntegerSorter();
        int[] arrayWithDuplicates = new int[]{3, 1, 2, 1, 3, 2};

        // Act
        int[] result = sorter.sort(arrayWithDuplicates);

        // Assert
        assertThat(result).containsExactly(1, 1, 2, 2, 3, 3);
    }
}
