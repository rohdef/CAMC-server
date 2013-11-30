
package mcserver;

public interface PlaylistListener {
    
    //PlaylistManagers call this when the playlist is configurably close to ending
    public void withinThreshold();
}
