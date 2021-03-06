package net;

import game.Ball;
import game.Brick;
import game.Commons;
import game.PlayerMP;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import net.packets.Packet;
import net.packets.Packet.PacketTypes;
import net.packets.Packet00Login;
import net.packets.Packet02Move;
import net.packets.Packet03Brick;
import net.packets.Packet04Ball;
import App.Game;

public class GameServer extends Thread implements Commons{
  
  private DatagramSocket socket;
  private Ball[] balls = new Ball[BALL_TOTAL];
  private Brick[] bricks = new Brick[NUM_BRICKS];
  private Timer timer;
  private boolean inGame = false;
  
  private List<PlayerMP> connectedPlayers = new ArrayList<PlayerMP>();
//  private Game game;
  
//sun
public Database db;
//

  public GameServer() {
    // sun
    db = new Database();
    db.Connect();
    //
 
    try {
      this.socket = new DatagramSocket(3333);
      timer = new Timer();
      timer.scheduleAtFixedRate(new ScheduleTask(), 1000, 10);
    } catch (SocketException e) {
      e.printStackTrace();
    }
  }
  
  class ScheduleTask extends TimerTask {

    public void run() {
      if(inGame) {
        for(PlayerMP p : connectedPlayers) {
//          System.out.println("sending scale: " + p.getScale());
          Packet02Move packet = new Packet02Move(p.getUsername(), p.getX(), p.getY(), p.getScale(), p.getLifeLeft(), p.getScore());
          sendDataToAllClients(packet.getData()); 
        }
          boolean[] bricksBool = new boolean[NUM_BRICKS]; 
          for(int i = 0; i < NUM_BRICKS; i++) {
            if(bricks[i].isDestroyed())
              bricksBool[i] = false;
            else
              bricksBool[i] = true;
          } 
//          send bricks to all
          Packet03Brick brickPacket = new Packet03Brick(bricksBool);
          sendDataToAllClients(brickPacket.getData());
//          send ball to all
          for(int i = 0; i < BALL_TOTAL; i++) {
//            System.out.println("Server sending back ball: " + i + "x:" + balls[i].getX());
            Packet04Ball ballPacket = new Packet04Ball(i, balls[i].getX(), balls[i].getY(), balls[i].isLost());
            sendDataToAllClients(ballPacket.getData());
          }
      }
    }
  }
  
  public void run() {
    while(true) {
      byte[] data = new byte[1024];
      DatagramPacket packet = new DatagramPacket(data, data.length);
      try {
        socket.receive(packet);
      } catch (IOException e) {
        e.printStackTrace();
      }
      this.parsePacket(packet.getData(), packet.getAddress(), packet.getPort());
    }
  }
  
//  take all packets we get, find what packet it is and how to deal with it
  private void parsePacket(byte[] data, InetAddress address, int port) {
    String message = new String(data).trim(); 
    PacketTypes type = Packet.lookupPacket(message.substring(0, 2));
    Packet packet = null;
    
    switch(type) {
    default:
    case INVALID:
      break;
    case LOGIN:
      packet = new Packet00Login(data);
      handleLogin((Packet00Login)packet, address, port);      
      break;
    case DISCONNECTED:
      break;
    case MOVE:
      packet = new Packet02Move(data);
      handleMove((Packet02Move)packet, address, port);
    }
  }

  /**
   * player is the player we want to add
   */
  public void addConnection(PlayerMP player, Packet00Login packet) {
    for (PlayerMP p : this.connectedPlayers) {
            // relay to the new player that the currently connect player
            // exists
            Packet00Login pc = new Packet00Login(p.getUsername(), p.getPassword(), p.getPosition(), p.getX(), p.getY(), p.getScore(), p.getValid());
            sendData(pc.getData(), player.ipAddress, player.port);
//        }
    }
//    send back to the client itself;
    Packet00Login pcToPlayer = new Packet00Login(player.getUsername(), player.getPassword(), player.getPosition(), player.getX(), player.getY(), player.getScore(), player.getValid());
    sendData(pcToPlayer.getData(), player.ipAddress, player.port);
//    send to all connected player, we have a new one
    sendDataToAllClients(pcToPlayer.getData());
      this.connectedPlayers.add(player);
//    }
  }

  public void sendData(byte[] data, InetAddress ipAddress, int port) {
    DatagramPacket packet = new DatagramPacket(data, data.length, ipAddress, port);
    try {
      this.socket.send(packet);
    } catch (IOException e) {
      e.printStackTrace();
    }
//    System.out.println("Server sending: " + new String(packet.getData()));
  }

  public void sendDataToAllClients(byte[] data) {
    for(PlayerMP p : connectedPlayers) {
      sendData(data, p.ipAddress, p.port);
    }
  }
  
  public int getNumPlayers() {
    return connectedPlayers.size();
  }
  
  private void handleLogin(Packet00Login packet, InetAddress address, int port) {
    System.out.println(address.getHostAddress() + ": " + port
        + " " + packet.getUsername() + " has connected...");
    // sun password
    PlayerMP player = new PlayerMP(packet.getUsername(), packet.getPassword(), packet.getX(), packet.getY(), address, port);
    // sun authorize player
    if (db.Authorize(player.getUsername(), player.getPassword()) )
      player.setValid(1);
    else
    {
      Packet00Login pc = new Packet00Login(packet.getUsername(), "", -1, -1, -1, -1, 0);
      sendData(pc.getData(), player.ipAddress, player.port);
      return;
    };
    //

    // sun
    // assume connected players are all different
//    set the position of the new added player
    player.setPosition(connectedPlayers.size() + 1);
    this.addConnection(player, packet);
    if(getNumPlayers() == NUM_PLAYERS) {
      System.out.println("RRRRRRRRRReady");
      new Game(connectedPlayers, this, this.balls, this.bricks);
      inGame = true;
    }
  }
  
  private void handleMove(Packet02Move packet, InetAddress address, int port) {
//    System.out.println(address.getHostAddress() + ": " + port
//        + " " + packet.getUsername() + " has moved..."); 
    for(PlayerMP player : connectedPlayers) {
      if(player.getUsername().equalsIgnoreCase(packet.getUsername())) {
        player.set(packet.getX(), packet.getY());
        break;
      }
    }
  }
}