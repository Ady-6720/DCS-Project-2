import java.io.*;
import java.net.*;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Scanner;
import java.net.InetAddress;


class Globals {
    public static final HashMap<Long, String> threadMap = new HashMap<>();
    public static final HashMap<String, Semaphore> fileSemaphoreMap = new HashMap<>();
    public static final String ACTIVE = "active";
    public static final String TERMINATED = "terminated";
}

class TerminateThread extends Thread {
    private final DataInputStream in;
    private final DataOutputStream out;

    TerminateThread(DataInputStream din, DataOutputStream dout) {
        this.in = din;
        this.out = dout;
    }

    public void run() {
        try {
            String input;
            while (!(input = in.readUTF()).equals("quit")) {
                System.out.println("tThread input:" + input);
                long threadId = Long.parseLong(input.split(" ")[1]);
                if (Globals.threadMap.containsKey(threadId) && Globals.threadMap.get(threadId).equals(Globals.ACTIVE)) {
                    Globals.threadMap.put(threadId, Globals.TERMINATED);
                    out.writeUTF("Termination Start");
                } else {
                    out.writeUTF("No command found for id: " + threadId);
                }
            }
        } catch (IOException i) {
            System.out.println("Exception in TerminateThread-run ");
        }
    }
}

class TPortThread extends Thread {
    private final int port;

    TPortThread(int port) {
        this.port = port;
    }

    public void run() {
        try {
            ServerSocket server = new ServerSocket(port);
            System.out.println("Listening terminate port : " + port);
            while (true) {
                Socket socket = server.accept();
                System.out.println("Client connected to terminate Port : " + port);
                DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                new TerminateThread(in, out).start();
            }
        } catch (IOException e) {
            System.out.println("Exception in TPortThread-run");
        }
    }
}

class NPortThread extends Thread {
    private final int port;

    NPortThread(int port) {
        this.port = port;
    }

    public void run() {
        try {
            ServerSocket server = new ServerSocket(port);
            System.out.println("Listening normal port :" + port);
            while (true) {
                Socket socket = server.accept();
                System.out.println("Client connected to normal Port: " + port);
                DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                new WorkerThread(in, out).start();
            }
        } catch (IOException e) {
            System.out.println("Exception in NPortThread-run");
        }
    }
}

class WorkerThread extends Thread {
    private final DataInputStream in;
    private final DataOutputStream out;

    WorkerThread(DataInputStream din, DataOutputStream dout) {
        this.in = din;
        this.out = dout;
    }

    public static String printResults(Process process) throws IOException {
        String line = "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.waitFor() == 0 ? process.getInputStream() : process.getErrorStream()))) {
            String line1;
            while ((line1 = reader.readLine()) != null) {
                line += line1 + "\t";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return line;
    }

    public void run() {
        try {
            String userDirectory = System.getProperty("user.dir");
            long id = Thread.currentThread().getId();
            String line = "";
            id *= id;

            while (!line.equals("quit")) {
                line = in.readUTF();
                id++;

                System.out.println("Command Received " + line);
                String result = "";

                String[] commandParts = line.split(" ");
                String command = commandParts[0]; // Extract the command keyword

                switch (command) {
                    case "pwd":
                        result = "\n" + userDirectory + "\n";
                        break;
                    case "mkdir":
                        if (commandParts.length >= 2) {
                            String directoryName = commandParts[1];
                            File newDirectory = new File(userDirectory + "/" + directoryName);
                            if (!newDirectory.exists()) {
                                if (newDirectory.mkdir()) {
                                    result = "Directory Created";
                                } else {
                                    result = "Failed to create directory";
                                }
                            } else {
                                result = "Directory already exists";
                            }
                        } else {
                            result = "Invalid command: No directory name provided";
                        }
                        break;
                    case "rmdir":
                        if (commandParts.length >= 2) {
                            String directoryName = commandParts[1];
                            File directoryToRemove = new File(userDirectory + "/" + directoryName);
                            if (directoryToRemove.exists() && directoryToRemove.isDirectory()) {
                                if (directoryToRemove.delete()) {
                                    result = "Directory Removed";
                                } else {
                                    result = "Failed to remove directory";
                                }
                            } else {
                                result = "Directory does not exist";
                            }
                        } else {
                            result = "Invalid command: No directory name provided";
                        }
                        break;
                    case "delete":
                        if (commandParts.length >= 2) {
                            String fileNameToDelete = commandParts[1];
                            File fileToDelete = new File(userDirectory + "/" + fileNameToDelete);
                            if (fileToDelete.exists() && fileToDelete.isFile()) {
                                if (fileToDelete.delete()) {
                                    result = "File Deleted";
                                } else {
                                    result = "Failed to delete file";
                                }
                            } else {
                                result = "File does not exist";
                            }
                        } else {
                            result = "Invalid command: No file name provided";
                        }
                        break;
                    case "cd":
                        if (commandParts.length >= 2) {
                            String newDirectoryName = commandParts[1];
                            File newDirectory;
                            if (newDirectoryName.equals("..")) {
                                newDirectory = new File(userDirectory).getParentFile();
                                if (newDirectory == null) {
                                    result = "Already at the root directory";
                                    break;
                                }
                            } else {
                                newDirectory = new File(userDirectory + "/" + newDirectoryName);
                            }

                            if (newDirectory.exists() && newDirectory.isDirectory()) {
                                userDirectory = newDirectory.getAbsolutePath();
                                result = "Directory Changed";
                            } else {
                                result = "Directory does not exist";
                            }
                        } else {
                            result = "Invalid command: No directory name provided";
                        }
                        break;
                    case "ls":
                        Process lsProcess = Runtime.getRuntime().exec(line, null, new File(userDirectory));
                        result = printResults(lsProcess);
                        String[] results = result.split("\t");
                        result = "";
                        for (String result1 : results) {
                            if (result1.indexOf("Server") < 0) {
                                result += result1 + "\n";
                            }
                        }
                        break;
                    case "get":
                        String fileName = commandParts[1];
                        File file = new File(userDirectory + "/" + fileName);
                        boolean terminated = false;

                        if (file.exists() && file.isFile()) {
                            try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
                                System.out.println("Running getThread: " + id);
                                Globals.threadMap.put(id, Globals.ACTIVE);
                                Semaphore semaphore = Globals.fileSemaphoreMap.get(fileName);
                                if (semaphore == null) {
                                    semaphore = new Semaphore(1);
                                    Globals.fileSemaphoreMap.put(fileName, semaphore);
                                }
                                semaphore.acquire();
                                out.writeBoolean(true);
                                out.writeUTF("get#" + fileName + "#" + userDirectory + "#" + id);
                                out.writeLong(file.length());

                                byte[] buffer = new byte[1024];
                                int count;
                                while (!terminated && (count = reader.read(buffer)) > 0) {
                                    out.write(buffer, 0, count);
                                    if (Globals.threadMap.get(id).equals(Globals.TERMINATED)) {
                                        terminated = true;
                                        Globals.threadMap.remove(id);
                                    }
                                    out.writeBoolean(!terminated);
                                }
                                semaphore.release();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } else {
                            result = fileName + ": Not Found";
                            out.writeBoolean(true);
                        }
                        break;
                    case "put":
                        System.out.println("Running putThread: " + id);
                        Globals.threadMap.put(id, Globals.ACTIVE);
                        out.writeLong(id);

                        String fileNamePut = commandParts[1];
                        Semaphore semaphorePut = Globals.fileSemaphoreMap.get(fileNamePut);

                        if (semaphorePut == null) {
                            semaphorePut = new Semaphore(1);
                            Globals.fileSemaphoreMap.put(fileNamePut, semaphorePut);
                        }
                        semaphorePut.acquire();
                        out.writeBoolean(true);

                        boolean terminatedPut = false;
                        long originalSizePut = in.readLong();
                        long fileSizePut = originalSizePut;
                        File filePut = new File(userDirectory + "/" + fileNamePut);
                        try (FileOutputStream fileOutStreamPut = new FileOutputStream(filePut)) {
                            if (!filePut.exists()) {
                                filePut.createNewFile();
                            }

                            byte[] bufferPut = new byte[1024];
                            int bytesReadPut;
                            while (!terminatedPut && fileSizePut > 0 &&
                                    (bytesReadPut = in.read(bufferPut, 0, (int) Math.min(bufferPut.length, fileSizePut))) != -1) {
                                fileOutStreamPut.write(bufferPut, 0, bytesReadPut);
                                fileSizePut -= bytesReadPut;
                                if (Globals.threadMap.get(id).equals(Globals.TERMINATED)) {
                                    filePut.delete();
                                    terminatedPut = true;
                                    out.writeBoolean(false);
                                    Globals.threadMap.remove(id);
                                } else {
                                    out.writeBoolean(true);
                                }
                            }
                            if (!terminatedPut) {
                                result = "Uploaded " + fileNamePut + "\n";
                            } else {
                                result = "put command Termination Finished";
                            }
                        }
                        semaphorePut.release();
                        break;
                    case "quit":
                        result = "Connection Closed!";
                        break;
                    default:
                        result = "Invalid Command";
                        break;
                }
                out.writeUTF(result);
            }
            System.out.println("Closing connection");
        } catch (Exception e) {
            System.out.println("Exception: Connection Terminated");
        }
    }
}

public class Server {
    public Server(int nPort, int tPort) {
        new NPortThread(nPort).start();
        new TPortThread(tPort).start();
    }

    public static void main(String args[]) {
        Scanner scanner = new Scanner(System.in); // Create a Scanner object

        try {
            // Get the localhost address
            InetAddress localhost = InetAddress.getLocalHost();
            System.out.println("Server IP Address: " + localhost.getHostAddress());
        } catch (UnknownHostException e) {
            System.out.println("Unable to get the server IP address: " + e.getMessage());
        }

        // Prompt for and read the normal port number
        System.out.println("Enter the normal port:");
        int nPort = scanner.nextInt(); // Read user input for nPort

        // Prompt for and read the terminate port number
        System.out.println("Enter the terminate port:");
        int tPort = scanner.nextInt(); // Read user input for tPort

        scanner.close(); // Close the scanner

        new Server(nPort, tPort);

    }
}