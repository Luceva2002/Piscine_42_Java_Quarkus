import jdk.dynalink.beans.StaticClass;

public class ex02{
    static String extractDomain(String email){
        if(email.indexOf('@') <= 0)
            return IllegalArgumentException("Dominio non valido");
        else{
           int num = email.indexOf('@');
           String domain = email.substring(num + 1);
           return domain;
        }
    }

    static String IllegalArgumentException(String ciccio) {
        return(ciccio);
    }

    public static void main(String[] email){
        String domain;
        domain = extractDomain(email[0]);
        System.out.println(domain);
    }
}