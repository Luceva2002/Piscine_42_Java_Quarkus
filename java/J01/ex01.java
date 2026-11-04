public class ex01 {

    public static void main(String[] args){
        GuestPublic g = new GuestPublic();
        System.out.println(g.id == null);
        System.out.println(g.phone == null);
        System.out.println(g.guests == 0);
        g.email = "a@x.it";
        System.out.println(g.email.equals("a@x.it"));

        GuestBean b = new GuestBean();
        System.out.println(b.getEmail() == null);
        b.setEmail("b@x.it");
        System.out.println(b.getEmail().equals("b@x.it"));
        System.out.println("OK");
    }

    static void check(boolean cond){
        if(!cond) throw new AssertionError();
    }
}

class GuestPublic{
    public String firstName;
    public String lastName;
    public String email;
    public String phone;
    public Integer id;
    public int guests;
}

class GuestBean{
    private String email;

    String getEmail() {return email;}
    void setEmail(String email) {this.email = email;}
}