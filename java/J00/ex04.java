public class ex04 {

    static String statusName(int code) {
        return switch (code) {
            case 1 -> "Iscrizioni aperte";
                case 2 -> "Iscrizioni chiuse";
            case 3 -> "evento chiuso";
            default -> throw new IllegalArgumentException("stato sconosciuto: " + code);
        };
    }

    static String statusNamelseif(int code){
        if(code == 1)
            return ("Iscrizioni Aperte");
        else if(code == 2)
            return("Iscrizioni Chiuse");
        else if(code == 3)
            return("Evento chiuso");
        else
            throw new IllegalArgumentException("stato sconosciuto: " + code);
    }

    public static void main(String[] args){
        String ciccio = statusName(Integer.valueOf(args[0]));
        System.out.println(ciccio);
        ciccio = statusNamelseif(Integer.valueOf(args[0]));
        System.out.println(ciccio);

    }
}