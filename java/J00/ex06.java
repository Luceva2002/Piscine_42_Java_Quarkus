public class ex06 {
    public static void main(String[] args) {
        Integer a = 127, b = 127;
        System.out.println(a == b);

        Integer c = 1000, d = 1000;
        System.out.println(c == d);
        System.out.println(c.equals(d));

        Integer missing = null;
        try {
            int x = missing;
        } catch (NullPointerException e) {
            System.out.println("NullPointerException");
        }

        String s = new String("a");
        System.out.println(s == "a");
        System.out.println(s.equals("a"));
    }
}