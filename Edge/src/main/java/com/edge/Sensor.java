package com.edge;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.*;
import java.net.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Random;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import java.time.Instant;
import java.util.Queue;

public class Sensor {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Debe proporcionar el tipo de sensor como argumento (Humo/Temperatura/Humedad).");
            return;
        }

        // Obtenemos el tipo de sensor del primer argumento
        String tipoDeSensor = args[0];

        // Verificamos que el tipo de sensor sea válido
        if (!tipoDeSensor.equalsIgnoreCase("Humo") && !tipoDeSensor.equalsIgnoreCase("Temperatura")
                && !tipoDeSensor.equalsIgnoreCase("Humedad")) {
            System.out.println("El tipo de sensor proporcionado no es válido.");
            return;
        }
        // declaramos variables iniciales
        int cantidadSensores = 10;
        int puerto = 0;
        Queue<String> colaCompartida = new ConcurrentLinkedQueue<>();

        // dependiendo del sensor asignamos un puerto para mandar la informacion
        if (tipoDeSensor.equals("Humo")) {
            puerto = 10000;
        } else if (tipoDeSensor.equals("Temperatura")) {
            puerto = 10005;
        } else if (tipoDeSensor.equals("Humedad")) {
            puerto = 10010;
        }

        // definimos un hilo que sera el que se encargara de mandar la informacion al
        // proxy
        Thread push = new Thread(new Push(puerto, colaCompartida));
        push.start();

        // iniciamos los 10 hilos para el sensor elegido
        for (int i = 1; i <= cantidadSensores; i++) {
            Thread sensor = new Thread(new SensorHilo(tipoDeSensor, colaCompartida));
            sensor.start();
        }
    }
}

class Push implements Runnable {
    public int puerto;
    private boolean respaldo = false;
    private Queue<String> cola;

    public Push(int puerto, Queue<String> cola) {
        this.puerto = puerto;
        this.cola = cola;
    }

    public void run() {
        Thread comprobar = new Thread(new Comprobacion());
        comprobar.start();
        // mandado de informacion obtenida por los sensores mediante la iteracion de una
        // cola que siempre y tenga datos pendientes los mandara
        // si es de hunmo y verdadero mandarlo localhost a el aspersor
        try (ZContext context = new ZContext()) {
            try (ZContext context2 = new ZContext()) {
                try (ZContext context3 = new ZContext()) {
                    ZMQ.Socket pushSocket = context.createSocket(SocketType.PUSH);
                    pushSocket.connect("tcp://10.43.100.213:" + puerto);
                    ZMQ.Socket pushSocket2 = context2.createSocket(SocketType.PUSH);
                    pushSocket2.connect("tcp://localhost:10015");
                    ZMQ.Socket PushSocket3 = context3.createSocket(SocketType.PUSH);
                    PushSocket3.connect("tcp://10.43.101.10:" + puerto);
                    while (true) {
                        while (cola.peek() != null) {
                            String[] parts = cola.peek().split("\\|");
                            double receivedDouble = Double.parseDouble(parts[0]);
                            if (puerto == 10000 && receivedDouble == 1) {
                                pushSocket2.send(cola.peek().getBytes());
                                AvisarCalidad();
                            }
                            System.out.println("Sending data: " + cola.peek());
                            if (respaldo) {
                                PushSocket3.send(cola.poll().getBytes());
                            }else{
                                pushSocket.send(cola.poll().getBytes());
                            }
                        }
                    }
                }
            }
        }
    }

    class Comprobacion implements Runnable {

        public void run() {
            try (ZContext context = new ZContext()) {
                ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
                subscriber.connect("tcp://10.43.101.10:10045");
                subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
                while (!Thread.currentThread().isInterrupted()) {
                    subscriber.recvStr();
                    respaldo = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void AvisarCalidad() {
        String hostname = "localhost";
        int port = 10020;
        try (Socket socket = new Socket(hostname, port)) {
            // Envío de la solicitud al servidor
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println("los roseadores se han activado");

            // Lectura de la respuesta del servidor
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println(in.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class SensorHilo implements Runnable {
    String tipoDeSensor;
    double correcto;
    double fuera;
    double error;
    double parametro;
    private Queue<String> cola;

    public SensorHilo(String tipoDeSensor, Queue<String> cola) {
        this.tipoDeSensor = tipoDeSensor;
        this.cola = cola;
    }

    public void run() {
        // buscamos el archivo de texto usando el mismo nombre del sensor que hayamos
        // elegido
        InputStream inputStream = Sensor.class.getClassLoader().getResourceAsStream(tipoDeSensor + ".txt");
        try {
            // lo leemos y asignamos cada valor a una variable
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            correcto = Double.parseDouble(reader.readLine());
            fuera = Double.parseDouble(reader.readLine());
            error = Double.parseDouble(reader.readLine());
            reader.close();
            // sumamos todas las probabilidades y las dividimos en cada valor con eso nos
            // aeguramos que siempre de 100%
            double sumaProbabilidades = correcto + fuera + error;
            correcto /= sumaProbabilidades;
            fuera /= sumaProbabilidades;
            error /= sumaProbabilidades;
        } catch (IOException e) {
            System.out.println("Error al leer el archivo.");
            e.printStackTrace();
            return;
        } catch (NumberFormatException e) {
            System.out.println("Error: Formato incorrecto en el archivo.");
            return;
        }
        // caso de seleccion de sensor Humo
        if (tipoDeSensor.equals("Humo")) {
            while (true) {
                // generamos un numero aleatorio y dependiendo del numero caera en acertado
                // fuera o error y asignamos un valor al parametro a mandar siendo -1 el error
                Random rand = new Random();
                double randomNumber = rand.nextDouble();
                if (randomNumber <= correcto) {
                    parametro = 0;
                } else if (randomNumber <= correcto + fuera) {
                    parametro = 1;
                } else {
                    parametro = -1;
                }
                // al parametro a mandar lo concatenamos con tiempo actual separados por | para
                // saber como romperlo al otro lado
                long currentTime = Instant.now().toEpochMilli();
                String combinedData = parametro + "|" + currentTime;
                cola.offer(combinedData);
                // tiempo de espera definido antes de volver a buscar los datos
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    System.out.println("El hilo ha sido interrumpido.");
                }
            }
            // caso de seleccion de sensor Temperatura
        } else if (tipoDeSensor.equals("Temperatura")) {
            while (true) {
                // generamos un numeor aleatorio y dependiendo del numero caera en acertado
                // fuera o error
                Random rand = new Random();
                double randomNumber = rand.nextDouble();
                if (randomNumber <= correcto) {
                    // en caso de acertado genera un numero que este dentro del rango delimitado y
                    // lo limita a 1 unidad decimal
                    Random random = new Random();
                    parametro = 11 + (29.4 - 11) * random.nextDouble();
                    parametro = Math.round(parametro * 10) / 10.0;
                } else if (randomNumber <= correcto + fuera) {
                    // en caso de fuera genera un numero que este fuera del parametro mencionado
                    // tanto para rriba como para abajo y valida que sea postivo
                    Random random = new Random();
                    double numeroAleatorio;
                    do {
                        numeroAleatorio = random.nextDouble() * 1000;
                    } while (numeroAleatorio >= 11 && numeroAleatorio <= 29.4);
                    parametro = Math.round(numeroAleatorio * 10) / 10.0;
                } else {
                    // si es error definir parametro como -1
                    parametro = -1;
                }
                // al parametro a mandar lo concatenamos con tiempo actual separados por | para
                // saber como romperlo al otro lado
                long currentTime = Instant.now().toEpochMilli();
                String combinedData = parametro + "|" + currentTime;
                cola.offer(combinedData);
                // tiempo de espera definido antes de volver a buscar los datos
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    System.out.println("El hilo ha sido interrumpido.");
                }
            }
            // caso de seleccion de sensor Humedad
        } else if (tipoDeSensor.equals("Humedad")) {
            while (true) {
                // generamos un numeor aleatorio y dependiendo del numero caera en acertado
                // fuera o error
                Random rand = new Random();
                double randomNumber = rand.nextDouble();
                if (randomNumber <= correcto) {
                    // en caso de acertado genera un numero que este dentro del rango delimitado y
                    // lo limita a 1 unidad decimal
                    Random random = new Random();
                    parametro = 70 + (100 - 70) * random.nextDouble();
                    parametro = Math.round(parametro * 10) / 10.0;
                } else if (randomNumber <= correcto + fuera) {
                    // en caso de fuera genera un numero que este fuera del parametro mencionado
                    // tanto para rriba como para abajo y valida que sea postivo
                    Random random = new Random();
                    double numeroAleatorio;
                    do {
                        numeroAleatorio = random.nextDouble() * 1000;
                    } while (numeroAleatorio >= 70 && numeroAleatorio <= 100);
                    parametro = Math.round(numeroAleatorio * 10) / 10.0;
                } else {
                    // si es error definir parametro como -1
                    parametro = -1;
                }
                // al parametro a mandar lo concatenamos con tiempo actual separados por | para
                // saber como romperlo al otro lado
                long currentTime = Instant.now().toEpochMilli();
                String combinedData = parametro + "|" + currentTime;
                cola.offer(combinedData);
                // tiempo de espera definido antes de volver a buscar los datos
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    System.out.println("El hilo ha sido interrumpido.");
                }
            }
        }

    }
}