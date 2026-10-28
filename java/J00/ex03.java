public class ex03 {

    static int max(int[] a) {
        if (a.length == 0) 
            throw new IllegalArgumentException("array vuoto");
        int best = a[0];
        for (int i = 1; i < a.length; i++) {
            if (a[i] > best) best = a[i];
        }
        return best;
    }

    static int[] reverse(int[] a) {
        int[] r = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            r[a.length - 1 - i] = a[i];
        }
        return r;
    }

    static int count(int[] a, int x) {
        int n = 0;
        for (int v : a) {
            if (v == x) n++;
        }
        return n;
    }

    public static void main(String[] args) {
        check(max(new int[]{3, 9, 2}) == 9);
        check(java.util.Arrays.equals(reverse(new int[]{1, 2, 3}), new int[]{3, 2, 1}));
        check(count(new int[]{1, 2, 1, 1}, 1) == 3);
        try {
            max(new int[0]);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {

        }
        System.out.println("OK");
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}