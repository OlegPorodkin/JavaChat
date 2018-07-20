package ru.javachat.core.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String nick;

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            new Thread(() -> innerThread()).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void innerThread() {
        try {
            while (true) {
                final Timer timer = new Timer();
                String msg = in.readUTF();
                if (msg.startsWith("/auth ")) {
                    String[] data = msg.split("\\s");
                    if (data.length == 3) {
                        String newNick = server.getAuthService().getNickByLoginAndPass(data[1], data[2]);
                        if (newNick != null) {
                            if (!server.isNickBusy(newNick)) {
                                nick = newNick;
                                ClientHandler.this.sendMsg("/authok " + newNick);
                                server.subscribe(ClientHandler.this);
                                break;
                            } else {
                                ClientHandler.this.sendMsg("Учетная запись уже занята");
                                timer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        if (nick == null){
                                            closeConnect();
                                        }
                                    }
                                }, 120_000);
                            }
                        } else {
                            ClientHandler.this.sendMsg("Неверный логин/пароль");
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    if (nick == null){
                                        closeConnect();
                                    }
                                }
                            }, 120_000);
                        }
                    }
                }
            }

            while (true) {
                String msg = in.readUTF();
                System.out.println(nick + ": " + msg);
                if (msg.startsWith("/")) {
                    if (msg.equals("/end")) break;
                    if (msg.startsWith("/w ")) { // /w nick1 hello friend
                        String data[] = msg.split("\\s", 3);
                        server.sendPrivateMsg(ClientHandler.this, data[1], data[2]);
                    }
                } else {
                    server.broadcastMsg(nick + ": " + msg);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeConnect();
        }
    }

    private void closeConnect(){
        ClientHandler.this.sendMsg("Превышенно время ожидания!");
        nick = null;
        server.unsubscribe(ClientHandler.this);
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNick() {
        return nick;
    }

    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
