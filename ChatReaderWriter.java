import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.util.ArrayList;

/*
 * @author Simone Schiavone MAT: 582418
 */

public class ChatReaderWriter extends Thread {
	private InetAddress indmulticast;
	private MulticastSocket socketmulticast;
	private String nomeproj;
	private int port;
	private ArrayList<String> codamessaggi;
	
	
	public ChatReaderWriter (InetAddress ia,int p, String name) {
		this.codamessaggi=new ArrayList<String>();
		this.port=p;
		this.nomeproj=name;
		try {
			this.socketmulticast=new MulticastSocket(port);
			this.indmulticast=ia;
			socketmulticast.joinGroup(indmulticast);
		}	catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public InetAddress GetAddress(){
		return this.indmulticast;
	}
	
	public String GetProject() {
		return this.nomeproj;
	}
	
	public void LeggiChat() {
		System.out.println("WorthChatService: Lista messaggi non letti del progetto "+nomeproj);
		for(String s : codamessaggi) {
			System.out.println("\t"+s);
		}
		System.out.println("---Lista terminata---");
		
		//Svuoto la chat!
		this.codamessaggi.clear();
	}
	
	public void InviaMessaggio(String text) {
		DatagramPacket pacchetto= new DatagramPacket(text.getBytes(),text.getBytes().length,indmulticast,port);
		try(DatagramSocket dsocket=new DatagramSocket()) {
			dsocket.send(pacchetto);
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e1){
			e1.printStackTrace();
		}
	}
	
	public void run() {
		try {
			while(!Thread.currentThread().isInterrupted()) {
				byte[] buffer=new byte[1024];
				DatagramPacket pacchetto= new DatagramPacket(buffer,buffer.length);
				try {
					socketmulticast.receive(pacchetto);
					if(Thread.currentThread().isInterrupted())
						break;
				} catch (IOException e) {
					e.printStackTrace();
				}
				String messaggio=new String(pacchetto.getData(),0,pacchetto.getLength());	
				codamessaggi.add(messaggio);
			}
			socketmulticast.leaveGroup(indmulticast);
		}catch(IOException e) {
			e.printStackTrace();
		}finally {
		   socketmulticast.close();			
		}
	}
}
