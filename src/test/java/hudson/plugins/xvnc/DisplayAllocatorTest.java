package hudson.plugins.xvnc;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertThat;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

public class DisplayAllocatorTest {

    private static final int MAX = 3;
    private static final int MIN = 0;
    private DisplayAllocator allocator;

    @Before
    public void setup() {
        allocator = new DisplayAllocator();
    }

    @Test
    public void throwsExceptionWhenAllDisplaysTaken() {
        int range = MAX - MIN + 1;
        for (int i=0; i<range; i++) {
            allocate();
        }
        try {
            allocate();
            fail("Expected exception because all displays are allocated, none received");
        } catch(RuntimeException e) {
        }
    }

    private int allocate() {
        return allocator.allocate(MIN, MAX);
    }

    @Test
    public void doesNotAllocateBlacklistedDisplays() {
        int badDisplay = MIN + 1;
        int range = MAX - MIN + 1 - 1; // one bad display
        allocator.blacklist(badDisplay);
        for (int i=0; i<range; i++) {
            assertThat(allocate(), not(equalTo(badDisplay)));
        }
    }

    @Test
    public void clearsBlacklistWhenAllDisplaysTaken() {
        int badDisplay = MIN + 1;
        allocator.blacklist(badDisplay);
        int range = MAX - MIN + 1 - 1;
        for (int i=0; i<range; i++) {
            assertThat(allocate(), not(equalTo(badDisplay)));
        }
        assertThat(allocate(), equalTo(badDisplay));
        // now all displays are taken
        try {
            allocate();
            fail("Expected exception because all displays are allocated, none received");
        } catch(RuntimeException e) {
        }
    }

    @Test
    public void freesPortWhenBlacklisted() {
        allocator.allocate(0, 1);
        int second = allocator.allocate(0, 1);
        allocator.blacklist(second);
        assertThat(allocator.allocate(0, 1), equalTo(second));
    }

    @Test
    public void canHandleWhenAllDisplaysAreBlacklisted() {
        int range = MAX - MIN + 1;
        for (int i=0; i<range; i++) {
            allocate();
            allocator.blacklist(i);
        }
        try {
            allocate();
        } catch(RuntimeException e) {
            fail("Blacklist should have been cleared.");
        }
    }

    @Test
    public void doesReturnAllNumbersInRangeInclusive() {
        int range = MAX - MIN + 1;
        int[] displays = new int[range];
        int[] expected = new int[range];
        for (int i=0; i<range; i++) {
            displays[i] = allocate();
            expected[i] = i;
        }
        Arrays.sort(displays);
        assertArrayEquals(expected, displays);
    }

}
