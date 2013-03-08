package hudson.plugins.xvnc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public class DisplayAllocatorTest {

    private static final int MAX = 3;
    private static final int MIN = 0;

    @Test
    public void throwsExceptionWhenAllDisplaysTaken() {
        int range = MAX - MIN + 1;
        DisplayAllocator allocator = new DisplayAllocator(MIN, MAX);
        for (int i=0; i<range; i++) {
            allocator.allocate();
        }
        try {
            allocator.allocate();
            fail("Expected exception because all numbers allocated, none received");
        } catch(RuntimeException e) {
        }
    }

    @Test
    public void doesReturnAllNumbersInRangeInclusive() {
        int range = MAX - MIN + 1;
        DisplayAllocator allocator = new DisplayAllocator(MIN, MAX);
        int[] displays = new int[range];
        int[] expected = new int[range];
        for (int i=0; i<range; i++) {
            displays[i] = allocator.allocate();
            expected[i] = i;
        }
        Arrays.sort(displays);
        assertArrayEquals(expected, displays);
    }

}
