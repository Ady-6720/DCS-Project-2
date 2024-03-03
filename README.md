# Multithreaded Client-Server File Transfer Protocol System

## Overview
This project implements a client-server file transfer system using sockets in Java. The system allows users to interact with the server to perform various file operations such as uploading files (`put`), downloading files (`get`), creating directories (`mkdir`), changing directories (`cd`), deleting files (`delete`), listing directory contents (`ls`), and checking the current directory (`pwd`).

## Multithreading
The project works on multithreading 
Normal Port - takes commands and returns command ID 
Terminate port- Takes terminate command in form of "terminate followed by **command id**

## How to run
1. Compile the server and client programs using a javac filename.java.
2. Run the server program on a host machine by giving port number for normal port as well as terminate port .
3. Run the client program & connect it to the server using the server's IP address and port number.
4. Follow the prompts on the client-side to interact with the server and perform file operations.
5. When given **put 'filename' &** command or **get 'filename' &** a command ID will generate and will be displayed on terminal
   -----To terminate the operation enter - ('terminate <commandID>') , terminal will show the termination message 

## File Structure
- **Server.java** Contains the implementation of the server-side logic.
- **Client.java** Contains the implementation of the client-side logic.
- **README.md:** This file, providing an overview of the project, its features, prerequisites, usage instructions, and file structure.

## Contributors
- Yash Joshi
- Aditya Malode

“This project was done in its entirety by Yash Joshi & Aditya Malode. We hereby
state that we have not received unauthorized help of any form”
