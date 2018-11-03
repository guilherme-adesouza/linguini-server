package linguiniserver;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class UDPServer extends Thread {
    
    private static final int PORT = 5000;
    private static final int TAM_BUFFER = 4096;
    private static final String UPLOAD_FOLDER= "uploads/";
    private static CopyOnWriteArrayList<UDPClient> CLIENTS = new CopyOnWriteArrayList<>();

    @Override
    public void run() {
        

        try {
            System.out.println("OS: " + System.getProperty("os.name") +"\n"+
                    "Version: " + System.getProperty("os.version")+"\n"+
                    "Architecture: " + System.getProperty("os.arch")+"\n"+
                    "Port: " + UDPServer.PORT+"\n");            
            
            DatagramSocket serverSocket = new DatagramSocket(PORT);

            while (true) {

                System.out.println("Waiting new communication...");
                                
                ClientPackage clientPackage = UDPServer.receberPacote(serverSocket);  
                //new client
                UDPClient client = clientPackage.getClient();
                System.out.println(client+"\n");
                
                String recebido = new String(clientPackage.getData());
                
                String mensagem = "";

                switch (recebido.trim().toLowerCase()) {
                    case "upload":
                        
                        UDPServer.enviarPacote(serverSocket, client, "auth".getBytes());                            
                        System.out.println("Upload request. Waiting filename...");

                        String file = new String(UDPServer.receberPacote(serverSocket).getData());
                        System.out.println("File: " + file);
                        
                        byte[] bufferEntrada =  UDPServer.receberPacote(serverSocket).getData();
                        
                        FileOutputStream out = new FileOutputStream( UDPServer.UPLOAD_FOLDER  + file.trim(), false);

                        while (!(new String(bufferEntrada)).trim().equals("finish")) {
                            out.write(bufferEntrada);
                            
                            //send to receive next package
                            UDPServer.enviarPacote(serverSocket, client, bufferEntrada);                            
                            //wait client answer
                            bufferEntrada = UDPServer.receberPacote(serverSocket).getData();                            
                        }

                        out.close();
                        
                        break;
                    case "download":
                        System.out.println("Download request. Waiting filename...");

                        ClientPackage downloadPackage = UDPServer.receberPacote(serverSocket);                      
                        file = new String(downloadPackage.getData());
                        System.out.println("File: " + file);

                        byte[] bufferSaida = new byte[TAM_BUFFER];
                        byte[] bytes = fileToBytes(file.trim());
                        int count = 0;

                        for (byte b : bytes) {
                            bufferSaida[count] = b;
                            count++;

                            if (count == TAM_BUFFER) {
                                //send buffer
                                UDPServer.enviarPacote(serverSocket, downloadPackage.getClient(), bytes);
                                //receive confirmation
                                UDPServer.receberPacote(serverSocket);
                                
                                bufferSaida = new byte[TAM_BUFFER];
                                count = 0;
                            }
                        }

                        //send last buffer
                        UDPServer.enviarPacote(serverSocket, downloadPackage.getClient(), bytes);
                        //receive confirmation
                        UDPServer.receberPacote(serverSocket);

                        //send confirmation of last buffer
                        UDPServer.enviarPacote(serverSocket, downloadPackage.getClient(), "finish".getBytes());
                        
                        break;

                    case "list":
                        System.out.println("List request. Sending list of files in "+UDPServer.UPLOAD_FOLDER+"...");
                        File folder = new File(UDPServer.UPLOAD_FOLDER);
                        File[] listOfFiles = folder.listFiles();

                        for (int i = 0; i < listOfFiles.length; i++) {
                            mensagem += listOfFiles[i].getName() + ";";
                        }
                        
                        UDPServer.enviarPacote(serverSocket, client, mensagem.getBytes());

                        break;
                    case "receiver":  
                        System.out.println("Receiver request");
                        
                        if(CLIENTS.isEmpty()) {
                            UDPServer.CLIENTS.add(client);     
                        }
                        else {
                            for (UDPClient udpClient : CLIENTS) {
                                if(!udpClient.equals(client)) {
                                    UDPServer.CLIENTS.add(client);     
                                }
                            }
                        }
                        System.out.println("Client add client into list of receivers of message!");
                        break;
                    case "message": 
                        
                        UDPServer.enviarPacote(serverSocket, client, "auth".getBytes());
                        System.out.println("Message request. Waiting message...");
                            
                        ClientPackage messagePackage = UDPServer.receberPacote(serverSocket);                      
                        String message = new String(messagePackage.getData()).trim();
                        
                        System.out.println("Message: " + message);
                        
                        for (UDPClient udpClient : CLIENTS) {
                            if(!client.equals(udpClient)) {
                                System.out.println("Sending for " + udpClient+"...");
                                UDPServer.enviarPacote(serverSocket, udpClient, message.getBytes());
                            }
                            else {
                                System.out.println("Can't sent the message to sender!");
                            }
                        }          
                        
                        System.out.println("Message sent to "+CLIENTS.size()+" client(s).");
                        break;
                    default:
                        mensagem = "Help:\n";
                        mensagem += "Digit 'upload' to send a file\n";
                        mensagem += "Digit 'download' to download a file\n";
                        mensagem += "Digit 'list' to see all files\n";

                        UDPServer.enviarPacote(serverSocket, client, mensagem.getBytes());
                        break;
                }
            }
            
        } catch (Exception e) {
            System.out.println("500 - SERVER ERROR");
            e.printStackTrace();
        }
    }
    
    private static boolean enviarPacote(DatagramSocket serverSocket, UDPClient client, byte[] conteudo){
        try {
            byte[] bufferSaida = new byte[TAM_BUFFER];
            bufferSaida = conteudo;
            DatagramPacket sendPacket = new DatagramPacket(bufferSaida, bufferSaida.length, client.getIpCliente(), client.getPortaCliente());
            serverSocket.send(sendPacket);
            return true;
        }
        catch(Exception e) {
            System.out.println("500 - SERVER ERROR");
            e.printStackTrace();
            return false;
        }
    } 
    
    private static ClientPackage receberPacote(DatagramSocket serverSocket){        
        try {
            byte[] bufferEntrada = new byte[TAM_BUFFER];
            
            DatagramPacket receivePacket = new DatagramPacket(bufferEntrada, bufferEntrada.length);
            serverSocket.receive(receivePacket);
            
            ClientPackage clientPackage = new ClientPackage(receivePacket);
            return clientPackage;
        }
        catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    } 
    
    public static byte[] fileToBytes(String path) {
        File file = new File(UDPServer.UPLOAD_FOLDER+"/"+path);

        byte[] b = new byte[(int) file.length()];

        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            fileInputStream.read(b);
        } catch (FileNotFoundException e) {
            System.out.println("File Not Found.");
            e.printStackTrace();
        } catch (IOException e1) {
            System.out.println("Error Reading The File.");
            e1.printStackTrace();
        }
        return b;
    }
}
