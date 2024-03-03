import java.io.*;
import java.net.*;
import java.nio.CharBuffer;
import java.text.DecimalFormat;
import javax.sound.sampled.LineEvent;
import java.util.Scanner;
import java.net.InetAddress;

class GetThread extends Thread {

  private String fileName;
  private DataInputStream serverInput = null;
  private DataOutputStream serverOut = null;

  GetThread(String fileName, DataInputStream serverInput, DataOutputStream serverOut) {
    this.fileName = fileName;
    this.serverInput = serverInput;
    this.serverOut = serverOut;
  }

  public void run() {
    try {
      // Send the command to the server to get the file
      serverOut.writeUTF("get " + fileName + " &");

      // Wait for the server to acknowledge the command
      boolean commandAcknowledged = serverInput.readBoolean();

      if (commandAcknowledged) {
        // Receive the server's response
        String response = serverInput.readUTF();

        // Check if the response indicates a successful get command
        if (response.contains("get#")) {
          String[] fileData = response.split("#");
          System.out.println("Command ID:" + fileData[3]);

          // Wait for 5 seconds as per the requirements
          Thread.sleep(50000);

          // Get the file size from the server
          long fileSize = serverInput.readLong();
          File file = new File(fileData[1]);

          // Create a FileOutputStream to write the file
          FileOutputStream fileOutputStream = new FileOutputStream(file);

          // Check if the file already exists, if not create it
          if (!file.exists()) {
            file.createNewFile();
          }

          byte[] buffer = new byte[1024];
          int bytesRead = 0;

          // Read data from the server and write it to the file
          while (fileSize > 0 && (bytesRead = serverInput.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1 && serverInput.readBoolean()) {
            fileOutputStream.write(buffer, 0, bytesRead);
            fileSize -= bytesRead;
          }

          fileOutputStream.flush();
          fileOutputStream.close();

          // Check if the file transfer was successful
          if (fileSize == 0) {
            String result = "Got the " + fileData[1] +"\n";
            System.out.println(result);
          } else {
            // Delete the partially transferred file
            file.delete();
            System.out.println("get command terminated before completion");
          }

          // Read and discard any remaining data from the server
          serverInput.readUTF();
        } else {
          // Print the server's response if it does not indicate a successful get command
          System.out.println(response);
        }
      } else {
        // Print an error message if the command was not acknowledged by the server
        System.out.println("Command not acknowledged by the server");
      }
    } catch (Exception e) {
      // Print any exceptions that occur during the execution of the get command
      System.out.println("Exception in get command: " + e.getMessage());
    }
  }
}

class PutThread extends Thread {
  private String fileName;
  private DataInputStream serverInput = null;
  private DataOutputStream serverOut = null;

  PutThread(String fileName, DataInputStream serverInput, DataOutputStream serverOut) {
    this.fileName = fileName;
    this.serverInput = serverInput;
    this.serverOut = serverOut;
  }

  public void run() {
    try {
      File file = new File(fileName);

      if (!file.exists() || !file.isFile()) {
        System.out.println(fileName + ": File not found");
        return;
      }

      long fileSize = file.length();
      if (fileSize == 0) {
        System.out.println(fileName + ": File is empty");
        return;
      }

      try (FileInputStream fileInputStream = new FileInputStream(file)) {
        serverOut.writeUTF("put " + fileName + " &");
        serverInput.readBoolean();
        System.out.println("Command ID:" + (serverInput.readLong() / 256));
        Thread.sleep(5000);
        serverOut.writeLong(fileSize);

        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
          serverOut.write(buffer, 0, bytesRead);
          if (!serverInput.readBoolean()) {
            break;
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }

      String response = serverInput.readUTF();
      System.out.println(response);
    } catch (Exception e) {
      System.out.println("Exception in put command: " + e.getMessage());
    }
  }
}

public class Client {

  private Socket primarySocket = null;
  private BufferedReader primaryInput = null;
  private DataInputStream primaryServerInput = null;
  private DataOutputStream primaryOutput = null;

  private Socket threadSocket = null;
  private DataInputStream threadInput = null;
  private DataOutputStream threadOutput = null;

  public Client(String address, int primaryPort, int threadPort) {
    try {
      primarySocket = new Socket(address, primaryPort);
      System.out.println("Connected to primary port: " + primaryPort);

      primaryInput = new BufferedReader(new InputStreamReader(System.in));

      primaryServerInput = new DataInputStream(new BufferedInputStream(primarySocket.getInputStream()));
      primaryOutput = new DataOutputStream(primarySocket.getOutputStream());

      threadSocket = new Socket(address, threadPort);
      System.out.println("Connected to thread port: " + threadPort);

      threadInput = new DataInputStream(new BufferedInputStream(threadSocket.getInputStream()));
      threadOutput = new DataOutputStream(threadSocket.getOutputStream());
    } catch (UnknownHostException u) {
      System.out.println(u);
    } catch (IOException i) {
      System.out.println(i);
    }

    String line = "";

    while (!line.equals("quit")) {
      try {
        System.out.print("Enter Command: ");
        line = primaryInput.readLine();
        String error = "";
        String result = "";
        switch (line.split(" ")[0]) {
          case "mkdir":
          case "rmdir":
            if (line.split(" ").length != 2) error = line.split(" ")[0] + "directory name missing";
            break;
          case "delete":
            if (line.split(" ").length != 2) error = "delete: filename missing";
            break;
          case "get":
            if (line.split(" ").length == 1) error = "get: filename missing";
            else if (line.split(" ").length == 3 && line.endsWith("&")) {
              // MultiThreading
              new GetThread(line.split(" ")[1], primaryServerInput, primaryOutput).start();
            } else if (line.split(" ").length > 2) error = "get: invalid operand";
            break;
          case "put":
            if (line.split(" ").length == 1) error = "put: file name missing";
            else if (line.split(" ").length == 3 && line.endsWith("&")) {
              // MultiThreading
              new PutThread(line.split(" ")[1], primaryServerInput, primaryOutput).start();
            } else if (line.split(" ").length == 2) {
              String fileName = line.split(" ")[1];
              File file = new File(fileName);

              if (file.exists() && file.isFile()) {
                try {
                  RandomAccessFile reader = new RandomAccessFile(file, "r");
                  byte[] buffer = new byte[1024];
                  int count = 0;
                  primaryOutput.writeUTF(line);
                  primaryServerInput.readLong();
                  primaryServerInput.readBoolean();
                  primaryOutput.writeLong(file.length());

                  while ((count = reader.read(buffer)) > 0) {
                    primaryOutput.write(buffer, 0, count);
                    primaryServerInput.readBoolean();
                  }

                  reader.close();
                } catch (IOException e) {
                  e.printStackTrace();
                }

              } else {
                error = fileName + "not found";
              }
            } else error = "Invalid put command check syntax";
            break;
          case "terminate":
            if (line.split(" ").length != 2) error = "directory name missing";
            else {
              threadOutput.writeUTF(line);
              error = threadInput.readUTF();
            }
            break;
          case "quit":
            threadOutput.writeUTF("quit");
            break;
        }

        if (error.isEmpty() && !line.endsWith("&") && !line.contains("terminate")) {
          if (!line.contains("put")) primaryOutput.writeUTF(line);
          if (line.contains("get")) primaryServerInput.readBoolean();
          String line1 = primaryServerInput.readUTF();
          if (line1.contains("get#")) {
            String[] fileNameData = line1.split("#");
            System.out.println("Command ID:" + fileNameData[3]);
            long originalSize = primaryServerInput.readLong();
            long fileSize = originalSize;
            File file = new File(fileNameData[1]);
            FileOutputStream fileOutStream = new FileOutputStream(file);
            if (!file.exists()) {
              file.createNewFile();
            }

            byte[] buffer = new byte[1024];
            int bytesRead = 0;
            while (fileSize > 0 && (bytesRead = primaryServerInput.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1 && primaryServerInput.readBoolean()) {
              fileOutStream.write(buffer, 0, bytesRead);
              fileSize -= bytesRead;
            }

            fileOutStream.flush();
            fileOutStream.close();
            if (fileSize == 0) {
              result = "Got " + fileNameData[1] + "\n";
              System.out.println(result);
            } else {
              file.delete();
              System.out.println("Command is Terminated");
            }
            primaryServerInput.readUTF();
          } else {
            System.out.println(line1);
          }
        } else System.out.println(error);
      } catch (IOException err) {
        System.out.println(err);
      }
    }

    // close the connection
    try {
      primaryInput.close();
      primaryOutput.close();
      primaryServerInput.close();
      primarySocket.close();
      threadOutput.close();
      threadInput.close();
      threadSocket.close();
    } catch (IOException i) {
      System.out.println(i);
    }
  }

  public static void main(String args[]) {
    Scanner scanner = new Scanner(System.in);

    try {
      System.out.print("Enter the server IP address: ");
      String ipAddress = scanner.nextLine();

      System.out.print("Enter the normal port: ");
      int normalPort = scanner.nextInt();

      System.out.print("Enter the terminate port: ");
      int terminatePort = scanner.nextInt();

      // Create and start the client
      new Client(ipAddress, normalPort, terminatePort);
    } finally {
      // Close scanner
      scanner.close();
    }
  }
}