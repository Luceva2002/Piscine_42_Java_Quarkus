public class ex01{

    static String normalizePhone(String phone){
        if(phone == null)
            return null;
        String[] nospace = phone.trim().split("\\s+");
        return (String.join("", nospace));
    }

    static String normalizePhone2(String phone){
        return (phone == null ? null : phone.replaceAll("\\s+", ""));
    }

    public static void main(String[] phone){
        System.out.println(normalizePhone(phone[0]));
        System.out.println(normalizePhone2(phone[0]));
    }

    static void check(boolean cond) {
        if (!cond) throw new AssertionError();
    }
}