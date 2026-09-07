package org.example;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chat Server - Central hub for managing multiple client connections and message distribution
 *
 * This class implements the server-side functionality for the Java Chat Room project.
 * Features implemented according to project requirements:
 * 1. Multithreading: Each client connection handled in separate thread
 * 2. Automatic 5-digit ID assignment for new users (00000-99999)
 * 3. Message broadcasting to all connected clients
 * 4. Chat history storage on server side with file persistence
 * 5. Delivery of chat history to new users upon joining
 * 6. User join/leave notifications broadcast to all users
 * 7. Timestamp inclusion with all messages
 *
 * Multithreading implementation: Uses thread-safe collections and synchronized methods
 */
public class Server {
    // Thread-safe collection for active client handlers (Vector provides synchronization)
    private static final Vector<ClientHandler> activeClients = new Vector<>();

    // In-memory storage for chat history (synchronized access)
    private static final List<String> chatHistory = new ArrayList<>();

    // Atomic counter for generating unique 5-digit client IDs (00000-99999)
    private static final AtomicInteger clientIdCounter = new AtomicInteger(0);

    // File path for persistent chat history storage
    private static final String HISTORY_FILE = "chat_history.txt";

    // Network port for server socket (default: 6666)
    private static final int PORT = 6666;

    /**
     * Main entry point for the Server application
     *
     * Initializes server, loads existing chat history, and listens for client connections
     * Implements multithreading requirement by creating separate thread for each client
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        System.out.println("=== Java Chat Server ===");
        System.out.println("Port: " + PORT);
        System.out.println("History file: " + HISTORY_FILE);
        System.out.println("Server started. Waiting for clients...\n");

        // Load previous chat history from file (project requirement: chat record storage)
        loadHistory();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            // Infinite loop to accept incoming client connections
            while (true) {
                // Accept new client connection (blocks until client connects)
                Socket clientSocket = serverSocket.accept();

                // Generate unique 5-digit ID for new client (project requirement)
                String clientId = String.format("%05d", clientIdCounter.getAndIncrement());

                System.out.println("New client connected. Assigned ID: " + clientId);

                // Create handler for this client with I/O streams
                ClientHandler clientHandler = new ClientHandler(
                        clientSocket,
                        clientId,
                        new DataInputStream(clientSocket.getInputStream()),
                        new DataOutputStream(clientSocket.getOutputStream())
                );

                // Add to active clients list for message broadcasting
                activeClients.add(clientHandler);

                // Start new thread to handle client communication (multithreading requirement)
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Broadcasts message to all connected clients except the sender
     * Implements project requirements:
     * - Message broadcasting to all command-line interfaces
     * - Display current time with all messages
     * - Chat history storage on server side
     *
     * @param message The message content to broadcast (without timestamp)
     * @param excludeClient The client who sent the message (excluded from broadcast)
     */
    public static synchronized void broadcastMessage(String message, ClientHandler excludeClient) {
        // Add current timestamp to message (project requirement: display current time)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formattedMessage = "[" + timestamp + "] " + message;

        // Store message in server-side chat history (project requirement)
        chatHistory.add(formattedMessage);
        saveToHistoryFile(formattedMessage);

        // Broadcast to all active clients except the sender
        for (ClientHandler client : activeClients) {
            if (client != excludeClient && client.isActive()) {
                try {
                    client.sendMessage(formattedMessage);
                } catch (IOException e) {
                    System.err.println("Failed to send message to client " + client.getClientId());
                }
            }
        }
    }

    /**
     * Removes client from active clients list when they disconnect
     *
     * @param clientHandler The client handler to remove
     */
    public static synchronized void removeClient(ClientHandler clientHandler) {
        activeClients.remove(clientHandler);
        System.out.println("Client " + clientHandler.getClientId() + " removed. Active clients: " + activeClients.size());
    }

    /**
     * Generates formatted list of currently online users for new clients
     * Implements project requirement: Display names and IDs of other users to new user
     *
     * @param excludeClient The new client (excluded from the list)
     * @return Formatted string containing online users list
     */
    public static synchronized String getOnlineUsersForNewClient(ClientHandler excludeClient) {
        StringBuilder sb = new StringBuilder();

        // Count only other users (excluding the new client)
        int otherUserCount = 0;
        for (ClientHandler client : activeClients) {
            if (client != excludeClient && client.isActive() && client.getUsername() != null) {
                otherUserCount++;
            }
        }

        sb.append("=== Users currently in chat room (").append(otherUserCount).append(") ===\n");

        if (otherUserCount == 0) {
            sb.append("  No other users in the chat room.\n");
        } else {
            for (ClientHandler client : activeClients) {
                if (client != excludeClient && client.isActive() && client.getUsername() != null) {
                    sb.append("  ").append(client.getUsername())
                            .append(" (ID: ").append(client.getClientId()).append(")\n");
                }
            }
        }

        sb.append("===================================\n");
        return sb.toString();
    }

    /**
     * Retrieves recent chat history for new users
     * Implements project requirement: Server delivers messages to client when entering chatroom
     *
     * @return Formatted string containing last 50 messages from chat history
     */
    public static synchronized String getChatHistory() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Recent Chat History ===\n");

        // Display last 50 messages (or all if less than 50)
        int start = Math.max(0, chatHistory.size() - 50);
        for (int i = start; i < chatHistory.size(); i++) {
            sb.append(chatHistory.get(i)).append("\n");
        }

        if (chatHistory.isEmpty()) {
            sb.append("  No messages yet\n");
        }

        sb.append("===========================\n");
        return sb.toString();
    }

    /**
     * Loads chat history from persistent storage file
     * Used when server starts to restore previous chat sessions
     */
    private static void loadHistory() {
        File file = new File(HISTORY_FILE);
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                chatHistory.add(line);
            }
            System.out.println("Loaded " + chatHistory.size() + " messages from history.");
        } catch (IOException e) {
            System.err.println("Failed to load history: " + e.getMessage());
        }
    }

    /**
     * Appends a single message to the persistent chat history file
     * Implements project requirement: Chat record storage on server side
     *
     * @param message The formatted message to save (includes timestamp)
     */
    private static synchronized void saveToHistoryFile(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(HISTORY_FILE, true))) {
            writer.write(message);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to save message to history file: " + e.getMessage());
        }
    }
}

/**
 * ClientHandler - Manages communication with a single connected client
 *
 * Each instance runs in its own thread, handling all I/O operations for one client.
 * Implements Runnable interface for multithreading support.
 *
 * Responsibilities:
 * - User authentication and ID assignment
 * - Message reception from client
 * - Message broadcasting to other clients
 * - Client lifecycle management (join/leave notifications)
 */
class ClientHandler implements Runnable {
    // Network connection components
    private final Socket socket;
    private final String clientId;
    private final DataInputStream input;
    private final DataOutputStream output;

    // User information
    private String username;
    private boolean active;

    /**
     * Constructor for ClientHandler
     *
     * @param socket The client's network socket
     * @param clientId The 5-digit ID assigned to this client
     * @param input DataInputStream for receiving messages from client
     * @param output DataOutputStream for sending messages to client
     */
    public ClientHandler(Socket socket, String clientId, DataInputStream input, DataOutputStream output) {
        this.socket = socket;
        this.clientId = clientId;
        this.input = input;
        this.output = output;
        this.active = true;
    }

    /**
     * Main execution method for client handler thread
     *
     * Process flow:
     * 1. Display online users to new client
     * 2. Request and receive username from client
     * 3. Send welcome message with assigned ID
     * 4. Send recent chat history
     * 5. Broadcast join notification to all other clients
     * 6. Enter main message processing loop
     */
    @Override
    public void run() {
        try {
            // Step 1: Show current online users to new client (project requirement)
            String onlineUsers = Server.getOnlineUsersForNewClient(this);
            output.writeUTF(onlineUsers);

            // Step 2: Request username from client (project requirement: user can input username)
            output.writeUTF("SERVER: Please enter your username:");
            this.username = input.readUTF().trim();

            if (username.isEmpty()) {
                username = "User_" + clientId;
            }

            System.out.println("Client " + clientId + " set username to: " + username);

            // Step 3: Send welcome message with assigned 5-digit ID
            String welcomeMsg = "SERVER: Welcome " + username + "! Your ID: " + clientId;
            output.writeUTF(welcomeMsg);

            // Step 4: Send chat history to new client (project requirement)
            output.writeUTF(Server.getChatHistory());

            // Step 5: Broadcast join notification to all other clients
            String joinMsg = username + " (ID: " + clientId + ") has joined the chat.";
            Server.broadcastMessage("[SYSTEM] " + joinMsg, this);

            System.out.println("[SYSTEM] " + username + " (ID: " + clientId + ") joined.");

            // Step 6: Main message processing loop
            while (active) {
                try {
                    String message = input.readUTF();

                    if (message.equalsIgnoreCase("/exit")) {
                        // Client requested graceful disconnect
                        handleLogout();
                        break;
                    } else if (message.startsWith("/")) {
                        // Handle server-side commands
                        handleCommand(message);
                    } else {
                        // Normal chat message - broadcast to all other clients
                        String fullMessage = username + " (ID: " + clientId + "): " + message;
                        Server.broadcastMessage(fullMessage, this);
                    }
                } catch (EOFException | SocketException e) {
                    // Client disconnected unexpectedly (network error or forceful close)
                    System.out.println("Client " + username + " disconnected unexpectedly.");
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println("Error with client " + clientId + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Handles graceful client logout process
     *
     * Sends logout confirmation to client and broadcasts leave notification to all users
     * Implements project requirement: Display name and ID when user leaves chatroom
     *
     * @throws IOException if network communication fails
     */
    private void handleLogout() throws IOException {
        String leaveMsg = "[SYSTEM] " + username + " (ID: " + clientId + ") has left the chat.";
        Server.broadcastMessage(leaveMsg, this);
        output.writeUTF("SERVER: Goodbye!");
        System.out.println("[SYSTEM] " + username + " (ID: " + clientId + ") left.");
    }

    /**
     * Processes client commands (messages starting with '/')
     *
     * Supported commands:
     * - /users: Display list of all online users
     * - /history: Display recent chat history
     * - /help: Display available commands
     *
     * @param command The command string received from client
     * @throws IOException if network communication fails
     */
    private void handleCommand(String command) throws IOException {
        switch (command.toLowerCase()) {
            case "/users":
                // Send list of all online users (including current user)
                output.writeUTF(Server.getOnlineUsersForNewClient(null).replace(
                        "=== Users currently in chat room", "=== All online users"));
                break;
            case "/history":
                // Send recent chat history
                output.writeUTF(Server.getChatHistory());
                break;
            case "/help":
                output.writeUTF("Commands: /users - show online users, /history - show chat history, /exit - leave chat");
                break;
            default:
                output.writeUTF("SERVER: Unknown command. Type /help for available commands.");
        }
    }

    /**
     * Sends a message to this specific client
     *
     * @param message The formatted message to send
     * @throws IOException if network communication fails
     */
    public void sendMessage(String message) throws IOException {
        output.writeUTF(message);
    }

    /**
     * Cleans up resources when client disconnects
     *
     * Closes network streams and socket, removes client from active list
     */
    private void cleanup() {
        active = false;
        Server.removeClient(this);

        try {
            input.close();
            output.close();
            socket.close();
        } catch (IOException e) {
            System.err.println("Error closing resources for client " + clientId);
        }
    }

    // Accessor methods for client information
    public String getClientId() { return clientId; }
    public String getUsername() { return username; }
    public boolean isActive() { return active; }
}