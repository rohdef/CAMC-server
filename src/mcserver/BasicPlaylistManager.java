
package mcserver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class BasicPlaylistManager implements Runnable {

    private List<BasicSong> playlist;
    private int currentSong = 0;
    private PlaylistListener listener;
    private static final Logger logger = Logger.getLogger(BasicPlaylistManager.class.getName());
    
    static{
        try {
            File file = new File("logs");
            file.mkdir();
            FileHandler handler = new FileHandler("logs/"+BasicPlaylistManager.class.getSimpleName()+".log");
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Disabling logging to file", ex);
        } catch (SecurityException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    
    public BasicPlaylistManager(PlaylistListener listener, List<BasicSong> songs){
        playlist = new ArrayList<>();
        playlist.addAll(songs);
        this.listener = listener;
    }
    
    public synchronized String getCurrentSong(){
        return playlist.get(currentSong).getName();
    }
    
    public List<BasicSong> getPlaylist(){
        ArrayList<BasicSong> res = new ArrayList<>();
        res.addAll(playlist);
        return res;
    }
    
    public void run(){
        for(int i = 0; i < playlist.size(); i++){
            synchronized(this){
                currentSong = i;
            }
            logger.log(Level.INFO, "Now playing \"" + playlist.get(i).getName() + "\"");
            int duration = playlist.get(i).getDuration();
            //Pretend there's code to ask media player to play the song here
            //Also to remove a song if the media player can't acquire it
            if(i == playlist.size()-1){
                try {
                    //On the last song trigger voting halfway through
                    Thread.sleep(duration*500);
                    listener.withinThreshold();
                    Thread.sleep(duration*500); //This is probably not robust
                } catch (InterruptedException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
            try {
                Thread.sleep(duration*1000);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }
}
