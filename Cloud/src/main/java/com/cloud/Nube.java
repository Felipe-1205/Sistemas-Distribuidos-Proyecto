package com.cloud;

import java.io.*;
import java.net.*;
import java.time.Instant;

public class Nube {
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(10025)) {
            System.out.println("Nube esperando conexiones en el puerto 10025");
            int contador=0;
            double acumulador=0;

            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    
                    // Lectura de la solicitud del cliente
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String request = in.readLine();
                    String[] parts = request.split("\\|");
                    String tipo = new String(parts[0]);

                    if(tipo.equals("Temperatura")){

                        double receivedDouble = Double.parseDouble(parts[1]);

                        System.out.println("Guardando: "+tipo+" con un valor de "+receivedDouble);
                        
                        long currentTime = Instant.now().toEpochMilli();
                        escribirEnArchivo("Log.txt", tipo+","+receivedDouble+","+parts[2]+","+parts[3]+","+currentTime);

                    } else if(tipo.equals("Humedad")){

                        double receivedDouble = Double.parseDouble(parts[1]);
                        
                        System.out.println("Guardando: "+tipo+" con un valor de "+receivedDouble);
                        long currentTime = Instant.now().toEpochMilli();
                        escribirEnArchivo("Log.txt", tipo+","+receivedDouble+","+parts[2]+","+parts[3]+","+currentTime);

                    } else if(tipo.equals("Humo")){

                        double receivedDouble = Double.parseDouble(parts[1]);

                        if (receivedDouble==1) {
                            System.out.println("Guardando: "+tipo+" con un valor de Verdadero");
                            
                        } else if (receivedDouble==0) {
                            System.out.println("Guardando: "+tipo+" con un valor de Falso");
                        }
                        long currentTime = Instant.now().toEpochMilli();
                        escribirEnArchivo("Log.txt", tipo+","+receivedDouble+","+parts[2]+","+parts[3]+","+currentTime);

                    } else if(tipo.equals("HumedpromedioDia")){

                        double receivedDouble = Double.parseDouble(parts[1]);
                        acumulador+=receivedDouble;
                        contador++;
                        System.out.println("Guardando: "+tipo+" con un valor de "+receivedDouble);
                        escribirEnArchivo("Log.txt", tipo+","+receivedDouble);
                        if (contador==4) {
                            double promedio = acumulador/4;
                            long currentTime = Instant.now().toEpochMilli();
                            if (promedio<100&&promedio>70) {
                                System.out.println("Guardando: HumedpromedioMes con un valor de "+promedio);
                                escribirEnArchivo("Log.txt", "HumedpromedioMes,"+receivedDouble+","+currentTime);
                            } else {
                                AvisarCalidad();
                                System.out.println("Guardando: HumedpromedioMes con un valor de "+promedio);
                                escribirEnArchivo("Log.txt", "HumedpromedioMes,"+receivedDouble+","+currentTime);
                                System.out.println("Guardando mensaje de Calidad: \"Promedio de Humedad por mes fuera de rango\"");
                                escribirEnArchivo("Log.txt", "Calidad,Promedio de Humedad por mes fuera de rango");
                            }
                            contador=0;
                            acumulador=0;
                        }
                        

                    } else if(tipo.equals("Tempromedio")){

                        double receivedDouble = Double.parseDouble(parts[1]);
                        long currentTime = Instant.now().toEpochMilli();
                        System.out.println("Guardando: "+tipo+" con un valor de "+receivedDouble);
                        escribirEnArchivo("Log.txt", tipo+","+receivedDouble+","+parts[2]+","+currentTime);

                    } else if(tipo.equals("Calidad")){

                        String mensaje = new String(parts[1]);

                        System.out.println("Guardando mensaje de "+tipo+": \""+mensaje+"\"");
                        escribirEnArchivo("Log.txt", tipo+","+mensaje);

                    }
                    
                    // Envío de la respuesta al cliente
                    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    out.println("mensaje almacenado");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void escribirEnArchivo(String filePath, String linea) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            writer.write(linea);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error al escribir en el archivo: " + e.getMessage());
        }
    }
    public static void AvisarCalidad() {
        String hostname = "localhost";
        int port = 10035;
        try (Socket socket = new Socket(hostname, port)) {
            // Envío de la solicitud al servidor
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println("Humedad fuera del rango");

            // Lectura de la respuesta del servidor
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println(in.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
