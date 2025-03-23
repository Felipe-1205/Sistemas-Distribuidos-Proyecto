package com.edge;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class Aspersor {
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket pullSocket = context.createSocket(SocketType.PULL);
            pullSocket.bind("tcp://localhost:10015");
            while (!Thread.currentThread().isInterrupted()) {
                //esperamos que llegue el dato y si llega se activa
                pullSocket.recv();
                System.out.println("Se activo el aspersor");
            }
        } 
    }
}
