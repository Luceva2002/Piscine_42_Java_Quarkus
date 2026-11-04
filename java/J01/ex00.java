public class ex07{

    public static void main(String[] args){
        Pool p = new Pool(1, "Blu Club", "Milano", 80);
        System.out.println(p.getId() == (1));
        System.out.println(p.getName().equals("Blu Club"));
        System.out.println(p.getCity().equals("Milano"));
        System.out.println(p.getCapacity() == (80));
        try{
            new Pool(2, "X", "Y", -1);
            throw new AssertionError();
        }
        catch (IllegalArgumentException expected){
            //ok
        }
        System.out.println("OK");
    }
    static void check(boolean cond){
        if(!cond) throw new AssertionError();
    }
}

class Pool{
    private final int id;
    private final String name;
    private final String city;
    private final int capacity;

    Pool(int id, String name, String city, int capacity){
        if (capacity < 0) throw new IllegalArgumentException("capienza negativa");
        this.id = id;
        this.name = name;
        this.city = city;
        this.capacity = capacity;

    }

    int getId() { return id; };
    String getName() { return name; };
    String getCity() { return city; };
    int getCapacity() { return capacity; };


    @Override
    public String toString() {
        return "Pool{id=" + id + "\n, name='" + name + "'\n, city='" + city + "'\n, capacity=" + capacity + "}";
    }
}

