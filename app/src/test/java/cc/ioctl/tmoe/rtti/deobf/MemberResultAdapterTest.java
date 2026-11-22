package cc.ioctl.tmoe.rtti.deobf;

import org.junit.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class MemberResultAdapterTest {
    public static class NamedSparse {
        final Map<Long, Object> values = new LinkedHashMap<>();
        public NamedSparse(int capacity) {}
        public void put(long id, Object value) { values.put(id, value); }
        public void append(long id, Object value) { throw new AssertionError("append must not be used"); }
    }

    public static class OptimizedSparse {
        final Map<Long, Object> values = new LinkedHashMap<>();
        public OptimizedSparse(int capacity) {}
        public void k(Object value, long id) { values.put(id, value); }
        public void a(long id, Long value) { throw new AssertionError("specialized append must not be used"); }
    }

    public static class NamedAdapter {
        Map<Long, Object> received;
        ArrayList<?> list;
        boolean notify;
        private void showUsersResult(ArrayList<?> list, NamedSparse sparse, boolean notify) {
            this.list = list;
            this.received = sparse.values;
            this.notify = notify;
        }
    }

    public static class OptimizedAdapter {
        Map<Long, Object> received;
        ArrayList<?> list;
        boolean notify;
        private void y(OptimizedSparse sparse, ArrayList<?> list, boolean notify) {
            this.list = list;
            this.received = sparse.values;
            this.notify = notify;
        }
    }

    @Test
    public void namedHostReceivesMembersAndTheirIds() throws Exception {
        NamedAdapter target = new NamedAdapter();
        MemberResultAdapter adapter = new MemberResultAdapter(NamedAdapter.class.getDeclaredMethod(
                "showUsersResult", ArrayList.class, NamedSparse.class, boolean.class));
        ArrayList<String> members = new ArrayList<>(List.of("Alice", "Bob"));
        adapter.show(target, members, new long[]{123, 456});
        assertSame(members, target.list);
        assertEquals(Map.of(123L, "Alice", 456L, "Bob"), target.received);
        assertTrue(target.notify);
    }

    @Test
    public void optimizedHostReceivesMembersAfterParameterReordering() throws Exception {
        OptimizedAdapter target = new OptimizedAdapter();
        MemberResultAdapter adapter = new MemberResultAdapter(OptimizedAdapter.class.getDeclaredMethod(
                "y", OptimizedSparse.class, ArrayList.class, boolean.class));
        ArrayList<String> members = new ArrayList<>(List.of("Alice", "Bob"));
        adapter.show(target, members, new long[]{123, 456});
        assertSame(members, target.list);
        assertEquals(Map.of(123L, "Alice", 456L, "Bob"), target.received);
        assertTrue(target.notify);
    }
}
