package com.cloud;

import java.io.IOException;
import java.net.Socket;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Healthcheck {
    public static void main(String[] args) {
        
        try (Socket socket = new Socket("10.43.100.213", 10040)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            long lastHeartbeatTime = System.currentTimeMillis();
            long timeout = 5000; // 5 segundos de espera para el heartbeat
            
            while (true) {
                if (in.ready()) {
                    String message = in.readLine();
                    if ("heartbeat".equals(message)) {
                        lastHeartbeatTime = System.currentTimeMillis();
                    }
                }
                
                if (System.currentTimeMillis() - lastHeartbeatTime > timeout) {
                    System.out.println("El servidor ha dejado de enviar respuesta. Puede estar caído.");
                    ProxyRespaldo.respaldo();
                    break;
                }
                
                Thread.sleep(1000); // Espera 1 segundo antes de revisar nuevamente
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
class ProxyRespaldo {
    public static void respaldo() {
        Queue<String> colaCompartida = new ConcurrentLinkedQueue<>();
        // definimos 4 hilos uno para cada tipo de sensor y otro para mandar la
        // informacion a la nube
        Thread push = new Thread(new Push(colaCompartida));
        Thread humo = new Thread(new Humo(colaCompartida));
        Thread temperatura = new Thread(new Temperatura(colaCompartida));
        Thread humedad = new Thread(new Humedad(colaCompartida));
        Thread comprobar = new Thread(new Comprobacion());
        push.start();
        humo.start();
        temperatura.start();
        humedad.start();
        comprobar.start();
    }
}
class Comprobacion implements Runnable {

    public void run() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket publisher = context.createSocket(SocketType.PUB);
            publisher.bind("tcp://10.43.101.10:10045");
            while (!Thread.currentThread().isInterrupted()) {
                String message = "Cambiar a nuevo proxy";
                publisher.send(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class Push implements Runnable {
    
    private Queue<String> cola;

    public Push(Queue<String> cola) {
        this.cola = cola;
    }

    public void run() {
        while (true) {
            if(cola.peek() != null){
                mandarCloud(cola.poll());
            }
        }
    }
    public static void mandarCloud(String mensaje) {
        String hostname = "localhost";
        int port = 10025;
        try (Socket socket = new Socket(hostname, port)) {
            // Envío de la solicitud al servidor
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(mensaje);
            // Lectura de la respuesta del servidor
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println(in.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class Humo implements Runnable {
    private Queue<String> cola;

    public Humo(Queue<String> cola) {
        this.cola = cola;
    }

    public void run() {
        // ponemos a escuchar una direccion y un puerto dependiendo del sensor
        try (ZContext context = new ZContext()) {
            ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);
            pullSocket.bind("tcp://10.43.101.10:10000");
            while (!Thread.currentThread().isInterrupted()) {
                // hacemos la lectura del dato y sacamos el primer dato para comprobar
                byte[] combinedDataBytes = pullSocket.recv();
                String combinedData = new String(combinedDataBytes);
                String[] parts = combinedData.split("\\|");
                double receivedDouble = Double.parseDouble(parts[0]);
                long currentTime = Instant.now().toEpochMilli();
                // validamos el dato y dependiendo de lo obtenido poinemos el error o subismo el
                // dato a la colaa para ennviar a la nube
                if (receivedDouble == -1) {
                    System.out.println("Error en el sensor de humo");
                } else if (receivedDouble == 0) {

                    cola.offer("Humo|" + combinedData+"|"+currentTime);
                } else if (receivedDouble == 1) {
                    cola.offer("Humo|" + combinedData+"|"+currentTime);
                    cola.offer("Calidad|los roseadores se han activado");
                }
            }
        }
    }
}

class Temperatura implements Runnable {
    private Queue<String> cola;

    public Temperatura(Queue<String> cola) {
        this.cola = cola;
    }

    public void AvisarCalidad() {
        String hostname = "10.43.100.213";
        int port = 10030;
        try (Socket socket = new Socket(hostname, port)) {
            // Envío de la solicitud al servidor
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println("Temperatura fuera del rango");

            // Lectura de la respuesta del servidor
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println(in.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        // ponemos a escuchar una direccion y un puerto dependiendo del sensor
        try (ZContext context = new ZContext()) {
            ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);
            pullSocket.bind("tcp://10.43.101.10:10005");

            while (!Thread.currentThread().isInterrupted()) {
                double acumulado = 0, proemdio;
                int cantidad = 10;
                ArrayList<Instant> tiempos = new ArrayList<>();
                // hacemos la lectura del dato y hacemos un for para sacar el promedio de los 10
                // datos
                
                long currentTime = Instant.now().toEpochMilli();
                for (int i = 0; i < 10; i++) {
                    // al mensaje llegado lo dividimos en 2 uno para el dato del sensor y otro para
                    // el tiempo
                    byte[] combinedDataBytes = pullSocket.recv();
                    String combinedData = new String(combinedDataBytes);
                    String[] parts = combinedData.split("\\|");
                    double receivedDouble = Double.parseDouble(parts[0]);
                    long receivedTime = Long.parseLong(parts[1]);
                    Instant instant = Instant.ofEpochMilli(receivedTime);
                    tiempos.add(instant);
                    // dependiendo del dato recibido hacemos envio de la informacion a la cola o
                    // anunciamos el error y si es error restamos la cantidad para que el promedio
                    // sea solo de los datos llegados correctos
                    if (receivedDouble == -1) {
                        System.out.println("Error en el sensor de Temperatura");
                        cantidad--;
                    } else {
                        cola.offer("Temperatura|" + combinedData+"|"+currentTime);
                        acumulado += receivedDouble;
                    }
                }
                proemdio = acumulado / cantidad;
                System.out.println("Temperatura promedio: " + proemdio + " de las siguientes fechas " + tiempos);
                if (proemdio >= 11 && proemdio <= 29.4) {
                    cola.offer("Tempromedio|" + proemdio+"|"+currentTime);
                } else {
                    cola.offer("Tempromedio|" + proemdio+"|"+currentTime);
                    cola.offer("Calidad|Promedio de temperatura fuera de rango");
                    AvisarCalidad();
                }
            }
        }
    }
}

class Humedad implements Runnable {
    private Queue<String> cola;

    public Humedad(Queue<String> cola) {
        this.cola = cola;
    }

    public void run() {
        // ponemos a escuchar una direccion y un puerto dependiendo del sensor
        try (ZContext context = new ZContext()) {
            ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);
            pullSocket.bind("tcp://10.43.101.10:10010");

            while (!Thread.currentThread().isInterrupted()) {
                double acumulado = 0, proemdio;
                int cantidad = 10;
                long currentTime = Instant.now().toEpochMilli();
                for (int i = 0; i < 10; i++) {
                    // hacemos la lectura del dato y lo separamos en dos variables una para el
                    // tiempo y otra para el parametro
                    byte[] combinedDataBytes = pullSocket.recv();
                    String combinedData = new String(combinedDataBytes);
                    String[] parts = combinedData.split("\\|");
                    double receivedDouble = Double.parseDouble(parts[0]);
                    if (receivedDouble == -1) {
                        System.out.println("Error en el sensor de Humedad");
                        cantidad--;
                    } else {
                        cola.offer("Humedad|" + combinedData+"|"+currentTime);
                        acumulado += receivedDouble;
                    }
                }
                proemdio = acumulado / cantidad;
                cola.offer("HumedpromedioDia|" + proemdio+"|"+currentTime);
            }
        }
    }
}