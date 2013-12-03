package mcserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Voting {

    private Map<String, Integer> songsToVotes;
    private Set<String> currentlyPlaying;
    private Map<String, Integer> recentlyPlayed;
    private int songBanDuration;
    private Thread currentPlayThread;

    public Voting(int songBanDuration) {
        songsToVotes = new HashMap<>();
        recentlyPlayed = new HashMap<>();
        currentlyPlaying = new HashSet<>();
        this.songBanDuration = songBanDuration;
    }

    public void beginVoting() {
        songsToVotes = new HashMap<>();
    }

    public synchronized void addVote(String name) {
        Integer votes = songsToVotes.get(name);
        if (votes == null) {
            votes = 0;
        }
        songsToVotes.put(name, ++votes);
    }

    /*
     * Updates the banned list, selects vote winners, creates new playlist thread and starts it when the old one is dead.
     * Currently fakes song lengths to 5 seconds, limits playlist length to at most 5 songs
     */
    public synchronized void endVoting(PlaylistListener listener) {
        //Decrement counters for all recently played songs, remove songs with counters at 0
        for (String s : recentlyPlayed.keySet()) {
            int votesSincePlayed = recentlyPlayed.get(s);
            --votesSincePlayed;
            if (votesSincePlayed == 0) {
                recentlyPlayed.remove(s);
            } else {
                recentlyPlayed.put(s, votesSincePlayed);
            }
        }
        //Insert current tracklist in recently played. Number maybe should be configurable
        for (String s : currentlyPlaying) {
            recentlyPlayed.put(s, songBanDuration);
        }
        currentlyPlaying = new HashSet<>();
        List<SongWithVotes> candidates = new ArrayList<>();
        for (String s : songsToVotes.keySet()) {
            if (!recentlyPlayed.containsKey(s)) {
                candidates.add(new SongWithVotes(s, songsToVotes.get(s)));
            }
        }
        Collections.sort(candidates, Collections.reverseOrder());
        for(SongWithVotes s : candidates){
            System.out.println(s.name + " with " + s.votes);
        }
        List<BasicSong> songs = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            if (i > 4) {
                break; //Configurable playlist length?
            }
            BasicSong song = new BasicSong(candidates.get(i).name, 5);
            songs.add(song);
        }
        if (currentPlayThread != null) {
            try {
                currentPlayThread.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(Voting.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        Logger.getLogger(Voting.class.getName()).log(Level.INFO, "Voting ended");
        BasicPlaylistManager manager = new BasicPlaylistManager(listener, songs);
        currentPlayThread = new Thread(manager);
        currentPlayThread.start();
    }

    public void shutdown() {
        if (currentPlayThread != null) {
            currentPlayThread.interrupt();
        }
    }

    private class SongWithVotes implements Comparable<SongWithVotes> {

        private String name;
        private int votes;

        public SongWithVotes(String name, int votes) {
            this.name = name;
            this.votes = votes;
        }

        @Override
        public int compareTo(SongWithVotes other) {
            return votes - other.votes;
        }
    }
}
