package Project.Server;

import Project.Common.ConnectionPayload;
import Project.Common.LoggerUtil;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.RollPayload;
import Project.Common.RoomResultsPayload;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A server-side representation of a single client.
 * This class is more about the data and abstracted communication
 */
public class ServerThread extends BaseServerThread {
    public static final long DEFAULT_CLIENT_ID = -1;
    private Room currentRoom;
    private long clientId;
    private String clientName;
    private Consumer<ServerThread> onInitializationComplete; // callback to inform when this object is ready
    private Set<String> mutedUsers = new HashSet<>();
    private static final String MUTE_LIST_DIRECTORY = "mute_lists";


    


    /**
     * Wraps the Socket connection and takes a Server reference and a callback
     * 
     * @param myClient
     * @param server
     * @param onInitializationComplete method to inform listener that this object is
     *                                 ready
     */
    protected ServerThread(Socket myClient, Consumer<ServerThread> onInitializationComplete) {
        Objects.requireNonNull(myClient, "Client socket cannot be null");
        Objects.requireNonNull(onInitializationComplete, "callback cannot be null");
        info("ServerThread created");
        // get communication channels to single client
        this.client = myClient;
        this.clientId = ServerThread.DEFAULT_CLIENT_ID;// this is updated later by the server
        this.onInitializationComplete = onInitializationComplete;

    }


    //st278 and 07/27/24
    public void setClientName(String name) {
        if (name == null) {
            throw new NullPointerException("Client name can't be null");
        }
        this.clientName = name;
        loadMuteList(); // Loads the mute list after setting the client name
        onInitialized();
    }

    public String getClientName() {
        return clientName;
    }

    public long getClientId() {
        return this.clientId;
    }

    protected Room getCurrentRoom() {
        return this.currentRoom;
    }

    protected void setCurrentRoom(Room room) {
        if (room == null) {
            throw new NullPointerException("Room argument can't be null");
        }
        currentRoom = room;
    }

    @Override
    protected void onInitialized() {
        onInitializationComplete.accept(this); // Notify server that initialization is complete
    }

    @Override
    protected void info(String message) {
        LoggerUtil.INSTANCE.info(String.format("ServerThread[%s(%s)]: %s", getClientName(), getClientId(), message));
    }

    @Override
    protected void cleanup() {
        currentRoom = null;
        super.cleanup();
    }

    @Override
    protected void disconnect() {
        // sendDisconnect(clientId, clientName);
        super.disconnect();
    }

    // handle received message from the Client
    @Override
    protected void processPayload(Payload payload) {
        LoggerUtil.INSTANCE.fine("Received Payload: " + payload);
        try {
            switch (payload.getPayloadType()) {
                case CLIENT_CONNECT:
                    ConnectionPayload cp = (ConnectionPayload) payload;
                    setClientName(cp.getClientName());
                    break;
                case MESSAGE:
                    currentRoom.sendMessage(this, payload.getMessage());
                    break;
                case ROOM_CREATE:
                    currentRoom.handleCreateRoom(this, payload.getMessage());
                    break;
                case ROOM_JOIN:
                    currentRoom.handleJoinRoom(this, payload.getMessage());
                    break;
                case ROOM_LIST:
                    currentRoom.handleListRooms(this, payload.getMessage());
                    break;
                case DISCONNECT:
                    currentRoom.disconnect(this);
                    break;
                
                // /* 
                // Commented Out to test
                case ROLL:
                    RollPayload rollPayload = (RollPayload) payload;
                    currentRoom.handleRoll(this, rollPayload);
                    break;
                case FLIP:
                    currentRoom.handleFlip(this);
                    break;
                // */

                //st278 and 07/24/24
                case PRIVATE_MESSAGE:
                    currentRoom.sendPrivateMessage(this, payload.getClientId(), payload.getMessage());
                    break;


                //st278 and 07/24/24
                case MUTE:
                    currentRoom.handleMute(this, payload.getClientId());
                    break;
                case UNMUTE:
                    currentRoom.handleUnmute(this, payload.getClientId());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("Could not process Payload: " + payload,e);
        
        }
    }

    // send methods to pass data back to the Client

    public boolean sendRooms(List<String> rooms) {
        RoomResultsPayload rrp = new RoomResultsPayload();
        rrp.setRooms(rooms);
        return send(rrp);
    }

    public boolean sendClientSync(long clientId, String clientName) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        cp.setConnect(true);
        cp.setPayloadType(PayloadType.SYNC_CLIENT);
        return send(cp);
    }

    /**
     * Overload of sendMessage used for server-side generated messages
     * 
     * @param message
     * @return @see {@link #send(Payload)}
     */
    public boolean sendMessage(String message) {
        return sendMessage(ServerThread.DEFAULT_CLIENT_ID, message);
    }

    /**
     * Sends a message with the author/source identifier
     * 
     * @param senderId
     * @param message
     * @return @see {@link #send(Payload)}
     */
    public boolean sendMessage(long senderId, String message) {
        Payload p = new Payload();
        p.setClientId(senderId);
        p.setMessage(message);
        p.setPayloadType(PayloadType.MESSAGE);
        return send(p);
    }

    /**
     * Tells the client information about a client joining/leaving a room
     * 
     * @param clientId   their unique identifier
     * @param clientName their name
     * @param room       the room
     * @param isJoin     true for join, false for leaivng
     * @return success of sending the payload
     */
    public boolean sendRoomAction(long clientId, String clientName, String room, boolean isJoin) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setPayloadType(PayloadType.ROOM_JOIN);
        cp.setConnect(isJoin); // <-- determine if join or leave
        cp.setMessage(room);
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        return send(cp);
    }

    /**
     * Tells the client information about a disconnect (similar to leaving a room)
     * 
     * @param clientId   their unique identifier
     * @param clientName their name
     * @return success of sending the payload
     */
    public boolean sendDisconnect(long clientId, String clientName) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setPayloadType(PayloadType.DISCONNECT);
        cp.setConnect(false);
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        return send(cp);
    }

    /**
     * Sends (and sets) this client their id (typically when they first connect)
     * 
     * @param clientId
     * @return success of sending the payload
     */
    public boolean sendClientId(long clientId) {
        this.clientId = clientId;
        ConnectionPayload cp = new ConnectionPayload();
        cp.setPayloadType(PayloadType.CLIENT_ID);
        cp.setConnect(true);
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        return send(cp);
    }

    // end send methods


    //st278 and 07/28/24
    public boolean addMutedUser(String username) {
        boolean added = mutedUsers.add(username);
        if (added) {
            saveMuteList();
            LoggerUtil.INSTANCE.info(clientName + " muted user: " + username);
            notifyUserOfMuteStatus(username, true);
        }
        return added;
    }
    
    public boolean removeMutedUser(String username) {
        boolean removed = mutedUsers.remove(username);
        if (removed) {
            saveMuteList();
            LoggerUtil.INSTANCE.info(clientName + " unmuted user: " + username);
            notifyUserOfMuteStatus(username, false);
        }
        return removed;
    }
    
    private void notifyUserOfMuteStatus(String targetUsername, boolean isMuted) {
        String message = String.format("%s %s you", clientName, isMuted ? "muted" : "unmuted");
        currentRoom.sendPrivateSystemMessage(this, targetUsername, message);
    }

    public boolean isUserMuted(String username) {
        return mutedUsers.contains(username);
    }
    
    
    //st278 and 07/28/24
    private void saveMuteList() {
        String fileName = MUTE_LIST_DIRECTORY + File.separator + clientName + ".txt";
        try {
            Files.write(Paths.get(fileName), mutedUsers);
            LoggerUtil.INSTANCE.info("Saved mute list for " + clientName);
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("Error saving mute list for " + clientName, e);
        }
    }

    //st278 and 07/27/24
    private void loadMuteList() {
    String fileName = MUTE_LIST_DIRECTORY + File.separator + clientName + ".txt";
        try {
            List<String> lines = Files.readAllLines(Paths.get(fileName));
            mutedUsers.clear(); // Clear existing mute list before loading
            mutedUsers.addAll(lines);
            LoggerUtil.INSTANCE.info("Loaded mute list for " + clientName);
        } catch (IOException e) {
            LoggerUtil.INSTANCE.info("No existing mute list found for " + clientName + ". Starting with an empty list.");
        }
    }
}
