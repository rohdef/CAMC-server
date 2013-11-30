package mcserver;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class MCServer implements PlaylistListener {

    public static final int DISCOVERY_PORT = 49155;
    public static final int VOTING_PORT = 49156;
    private Thread discoveryThread = null;
    private static final Logger logger = Logger.getLogger(MCServer.class.getName());
    private Voting voting;

    static {
        try {
            File file = new File("logs");
            file.mkdir();
            FileHandler handler = new FileHandler("logs/" + MCServer.class.getSimpleName() + ".log");
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Disabling logging to file", ex);
        } catch (SecurityException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String[] args) throws SocketException, IOException, InterruptedException {
        MCServer server = new MCServer();

        server.start();

        Thread.sleep(10000);

        server.shutdown();

    }

    public MCServer() {
        discoveryThread = new Thread(new DiscoveryService());
        voting = new Voting(4);
    }

    public void start() {
        discoveryThread.start();
    }

    public void shutdown() {
        logger.log(Level.INFO, "Server shutting down");
        discoveryThread.interrupt();
    }

    @Override
    public void withinThreshold() {
        //Open for voting
        voting.beginVoting();
        VotingService votingService = new VotingService();
        Thread votingThread = new Thread(votingService);
        votingThread.start();
        //Broadcast start of voting period
        try (DatagramSocket socket = new DatagramSocket()) {
            InetSocketAddress address = new InetSocketAddress("255.255.255.255", VOTING_PORT);
            byte[] message = new byte[0];
            socket.setBroadcast(true);
            DatagramPacket packet = new DatagramPacket(message, message.length);
            packet.setSocketAddress(address);
            socket.send(packet);
        } catch (IOException e) {
            logger.log(Level.SEVERE, null, e);
        }
        try {
            Thread.sleep(15000);
        } catch (InterruptedException ex) {
            Logger.getLogger(MCServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        votingThread.interrupt();
        voting.endVoting(this);
    }

    private class VotingService implements Runnable {

        public void run() {
            try (DatagramSocket socket = new DatagramSocket()) {
                InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), VOTING_PORT);
                socket.bind(address);
                //TODO:Decide if one packet per song is wasteful
                byte[] message = new byte[1024];
                DatagramPacket packet = new DatagramPacket(message, message.length);
                socket.setSoTimeout(1000);
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        socket.receive(packet);
                        String name = new String(packet.getData()).trim();
                        voting.addVote(name);
                    } catch (SocketTimeoutException e) {
                        logger.log(Level.FINE, "No votes received in 1 second interval");
                    }
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
    }

    private class DiscoveryService implements Runnable {

        //Broadcasts a packet containing server address to the LAN
        public void run() {
            try (DatagramSocket socket = new DatagramSocket()) {
                InetSocketAddress address = new InetSocketAddress("255.255.255.255", DISCOVERY_PORT);
                byte[] message = new byte[0];
                socket.setBroadcast(true);
                DatagramPacket packet = new DatagramPacket(message, message.length);
                packet.setSocketAddress(address);
                while (true) {
                    socket.send(packet);
                    Thread.sleep(1000);
                }
            } catch (IOException e) {
                //Port is in use, we don't know our own address or we could not send the broadcast for some reason. Either way, discovery won't work.
                logger.log(Level.SEVERE, null, e);
            } catch (InterruptedException e) {
                logger.log(Level.INFO, "Discovery service shutting down");
            }
        }
    }
}
