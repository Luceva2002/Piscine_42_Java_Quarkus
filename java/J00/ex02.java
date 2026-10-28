import jdk.dynalink.beans.StaticClass;

public class ex02{
    static String extractDomain(String email){
        if((email.indexOf('@') <= 0) || (email == null))
            throw new IllegalArgumentException("Dominio non valido");
        else{
           int num = email.indexOf('@');
           return email.substring(num + 1);
        }
    }

    public static void main(String[] email){
        String domain;
        domain = extractDomain(email[0]);
        System.out.println(domain);
    }
}