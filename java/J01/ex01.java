public class ex01 {

    public static void main(String[] args){
        
    }
}

public class GuestPublic{
    public String firstName;
    public String lastName;
    public String email;
    public String phone;
    public Integer id;
    public int guests;
}

class GuestBean{
    private String email;

    String getEmail() {return email};
    void setEmail(String email) {this.email = email};
}