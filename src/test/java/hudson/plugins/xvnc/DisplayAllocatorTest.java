package hudson.plugins.xvnc;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertThat;

import java.util.Arrays;

import org.junit.Test;

public class DisplayAllocatorTest {

    private static final int MAX = 3;
    private static final int MIN = 0;

    @Test
    public void throwsExceptionWhenAllDisplaysTaken() {
        DisplayAllocator allocator = new DisplayAllocator(MIN, MAX);
        int range = MAX - MIN + 1;
        for (int i=0; i<range; i++) {
            allocator.allocate();
        }
        try {
            allocator.allocate();
            fail("Expected exception because all displays are allocated, none received");
        } catch(RuntimeException e) {
        }
    }

    @Test
    public void doesNotAllocateBlacklistedDisplays() {
        DisplayAllocator allocator = new DisplayAllocator(MIN, MAX);
        int badDisplay = MIN + 1;
        int range = MAX - MIN + 1 - 1; // one bad display
        allocator.blacklist(badDisplay);
        for (int i=0; i<range; i++) {
            assertThat(allocator.allocate(), not(equalTo(badDisplay)));
        }
    }

    @Test
    public void clearsBlacklistWhenAllDisplaysTaken() {
        DisplayAllocator allocator = new DisplayAllocator(MIN, MAX);
        int badDisplay = MIN + 1;
        allocator.blacklist(badDisplay);
        int range = MAX - MIN + 1 - 1;
        for (int i=0; i<range; i++) {
            assertThat(allocator.allocate(), not(equalTo(badDisplay)));
        }
        assertThat(allocator.allocate(), equalTo(badDisplay));
        // now all displays are taken
        try {
            allocator.allocate();
            fail("Expected exception because all displays are allocated, none received");
        } catch(RuntimeException e) {
        }
    }

    @Test
    public void freesPortWhenBlacklisted() {
        DisplayAllocator allocator = new DisplayAllocator(0, 1);
        int first = allocator.allocate();
        int second = allocator.allocate();
        allocator.blacklist(second);
        assertThat(allocator.allocate(), equalTo(second));
    }

    @Test
    public void canHandleWhenAllDisplaysAreBlacklisted() {
        DisplayAllocator allocator = new DisplayAllocator(MIN, MAX);
        int range = MAX - MIN + 1;
        for (int i=0; i<range; i++) {
            allocator.allocate();
            allocator.blacklist(i);
        }
        try {
            allocator.allocate();
        } catch(RuntimeException e) {
            fail("Blacklist should have been cleared.");
        }
    }

    @Test
    public void doesReturnAllNumbersInRangeInclusive() {
        DisplayAllocator allocator = new DisplayAllocator(MIN, MAX);
        int range = MAX - MIN + 1;
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
