package com.fog;

import java.io.*;
import java.net.*;

public class SistemaCalidad {
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(10030)) {
            System.out.println("Servidor esperando conexiones en el puerto 10030");
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    
                    // Lectura de la solicitud del cliente
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String request = in.readLine();
                    System.out.println(request);
                    
                    // Envío de la respuesta al cliente
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    out.println("mensaje informado");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}