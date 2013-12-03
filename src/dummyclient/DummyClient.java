package dummyclient;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DummyClient implements Runnable {

    public static final int DISCOVERY_PORT = 49155;
    public static final int VOTING_PORT = 50032;
    public static final Logger logger = Logger.getLogger(DummyClient.class.getName());

    /*
     * Creates 100 clients that vote for 3 songs each.
     */
    public static void main(String[] args) {
        List<String> s = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            s.add("Generic pop song #" + i);
        }
        List<Thread> threads = new ArrayList<>();
        InetAddress server = doDiscovery();
        for (int i = 0; i < 10; i++) {
            List<String> songs = new ArrayList<>();
            songs.add(s.get(i));
            songs.add(s.get(i + 1));
            songs.add(s.get(i + 2));
            threads.add(new Thread(new DummyClient(songs, server)));
        }
        List<String> lol = new ArrayList<>();
        logger.log(Level.INFO, "{0} and {1} should win.", new Object[]{s.get(5), s.get(6)});
        lol.add(s.get(5));
        lol.add(s.get(6));
        threads.add(new Thread(new DummyClient(lol, server)));
        waitForVoting();
        for (Thread t : threads) {
            t.start();
        }
    }
    private List<String> songs;
    private InetAddress serverAddress;

    public DummyClient(List<String> songs, InetAddress serverAddress) {
        this.songs = new ArrayList<>(songs);
        this.serverAddress = serverAddress;
    }

    /*
     * Currently assumes that you know the server address (see main or comments below)
     * Votes for client songs
     */
    @Override
    public void run() {
        //Listen for discovery service
        //Doing it in main so I can run multiple clients on one machine.
        /*InetAddress serverAddress = doDiscovery();
         logger.log(Level.INFO, "Found server at " + serverAddress);
         //Listen for voting to begin
         waitForVoting();
         logger.log(Level.INFO, "Voting started");*/
        //Can be done arbitrarily many times per voting period
        try (DatagramSocket socket = new DatagramSocket()) {
                throwVotes(serverAddress);
        } catch (SocketException e) {
            logger.log(Level.SEVERE, null, e);
        }
        logger.log(Level.INFO, "Threw vote");
    }

    /*
     * Pack the song name into a datagrampacket and send it to the server
     */
    public void throwVotes(InetAddress serverAddress) {
        try (Socket socket = new Socket(serverAddress, VOTING_PORT)){
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                for (String s : songs) {
                    writer.write(s);
                    writer.newLine();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(DummyClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /*public void throwVote(DatagramSocket socket, String songName, InetAddress serverAddress) {
     try {
     //Just set the message size to this to avoid weird trailing number errors on the server
     byte[] message = new byte[1024];
     System.arraycopy(songName.getBytes(), 0, message, 0, songName.getBytes().length);
     DatagramPacket packet = new DatagramPacket(message, message.length);
     packet.setSocketAddress(new InetSocketAddress(serverAddress, VOTING_PORT));
     socket.send(packet);
     } catch (SocketException ex) {
     logger.log(Level.SEVERE, null, ex);
     } catch (IOException ex) {
     logger.log(Level.SEVERE, null, ex);
     }
     }*/

    /*
     * Listens for the discovery broadcast.
     */
    //This isn't necessarily needed, I included it in case we need clients to know server IP in advance
    public static InetAddress doDiscovery() {
        InetAddress res = null;
        //Server sends empty packet, address can be read off from the DatagramPacket properties
        try {
            DatagramPacket packet = receiveEmptyBroadcast(DISCOVERY_PORT);
            res = packet.getAddress();
        } catch (SocketException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        } //Real thing needs proper error handling of course
        return res;
    }

    /*
     * Listens for the voting broadcast
     */
    public static void waitForVoting() {
        try {
            receiveEmptyBroadcast(VOTING_PORT); //Get address from here if you're not using discovery
        } catch (SocketException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }
    /*
     * Helper function to listen for broadcasts on a specified port.
     */
    //In the real client, you should probably reuse the socket

    public static DatagramPacket receiveEmptyBroadcast(int port) throws SocketException, IOException {
        DatagramSocket socket = new DatagramSocket(null);
        InetSocketAddress address = new InetSocketAddress("0.0.0.0", port);
        socket.bind(address);
        byte[] serverAck = new byte[0];
        DatagramPacket serverAckPacket = new DatagramPacket(serverAck, serverAck.length);
        socket.receive(serverAckPacket);
        socket.close();
        return serverAckPacket;
    }
}
