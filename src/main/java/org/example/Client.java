package org.example;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat Client - Provides command-line interface for users to connect to the chat server
 *
 * This class implements the client-side functionality for the Java Chat Room project.
 * Features implemented according to project requirements:
 * 1. Connects to server and receives automatically assigned 5-digit user ID
 * 2. Allows user to input custom username via keyboard
 * 3. Sends and receives messages in real-time with all other clients
 * 4. Displays timestamps with all sent and received messages
 * 5. Shows user join/leave notifications for all users in chatroom
 * 6. Provides /save command to export chat history to local desktop file
 *
 * Multithreading implementation: Uses separate threads for message sending and receiving
 */
public class Client {
    // Server connection parameters
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 6666;

    // Network communication components
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;

    // User identification information
    private String username = "User";
    private String clientId = "00000";

    // Thread synchronization for console output control
    private final Object outputLock = new Object();
    private boolean isWaitingForInput = false;

    // Local storage for chat history (used for /save command)
    private List<String> chatHistory = new ArrayList<>();

    // Desktop path for saving chat history files
    private static final String DESKTOP_PATH = System.getProperty("user.home") + File.separator + "Desktop";

    // Time formatter for local message timestamps
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Main entry point for the Client application
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        new Client().start();
    }

    /**
     * Starts the client application and establishes connection to server
     * Handles connection lifecycle including setup, message exchange, and cleanup
     */
    public void start() {
        try {
            // Establish TCP connection to chat server
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            System.out.println("=== Java Chat Client ===");
            System.out.println("Connected to server at " + SERVER_HOST + ":" + SERVER_PORT);
            System.out.println("Type /exit to leave, /help for commands, /save to save chat history\n");

            // Perform initial handshake with server (username setup, ID assignment)
            handleServerGreeting();

            // Start background thread for receiving messages from server
            Thread receiver = new Thread(this::receiveMessages);
            receiver.start();

            // Start main thread for sending user messages
            sendMessages();

            // Cleanup when done
            receiver.interrupt();
            socket.close();

        } catch (ConnectException e) {
            System.err.println("Cannot connect to server. Make sure server is running on " + SERVER_HOST + ":" + SERVER_PORT);
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("\nDisconnected from server.");
        }
    }

    /**
     * Handles initial communication with server upon connection
     * Implements project requirement: Display names and IDs of other users to new user
     *
     * Process steps:
     * 1. Receive and display list of currently online users
     * 2. Prompt user for username input
     * 3. Send username to server and receive assigned 5-digit ID
     * 4. Receive welcome message with ID confirmation
     * 5. Receive recent chat history from server
     *
     * @throws IOException if network communication fails
     */
    private void handleServerGreeting() throws IOException {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        // Step 1: Server sends list of currently online users (project requirement)
        String onlineUsers = input.readUTF();
        System.out.println(onlineUsers);

        // Step 2: Server prompts for username
        String serverPrompt = input.readUTF();
        System.out.print(serverPrompt + " ");

        // Step 3: User inputs username (project requirement: user can input username)
        this.username = consoleReader.readLine();
        output.writeUTF(username);

        // Step 4: Server responds with welcome message containing assigned 5-digit ID
        String welcomeMsg = input.readUTF();
        System.out.println(welcomeMsg);

        // Extract 5-digit client ID from welcome message (project requirement: 5-digit ID)
        extractClientIdFromWelcome(welcomeMsg);

        // Step 5: Server sends recent chat history (project requirement: chat record storage)
        String history = input.readUTF();
        System.out.println(history);

        // Store initial messages in local chat history for potential export
        synchronized (chatHistory) {
            chatHistory.add(welcomeMsg);
            String[] historyLines = history.split("\n");
            for (String line : historyLines) {
                if (!line.trim().isEmpty()) {
                    chatHistory.add(line);
                }
            }
        }
    }

    /**
     * Extracts the 5-digit client ID from server's welcome message
     * Implements project requirement: System automatically assigns 5-digit ID (e.g., 01234)
     *
     * @param welcomeMsg The welcome message received from server containing client ID
     */
    private void extractClientIdFromWelcome(String welcomeMsg) {
        if (welcomeMsg.contains("Your ID: ")) {
            // Locate the ID portion after "Your ID: "
            String idPart = welcomeMsg.substring(welcomeMsg.indexOf("Your ID: ") + 9);

            // Extract consecutive digits to form the ID
            StringBuilder idBuilder = new StringBuilder();
            for (int i = 0; i < idPart.length(); i++) {
                char c = idPart.charAt(i);
                if (Character.isDigit(c)) {
                    idBuilder.append(c);
                    if (idBuilder.length() >= 5) {
                        break;
                    }
                } else if (idBuilder.length() > 0) {
                    // Stop extraction when non-digit encountered after digits
                    break;
                }
            }

            if (idBuilder.length() > 0) {
                String idString = idBuilder.toString();
                // Ensure ID is exactly 5 digits (project requirement)
                if (idString.length() == 5) {
                    this.clientId = idString;
                } else {
                    // Pad with leading zeros if necessary
                    this.clientId = String.format("%05d", Integer.parseInt(idString));
                }
            }
        }
    }

    /**
     * Runs in separate thread to continuously receive messages from server
     * Implements multithreading requirement: concurrent message reception
     *
     * Message types handled:
     * - System notifications (user join/leave)
     * - Regular chat messages from other users
     * - Server commands and information
     */
    private void receiveMessages() {
        try {
            while (!socket.isClosed()) {
                String message = input.readUTF();

                // Identify message type for appropriate handling
                boolean isSystemMessage = message.startsWith("[SYSTEM]") ||
                        message.startsWith("=== ") ||
                        message.startsWith("SERVER: ");

                synchronized (outputLock) {
                    // Avoid displaying duplicate messages (messages user sent themselves)
                    if (!isMessageFromSelf(message)) {
                        // If we're currently waiting for user input, print a newline first
                        if (isWaitingForInput) {
                            System.out.println(); // Move to new line before showing received message
                        }

                        // Display the received message
                        System.out.println(message);

                        // Add to local chat history for /save functionality
                        synchronized (chatHistory) {
                            chatHistory.add(message);
                        }

                        // After displaying received message, show input prompt again if we were waiting for input
                        if (isWaitingForInput) {
                            showInputPrompt();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            // Normal exit when socket is closed
        } catch (EOFException e) {
            synchronized (outputLock) {
                System.out.println("\nServer closed the connection.");
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                synchronized (outputLock) {
                    System.err.println("Error receiving message: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Checks if a received message originated from the current user
     * Used to prevent duplicate display of messages user just sent
     *
     * @param message The message received from server
     * @return true if message is from current user, false otherwise
     */
    private boolean isMessageFromSelf(String message) {
        if (clientId == null || username == null || username.equals("User")) {
            return false;
        }

        // Match pattern: [timestamp] username (ID: XXXXX): message content
        // Escapes special characters in username for regex matching
        String pattern = ".*\\[.*\\] " + username.replaceAll("[\\[\\]()]", "\\\\$0") + " \\(ID: " + clientId + "\\).*";
        return message.matches(pattern);
    }

    /**
     * Main thread for sending user messages to server
     * Implements project requirement: User can send messages via keyboard input
     *
     * Handles special commands:
     * - /exit: Gracefully disconnect from server
     * - /save: Export chat history to desktop file
     * - /help: Display available commands
     *
     * @throws IOException if network communication fails
     */
    private void sendMessages() throws IOException {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        // Show initial input prompt
        synchronized (outputLock) {
            showInputPrompt();
            isWaitingForInput = true;
        }

        while (!socket.isClosed()) {
            String message;
            try {
                message = consoleReader.readLine();
            } catch (IOException e) {
                break;
            }

            if (message == null || message.trim().isEmpty()) {
                // If empty input, just show prompt again
                synchronized (outputLock) {
                    showInputPrompt();
                    isWaitingForInput = true;
                }
                continue;
            }

            // Handle special commands
            if (message.equalsIgnoreCase("/exit")) {
                output.writeUTF("/exit");
                break;
            } else if (message.equalsIgnoreCase("/save")) {
                // Implements project requirement: Save chat records to local file
                saveChatHistoryToFile();
                synchronized (outputLock) {
                    showInputPrompt();
                    isWaitingForInput = true;
                }
                continue;
            } else if (message.equalsIgnoreCase("/help")) {
                // Display available commands to user
                synchronized (outputLock) {
                    System.out.println("\nAvailable commands:");
                    System.out.println("  /exit   - Leave the chat room");
                    System.out.println("  /save   - Save chat history to desktop");
                    System.out.println("  /help   - Show this help message");
                    showInputPrompt();
                    isWaitingForInput = true;
                }
                continue;
            }

            synchronized (outputLock) {
                // User has entered a message, we're no longer waiting for input
                isWaitingForInput = false;

                // Display message locally with timestamp before sending to server
                // Implements requirement: Display current time when user sends message
                displayLocalMessage(message);
            }

            // Send message to server for broadcasting to all clients
            output.writeUTF(message);

            // After sending message, show prompt again for next input
            synchronized (outputLock) {
                showInputPrompt();
                isWaitingForInput = true;
            }
        }
    }

    /**
     * Displays user's own message locally with timestamp
     * Implements project requirement: Display current time when user sends message
     *
     * @param message The message content entered by user
     */
    private void displayLocalMessage(String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String formattedMessage = "[" + timestamp + "] " + username + " (ID: " + clientId + "): " + message;
        System.out.println(formattedMessage);

        // Add to local chat history storage
        synchronized (chatHistory) {
            chatHistory.add(formattedMessage);
        }
    }

    /**
     * Displays input prompt with username and ID
     * Implements project requirement: Both ID and username displayed before each message user sends
     *
     * Modified format: 【You are {username}(ID: {clientId})】
     */
    private void showInputPrompt() {
        System.out.print("【You are " + username + "(ID: " + clientId + ")】 \n");
        System.out.flush(); // Ensure immediate display
    }

    /**
     * Saves current chat history to a file on user's desktop
     * Implements project requirement: User can save all chat records to local file
     *
     * File format includes:
     * - Header with metadata (saved by, timestamp)
     * - Complete chat history
     * - Footer with message count
     *
     * File location: Desktop/my_chat_history.txt
     */
    private void saveChatHistoryToFile() {
        try {
            // Ensure desktop directory exists
            File desktopDir = new File(DESKTOP_PATH);
            if (!desktopDir.exists()) {
                desktopDir.mkdirs();
            }

            // Create file path on desktop
            String filePath = DESKTOP_PATH + File.separator + "my_chat_history.txt";
            File historyFile = new File(filePath);

            // Write chat history to file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(historyFile))) {
                writer.write("=== Chat History Saved from Java Chat Room ===\n");
                writer.write("Saved by: " + username + " (ID: " + clientId + ")\n");
                writer.write("Saved at: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
                writer.write("=============================================\n\n");

                synchronized (chatHistory) {
                    for (String message : chatHistory) {
                        writer.write(message);
                        writer.newLine();
                    }
                }

                writer.write("\n=== End of Chat History ===\n");
                writer.write("Total messages: " + chatHistory.size() + "\n");
            }

            // Notify user of successful save
            synchronized (outputLock) {
                System.out.println("\n[SYSTEM] Chat history has been successfully saved to the desktop!");
                System.out.println("[SYSTEM] File location: " + filePath);
            }

        } catch (IOException e) {
            synchronized (outputLock) {
                System.err.println("\n[SYSTEM] Error saving chat history: " + e.getMessage());
            }
        }
    }
}