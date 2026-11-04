public class ex00{

    public static void main(String[] args){
        Pool p = new Pool(1, "Blu Club", "Milano", 80);

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
    }

    int getId() { return id; };
    String getName() { return name; };
    String getCity() { return city; };
    int getCapacity() { return capacity; };


    @Override
    public STring toString() {
        return "Pool{id=" + id + "\n, name='" + name + "'\n, city='" + city + "'\n, capacity=" + capacity + "}";
    }
}