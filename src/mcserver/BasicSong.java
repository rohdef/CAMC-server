
package mcserver;

public class BasicSong {
    private String name;
    private int duration;
    //Add more as necessary
    
    public BasicSong(String name, int duration) {
        this.name = name;
        this.duration = duration;
    }

    public String getName(){
        return name;
    }
    
    public int getDuration(){
        return duration;
    }
}
