package org.example;

import java.util.Scanner;

/**
 * Main Application Launcher for Java Chat Room Project
 *
 * Provides entry point for both server and client modes
 * Users can choose to start either the chat server or a chat client
 *
 * This class implements the project requirement: Display multiple command-line interfaces
 * by allowing multiple client instances to be launched concurrently
 */
public class Main {
    /**
     * Main entry point for the Java Chat Room application
     *
     * Presents mode selection menu to user:
     * 1. Start Server - Launches the chat server on port 6666
     * 2. Start Client - Launches a chat client to connect to server
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Java Chat Room ===");
        System.out.println("1. Start Server");
        System.out.println("2. Start Client");
        System.out.print("Select mode (1/2): ");

        String choice = scanner.nextLine().trim();

        try {
            switch (choice) {
                case "1":
                    // Start the chat server (listens on port 6666)
                    System.out.println("Starting Chat Server on port 6666...");
                    Server.main(args);
                    break;
                case "2":
                    // Start a chat client (connects to localhost:6666)
                    System.out.println("Starting Chat Client...");
                    Client.main(args);
                    break;
                default:
                    System.out.println("Invalid choice. Please enter 1 or 2.");
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Ensure scanner resource is properly closed
            scanner.close();
        }
    }
}