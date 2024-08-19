package com.dyx.simpledb.client;

import com.dyx.simpledb.transport.Encoder;
import com.dyx.simpledb.transport.Packager;
import com.dyx.simpledb.transport.Transporter;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * @User Administrator
 * @CreateTime 2024/8/14 22:48
 * @className com.dyx.simpledb.client.l3
 */
public class l3 {
    public static void main(String[] args) throws UnknownHostException, IOException {
        Socket socket = new Socket("127.0.0.1", 9999);
        Encoder e = new Encoder();
        Transporter t = new Transporter(socket);
        Packager packager = new Packager(t, e);

        Client client = new Client(packager);
        Shell shell = new Shell(client);
        shell.run();
    }
}
