# Java Chat Room — CSC-1004 Course Project

A multithreaded console chat room implemented in Java using Socket programming.
This project is submitted as part of **CSC-1004: Computational Laboratory Using Java**,
The Chinese University of Hong Kong, Shenzhen (CUHK-SZ).

Course context: the assignment requires a Java chat room with multithreading,
chat-room functions, chat-record storage, and proper message display. [3](@ref)

## Features

- TCP client–server architecture on `localhost:6666`
- Automatic 5-digit client ID assignment (e.g. `01234`)
- Custom username input during handshake
- Real-time broadcasting to all connected clients
- Separate sender/receiver threads on the client side
- Server-side multithreading: one `ClientHandler` per connection
- Online user list sent to newly joined clients
- Join/leave system notifications for all users
- Timestamped messages (`HH:mm:ss`)
- Server-side chat history persisted to `chat_history.txt`
- Recent history delivered to new clients (last 50 messages)
- Client `/save` command exports chat history to desktop
- Commands: `/help`, `/exit`, `/save` (client); `/users`, `/history` (server-side)

## Tech Stack

- Java SE (Sockets, DataInputStream/DataOutputStream, Threads)
- `Vector` for thread-safe active client storage
- `AtomicInteger` for unique ID generation
- File I/O for history persistence

## Project Structure

src/main/java/org/example/
├── Main.java     # Launcher: choose 1=Server, 2=Client
├── Server.java   # ServerSocket, broadcast, history, ClientHandler
├── Client.java   # Console client, sender/receiver threads, /save
pom.xml           # Maven project file
chat_history.txt  # Auto-created server-side history file

## Requirements

- JDK 11+ (JDK 17/21 recommended)
- Maven (optional) or any Java IDE

## Run with Maven

# Terminal 1 - start server
mvn exec:java -Dexec.mainClass=org.example.Main
# choose 1

# Terminal 2/3/... - start clients
mvn exec:java -Dexec.mainClass=org.example.Main
# choose 2

## Run with plain javac

# Compile
javac src/main/java/org/example/*.java -d out

# Server terminal
java -cp out org.example.Main
# choose 1

# Client terminal(s)
java -cp out org.example.Main
# choose 2

## Usage

1. Start the server first.
2. Start one or more clients.
3. Enter a username when prompted.
4. Chat normally; messages are broadcast with timestamps.
5. Client commands:
   - `/save`  save chat history to desktop as `my_chat_history.txt`
   - `/help`  show command list
   - `/exit`  leave the chat room
6. Server-side client commands:
   - `/users`   list online users
   - `/history` show recent server history

## Notes for CSC-1004

- Multithreading: server spawns one thread per client; client uses one receiver thread + main sender thread.
- Socket I/O: `ServerSocket`/`Socket` with `DataInputStream`/`DataOutputStream`.
- Storage: in-memory history plus append-only `chat_history.txt`.
- Suitable for local multi-client demonstration and video recording.

Repository: https://github.com/berserkross/Java_Chat_Room
