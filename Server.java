import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

/*
 * @author Simone Schiavone MAT: 582418
 */

public class Server extends RemoteObject implements ServerInterfacciaRMI{
	private static final long serialVersionUID = 123456789L;
	
	private ArrayList<Progetto> listaprogetti;
	private ArrayList<Utente> utentiregistrati;
	private HashMap<ClientInterfacciaRMI,String> callbackinfo;
	private HashMap<SocketAddress,String> useraddress; //Socketaddress-username
	
	private File fileutenti;
	private File filebackup;
	private Gson gson;
	private final static int portaRMI=6789;
	private final static int portaTCP=9876 ;
	private final static int BufferDIM = 128;
	private final static String terminatore="%";
	
	
	
	//Metodo costruttore
	public Server() {
		super();
		this.listaprogetti=new ArrayList<Progetto>();
		this.utentiregistrati=new ArrayList<Utente>();
		this.filebackup=new File("./Backup/Progetti");
		filebackup.mkdirs();
		this.fileutenti=new File("./Backup/utentiregistrati.json"); //file usato per la memorizzazione degli utenti registrati
		this.gson=new GsonBuilder().setPrettyPrinting().create();
		this.callbackinfo=new HashMap<ClientInterfacciaRMI,String>();
		this.useraddress=new HashMap<SocketAddress,String>();
	}
	
	public synchronized int Register(String nickUtente, String password) {
		if(nickUtente==null)
			return 1; //codice che indica che il nickutente e' nullo
		if(password==null)
			return 2; //codice che indica che la password e' nulla
		
		for(Utente u : utentiregistrati) { //verifico che non vi siano altri utenti con lo stesso username
			if(u.GetUsername().equals(nickUtente))
				return 3; //codice che indica che l'utente e' gia' stato registrato
		}
	
		Utente nuovoutente=new Utente(nickUtente,password);
		utentiregistrati.add(nuovoutente); //aggiungo alla lista di utenti registrati il nuovo utente
		
		SalvataggioUtenti(); 
		//invoco il metodo della classe server che si occupa della persistenza degli utenti registrati
		
		//notifichiamo gli utenti registrati al servizio di notifica il fatto che ora si e' iscritto un nuovo utente
		UpdateStatus(nickUtente,"offline");
		
		System.out.println(nickUtente+" si e' registrato!");
		return 0; //operazione avvenuta correttamente
	}
	
	private void SalvataggioUtenti() {
		//Metodo che si occupa di effettuare la serializzazione della lista di utenti registrati
		try {
			//Metodo del pacchetto Gson per la serializzazione
			String stringajson=gson.toJson(utentiregistrati);
			//Scrittura della stringa in JSON sul file .json con NIO
			WritableByteChannel channel=Channels.newChannel(new FileOutputStream(fileutenti));
			ByteBuffer buffer=ByteBuffer.allocateDirect(stringajson.getBytes().length);
			buffer.put(stringajson.getBytes());
			buffer.flip();
			channel.write(buffer);
			channel.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void CreaCartellaBackupProgetto(String projectname) {
		if(!filebackup.exists()) {
			System.out.println("ServerWORTH: Non e' possibile salvare il progetto perche' non c'e' la cartella di backup");
			return;
		}
		
		//Costruzione del percorso
		StringBuilder path=new StringBuilder();
		path.append(filebackup.toString());
		path.append("/");
		path.append(projectname);
		
		//Crezione della cartella se non esiste gia'
		File fpath=new File(path.toString());
		if(fpath.exists()) {
			System.out.println("ServerWORTH: La cartella "+projectname+" esiste gia'!");
		}else {
			fpath.mkdir();
			System.out.println("ServerWORTH: La cartella "+projectname+" e' stata creata");
		}
	}
	
	private void SalvataggioMembriProgetto(String projectname) {
		if(!filebackup.exists()) {
			System.out.println("ServerWORTH: Non e' stata trovata la cartella di backup dei progetti");
			return;
		}
		//recupero la lista degli utenti del progetto
		ArrayList<Utente> members= GetProjectByName(projectname).GetMembri();
		
		//Creazione del percorso
		StringBuilder path=new StringBuilder();
		path.append(filebackup.toString());
		path.append("/");
		path.append(projectname);
		File fpath=new File(path.toString());
		if(fpath.exists()) {
			try {
				//Metodo del pacchetto Gson per la serializzazione
				String stringajson=gson.toJson(members);
				//Scrittura della stringa in JSON sul file .json con NIO
				File filemembri=new File(path.append("/membri.json").toString());
				WritableByteChannel channel=Channels.newChannel(new FileOutputStream(filemembri));
				ByteBuffer buffer=ByteBuffer.allocateDirect(stringajson.getBytes().length);
				buffer.put(stringajson.getBytes());
				buffer.flip();
				channel.write(buffer);
				channel.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}else {
			System.out.println("ServerWORTH: Non e' stata provata la cartella di backup del progetto "+projectname);
		}
	}
	
	private void SalvataggioCartaProgetto(String projectname, Card c) {
		if(!filebackup.exists()) {
			System.out.println("ServerWORTH: Non e' stata trovata la cartella di backup dei progetti");
			return;
		}
		
		//Creazione del percorso
		StringBuilder path=new StringBuilder();
		path.append(filebackup.toString());
		path.append("/");
		path.append(projectname);
		File fpath=new File(path.toString());
		
		if(fpath.exists()) {
			try {
				//Metodo del pacchetto Gson per la serializzazione
				String stringajson=gson.toJson(c);
				//Scrittura della stringa in JSON sul file .json con NIO
				path.append("/");
				path.append(c.GetName());
				path.append(".json");
				File filemembri=new File(path.toString());
				WritableByteChannel channel=Channels.newChannel(new FileOutputStream(filemembri));
				ByteBuffer buffer=ByteBuffer.allocateDirect(stringajson.getBytes().length);
				buffer.put(stringajson.getBytes());
				buffer.flip();
				channel.write(buffer);
				channel.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}else {
			System.out.println("ServerWORTH: Non e' stata trovata la cartella di backup del progetto "+projectname);
		}
	}
	
	private void SalvataggioIpMulticast(String projectname,InetAddress ip) {
		if(!filebackup.exists()) {
			System.out.println("ServerWORTH: Non e' stata trovata la cartella di backup dei progetti");
			return;
		}
		
		//Creazione del percorso
		StringBuilder path=new StringBuilder();
		path.append(filebackup.toString());
		path.append("/");
		path.append(projectname);
		File fpath=new File(path.toString());
		
		if(fpath.exists()) {
			try {
				//Metodo del pacchetto Gson per la serializzazione
				String stringajson=gson.toJson(ip);
				//Scrittura della stringa in JSON sul file .json con NIO
				path.append("/ipchat.json");
				File fileipchat=new File(path.toString());
				WritableByteChannel channel=Channels.newChannel(new FileOutputStream(fileipchat));
				ByteBuffer buffer=ByteBuffer.allocateDirect(stringajson.getBytes().length);
				buffer.put(stringajson.getBytes());
				buffer.flip();
				channel.write(buffer);
				channel.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}else {
			System.out.println("ServerWORTH: Non e' stata trovata la cartella di backup del progetto "+projectname);
		}
	}
	
	private void RipristinoProgetti() {
		if(!filebackup.exists()) {
			System.out.println("ServerWORTH: Non e' stata trovata la cartella di backup dei progetti");
			return;
		}
		File[] cartelleprogetti=filebackup.listFiles();
		
		for(File f : cartelleprogetti) {
			//Recuperiamo tutti i progetti
			if(f.isDirectory()) {
				Progetto p=new Progetto(f.getName()); //Creazione nuovo progetto recuperando nome della cartella
				listaprogetti.add(p);
				
				File[] fileprogetto=f.listFiles();
				for(File w : fileprogetto) {
					if(w.isFile()) {
						StringBuilder stringajsonb=new StringBuilder();
						String stringajson = null;
						//Lettura della stringa JSON contenuta nel file .json con NIO
						try(ReadableByteChannel channel=Channels.newChannel(new FileInputStream(w))) {
							ByteBuffer buffer=ByteBuffer.allocateDirect((int)w.length());
							channel.read(buffer);
							buffer.flip(); 
		
							while(buffer.position()<(int)(w.length())) {
								byte extracted=buffer.get();
								stringajsonb.append((char)extracted);
							}
							
							stringajson=stringajsonb.toString();
						}catch(FileNotFoundException e) {
							e.printStackTrace();
						}catch(IOException e) {
							e.printStackTrace();
						}
						
						//recupero dei membri del progetto
						if(w.getName().equals("membri.json")) { 
							//Deserializzazione
							JsonArray array=JsonParser.parseString(stringajson).getAsJsonArray();
							for(int i=0;i<array.size();i++) {
								p.GetMembri().add(gson.fromJson(array.get(i), Utente.class));
							}
						}else {
							//recupero ip multicast della chat del progetto
							if(w.getName().equals("ipchat.json")) {
								//Deserializzazione
								InetAddress ip=gson.fromJson(stringajson, InetAddress.class);
								p.AssegnaIpChat(ip);
							}else {
								//recupero card del progetto
								Card c=gson.fromJson(stringajson,Card.class);
								p.GetCards().add(c);
								switch(c.GetStatus()) {
									case "todo":
										p.GetTODOCards().add(c);
										break;
									case "inprogress":
										p.GetINPROGRESSCards().add(c);
										break;
									case "toberevised":
										p.GetTOBEREVISEDCards().add(c);
										break;
									case "done":
										p.GetDONECards().add(c);
										break;
									default:
										System.out.println("ServerWORTH: non so in quale lista inserire la carta "+c.GetName()+" del progetto "+p.GetName());
								}
							}
						}
					}
				}
			}
		}
	}
	
	private void RipristinoUtenti() {
		//Metodo che si occupa di ricostruire la lista degli utenti registrati a partire dal file .json
		if(fileutenti.exists()) {
			StringBuilder stringajsonb=new StringBuilder();
			String stringajson = null;
			//Lettura della stringa JSON contenuta nel file .json con NIO
			try(ReadableByteChannel channel=Channels.newChannel(new FileInputStream(fileutenti))) {
				ByteBuffer buffer=ByteBuffer.allocateDirect((int)fileutenti.length());
				channel.read(buffer);
				buffer.flip(); 
				
				while(buffer.position()<(int)(fileutenti.length())) {
					byte extracted=buffer.get();
					stringajsonb.append((char)extracted);
				}
				
				stringajson=stringajsonb.toString();
				
			}catch(FileNotFoundException e) {
				e.printStackTrace();
			}catch(IOException e) {
				e.printStackTrace();
			}
			
			//Deserializzazione
			JsonArray array=JsonParser.parseString(stringajson).getAsJsonArray();
			for(int i=0;i<array.size();i++) {
				Utente current=gson.fromJson(array.get(i), Utente.class);
				current.Offline();
				this.utentiregistrati.add(current);
			}
		}			
	}
	
	private void CancellaCartella(String name) {
		System.out.println("ServerWORTH: Avvio procedura cancellazione progetto "+name+" da cartella di backup");
		File[] progetti=filebackup.listFiles();
		for(File f : progetti) {
			
			if(f.getName().equals(name)) {
				//Per essere cancellata la cartella deve essere vuota, rimuovo prima tutti i file
				for(File w : f.listFiles()) {
					String filename=w.getName();
					String risposta= (w.delete() ? "\t Il file "+filename+" e' stato correttamente cancellato" : "\t Il file "+filename+"NON e' stato cancellato");
					System.out.println(risposta);
				}
				String filename=f.getName();
				String risposta= (f.delete() ? "ServerWORTH: La cartella "+filename+" e' stata cancellata" : "ServerWORTH: La cartella "+filename+"NON e' stata cancellata");
				System.out.println(risposta);
			}
		}
	}
	
	private void PubblicazioneStubRMI() {
		try {
			ServerInterfacciaRMI stub = (ServerInterfacciaRMI) UnicastRemoteObject.exportObject(this,0); 
			
			//Creazione di un registry sulla porta "portaRMI"
			LocateRegistry.createRegistry(portaRMI);
			Registry registry=LocateRegistry.getRegistry(portaRMI);
			//Pubblicazione dello stub nel registry. Il client poi reperira' lo stub che abbiamo pubblicato
			registry.rebind("SERVERWORTH", stub);
		}catch(RemoteException re) {
			System.err.println("ServerWORTH: RemoteException");
			return;
		}
		System.out.println("ServerWORTH: SERVERWORTH Pubblicato");
	}
	
	private String Login(String username,String password,SocketAddress addr) {
		if(username==null || password ==null || password=="" || username=="")
			return "Login FALLITO: username e/o password mancante";
		
		for(Utente u : utentiregistrati) {
			if(u.GetUsername().equals(username))
				if(u.VerificaPassword(password)) {
					if(u.SetOnline()==1) {
						UpdateStatus(username,"online");
						this.useraddress.put(addr, username);
						return "Login EFFETTUATO; Benvenuto/a "+username;
					}else {
						return"Login FALLITO: "+username+" e' gia' loggato";
					}
				}else {
					return "Login FALLITO: password errata";
				}
		}
		return "Login FALLITO: "+username+" non e' registrato a WORTH!";
	}
	
	private String Logout(String username, SocketAddress addr) {
		for(Utente u : utentiregistrati) {
			if(u.GetUsername().equals(username)) {
				if(u.SetOffline()==1) {
					UpdateStatus(username,"offline");
					this.useraddress.remove(addr);
					return "Logout EFFETTUATO; Arrivederci "+username;
				}else {
					return "Logout FALLITO: "+username+" non e' online";
				}
			}
		}
		return "Logout FALLITO: "+username+" non e' registrato a WORTH!";
	}
	
	private String Listusers(){
		ArrayList<String> usernames=new ArrayList<String>();
		//Non voglio inviare al client una lista di utenti contenente per ogni utente anche
		//i suoi dati sensibili (password) percio' invio solamente la rappresentazione json 
		//della lista di stringhe composte dalla concatenazione di "username" e "stato"
		//("Online"/"Offline")
		for(Utente e : utentiregistrati) {
			String stato=(e.isOnline()==true) ? "Online" : "Offline";
			usernames.add(e.GetUsername()+" "+stato);
		}
		
		String jsonrep=gson.toJson(usernames);
		return jsonrep;
	}
	
	private String Listonlineusers(){
		ArrayList<String> usernames=new ArrayList<String>();
		//Non voglio inviare al client una lista di utenti contenente per ogni utente anche
		//i suoi dati sensibili (password) percio' invio solamente la rappresentazione json 
		//della lista di username
		for(Utente e : this.utentiregistrati) {
			if(e.isOnline())
				usernames.add(e.GetUsername());
		}
		
		String jsonrep=gson.toJson(usernames);
		return jsonrep;
	}
	
	private String ListProjects(String username) {
		ArrayList<String>listprojects= new ArrayList<String>();
		for(Progetto p : listaprogetti) {
			if(p.isMember(username))
				listprojects.add(p.GetName());
		}
		
		String jsonrep=gson.toJson(listprojects);
		return jsonrep;
	}
	
	private String CreateProject(String nomeproj, String username) {
		//Recupero utente dalla lista degli utenti
		Utente creatore=GetUserByName(username);
				
		//Verifichiamo che non esistano progetti con lo stesso nome
		if(!(GetProjectByName(nomeproj)==null))
			return "Operazione createproject FALLITA: progetto con lo stesso nome gia' esistente!";
		
		Progetto nuovoprogetto=new Progetto(nomeproj,creatore);
		//il metodo costruttore del progetto mette gia' il creatore come membro del progetto creato
		
		//Generazione ip multicast per la chat relativa a questo progetto
		InetAddress random=GeneraMulticastAddress();
		if(random==null)
			return "Operazione createproject FALLITA: non ho piu' indirizzi di multicast liberi per la chat di progetto";
		nuovoprogetto.AssegnaIpChat(random);
		
		//Inserimento del nuovo progetto in lista
		listaprogetti.add(nuovoprogetto); 
		
		//Creazione della cartella di backup di questo progetto e salvataggio membri ed ip chat
		CreaCartellaBackupProgetto(nomeproj);
		SalvataggioMembriProgetto(nomeproj);
		SalvataggioIpMulticast(nomeproj,random);
		
		//Tramite RMI faccio partire un thread nel client associato all'username del creatore
		//che sta in ascolto sull'indirizzo di multicast individuato in attesa di messaggi
		ClientInterfacciaRMI c=GetClientByName(username);
		if(!(c==null)) {
			try {
				c.AvviaRicezione(random, nomeproj);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		
		return "Progetto "+nomeproj+" creato correttamente!";
	}

	private String AddMember(String projectname,String utentedaaggiungere,String richiedente) {
		//verifichiamo che l'utente da aggiungere sia registrato e recuperiamolo
		Utente salvautente=GetUserByName(utentedaaggiungere);
		if(salvautente==null)
			return "Operazione addmember FALLITA: "+utentedaaggiungere+" non e' registrato/a a WORTH";
		
		//verifichiamo che il progetto esista
		Progetto project=GetProjectByName(projectname);
		if(project==null) {
			return "Operazione addmember FALLITA: il progetto "+projectname+" non esiste";
		}
		
		//verifichiamo che l'utente richiedente sia partecipante
		if(!project.isMember(richiedente))
			return "Operazione addmember FALLITA: non sei un partecipante al progetto "+projectname;

		//verifichiamo che l'utente da aggiungere non sia gia' un partecipante
		if(project.isMember(utentedaaggiungere))
			return "Operazione addmember FALLITA: "+utentedaaggiungere+" partecipa gia' al progetto "+projectname;
		
		//Se i controlli precedenti sono andati a buon fine aggiungiamo il nuovo membro
		project.GetMembri().add(salvautente);
		
		//Tramite RMI faccio partire un thread, nel client associato all'username dell'utente aggiunto,
		//che sta in ascolto sull'indirizzo di multicast individuato in attesa di messaggi
		ClientInterfacciaRMI c=GetClientByName(utentedaaggiungere);
		if(!(c==null)) {
			try {
				c.AvviaRicezione(project.GetIpChat(), project.GetName());
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		
		//Salvataggio nella cartella di backup della nuova lista di membri
		SalvataggioMembriProgetto(projectname);
		
		return "Operazione addmember EFFETTUATA: adesso "+utentedaaggiungere+" partecipa al progetto "+projectname;
	}
	
	private String ShowMembers(String projectname, String richiedente){
		//verifichiamo che il progetto esista
		Progetto project=GetProjectByName(projectname);
		if(project==null) {
			return "Operazione ShowMembers FALLITA: il progetto "+projectname+" non esiste";
		}
		
		//verichiamo che l'utente sia partecipante al progetto
		if(!project.isMember(richiedente))
			return "Operazione showmembers FALLITA: non sei un partecipante al progetto "+projectname;
		
		//Costruzione della risposta contenente solo i nomi dei partecipanti (non voglio inviare anche la loro psw)
		ArrayList<String>risposta=new ArrayList<String>();
		for(Utente u : project.GetMembri()) {
			risposta.add(u.GetUsername());
		}
		String jsonrep=gson.toJson(risposta);
		return jsonrep;
	}
	
	private String ShowCards(String projectname, String richiedente) {
		//verifichiamo che il progetto esista
		Progetto project=GetProjectByName(projectname);
		if(project==null) {
			return "Operazione showcards FALLITA: il progetto "+projectname+" non esiste";
		}
		
		//verichiamo che l'utente sia partecipante al progetto
		if(!project.isMember(richiedente))
			return "Operazione showcards FALLITA: non sei un partecipante al progetto "+projectname;
		
		//Costruzione della risposta contenente solo i nomi delle cards
		ArrayList<String>risposta=new ArrayList<String>();
		for(Card c : project.GetCards()) {
			risposta.add(c.GetName());
		}
		String jsonrep=gson.toJson(risposta);
		return jsonrep;
	}
	
	private String AddCard(String[] comando) {
		String projectname=comando[1];
		String cardname=comando[2];
		int cmdlength=comando.length;
		String richiedente=comando[cmdlength-1];
		
		//la card non puo' avere come nome "ipchat" o "membri" perche' interferirebbe con i file di backup del progetto
		if(cardname.equals("ipchat")||cardname.equals("membri"))
			return "Operazione addcard FALLITA: non e' possibile creare una card con nome "+cardname;
		
		//Ho bisogno di ricostruire la descrizione che puo' essere composta da piu' parole che il metodo
		//split ha separato.
		String descrizione=comando[3];
		for(int i=4;i<comando.length-1;i++) {
			descrizione=descrizione+" ";
			descrizione=descrizione+comando[i];
		}
		 
		//verifichiamo che il progetto esista
		Progetto project=GetProjectByName(projectname);
		if(project==null) {
			return "Operazione addcard FALLITA: il progetto "+projectname+" non esiste";
		}
		
		//verichiamo che l'utente sia partecipante al progetto
		if(!project.isMember(richiedente))
			return "Operazione addcard FALLITA: non sei un partecipante al progetto "+projectname;
		
		//provvediamo all'inserimento della carta nel progetto se non ne esiste gia' una con lo stesso nome
		Card c=project.existsCard(cardname);
		if(!(c==null))
			return "Operazione addcard FALLITA: la carta "+cardname+" e' gia' presente nel progetto "+projectname;
		Card nuovacarta=new Card(cardname, descrizione);
		project.GetCards().add(nuovacarta);
		project.GetTODOCards().add(nuovacarta); //carta messa inizialmente nella lista todo
		
		//Aggiorno la card history
		nuovacarta.NuovoMovimento(richiedente+" ha creato la carta "+nuovacarta.GetName());
		
		//Salvataggio della carta nella cartella di backup del progetto
		SalvataggioCartaProgetto(projectname,nuovacarta);
		return "Operazione addcard EFFETTUATA: e' stata aggiunta la carta "+cardname+" al progetto "+projectname;
	}
	
	private String ShowCard(String projectname,String cardname,String richiedente) {
		//verifichiamo che il progetto esista
		Progetto project=GetProjectByName(projectname);
		if(project==null) {
			return "Operazione showcard FALLITA: il progetto "+projectname+" non esiste";
		}
		
		//verichiamo che l'utente sia partecipante al progetto
		if(!project.isMember(richiedente))
			return "Operazione showcard FALLITA: non sei un partecipante al progetto "+projectname;
		
		//verifichiamo se la card esiste in quel progetto
		Card c=project.existsCard(cardname);
		if(c==null)
			return "Operazione showcard FALLITA: la carta "+cardname+" non e' presente nel progetto "+projectname;
		
		return "NomeCarta: "+c.GetName()+"   Descrizione: "+c.GetDescrizione()+"   Status: "+c.GetStatus();
	}
	
	private String MoveCard(String projectname, String cardname, String listapartenza, String listadestinazione,String richiedente) {
		//verifichiamo che il progetto esista
		Progetto project=GetProjectByName(projectname);
		if(project==null) {
			return "Operazione movecard FALLITA: il progetto "+projectname+" non esiste";
		}
		
		//verichiamo che l'utente sia partecipante al progetto
		if(!project.isMember(richiedente))
			return "Operazione movecard FALLITA: non sei un partecipante al progetto "+projectname;

		//verifichiamo che la card esista in quel progetto
		Card target=project.existsCard(cardname);
		if(target==null) {
			return "Operazione movecard FALLITA: la carta "+cardname+" non e' presente nel progetto "+projectname;
		}
			
		//verifica correttezza transizioni di lista
		String risposta=project.MoveCard(target, listapartenza.trim(),listadestinazione.trim()); 
		
		//aggiungiamo un nuovo movimento alla "storia" della carta ed aggiorniamo
		if(risposta.startsWith("Operazione movecard EFFETTUATA:")) {
			target.NuovoMovimento(richiedente+" ha spostato la carta "+cardname+" da "+listapartenza+" a "+listadestinazione);
			SalvataggioCartaProgetto(projectname,target);
			
		}	
		return risposta;
	}
	
	private String GetCardHistory(String projectname, String cardname, String richiedente){
		//verifichiamo che il progetto esista
		Progetto project=GetProjectByName(projectname);
		if(project==null) {
			return "Operazione getcardhistory FALLITA: il progetto "+projectname+" non esiste";
		}
		
		//verichiamo che l'utente sia partecipante al progetto
		if(!project.isMember(richiedente))
			return "Operazione getcardhistory FALLITA: non sei un partecipante al progetto "+projectname;
		
		//verifichiamo che la card esista in quel progetto
		Card target=project.existsCard(cardname);
		if(target==null) {
			return "Operazione getcardhistory FALLITA: la carta "+cardname+" non e' presente nel progetto "+projectname;
		}		
		
		ArrayList<String> risposta=target.GetMovimenti();
		String jsonrep=gson.toJson(risposta);
		return jsonrep;
	}
	
	private String CancelProject(String projectname, String richiedente) {
		//verifichiamo che il progetto esista
		Progetto project=GetProjectByName(projectname);
		if(project==null) {
			return "Operazione cancelproject FALLITA: il progetto "+projectname+" non esiste";
		}
		
		//verichiamo che l'utente sia partecipante al progetto
		if(!project.isMember(richiedente))
			return "Operazione cancelproject FALLITA: non sei un partecipante al progetto "+projectname;
		
		//verifichiamo che tutte le card del progetto siano in lista done
		Boolean condition=true;
		StringBuilder notdone=new StringBuilder("{");
		for(Card c : project.GetCards()) {
			if(!c.GetStatus().equals("done")) {
					condition=false;
					notdone.append(c.GetName()+" ");
			}
		}
		if(condition==true) {
			CancellaCartella(projectname);
			this.listaprogetti.remove(project);
			
			//Notifichiamo i membri del progetto del fatto che il progetto e' stato cancellato
			//Quindi i thread ascoltatori della chat devono essere interrotti
			for(Utente u : project.GetMembri()) {
				ClientInterfacciaRMI c=GetClientByName(u.GetUsername());
				if(!(c==null)) {
					try {
						c.StopRicezione(project.GetIpChat());
					} catch (RemoteException e) {
						System.err.println("Remote exception nella notifica di "+u.GetUsername());
						e.printStackTrace();
					}
				}
				
			}
			return "Operazione cancelproject EFFETTUATA: il progetto "+projectname+" e' stato cancellato";
		}else {
			notdone.append("}");
			return "Operazione cancelproject FALLITA: le card "+notdone.toString()+" del progetto "+projectname+" non sono nello stato DONE!";
		}
		
	}
	
	private String InterpretaComando(String[] comando, SocketAddress addr) {
		//Metodo che si occupa di interpretare la stringa ricevuta dal client
		//invocando un metodo che risponda alla richiesta dell'utente.
		
		if(comando.length==0)
			return "ERRORE Il comando ricevuto e' vuoto!";
		
		String risposta=null;
		switch(comando[0]) {
			case	"login":
				if(comando.length>3)
					return "Login FALLITO: troppi argomenti";
				if(comando.length<3)
					return "Login FALLITO: argomenti mancanti";
				risposta=Login(comando[1],comando[2],addr);
				break;
			case 	"logout":
				if(comando.length>2)
					return "Logout FALLITO: troppi argomenti";
				if(comando.length<2)
					return "Logout FALLITO: argomenti mancanti";
				risposta=Logout(comando[1],addr);
				break;
			case	"listusers":
				if(comando.length>1)
					return "Operazione listusers FALLITA: troppi argomenti";
				risposta=Listusers();
				break;
			case	"listonlineusers":
				if(comando.length>1)
					return "Operazione listonlineusers FALLITA: troppi argomenti";
				risposta=Listonlineusers();
				break;
			case 	"listprojects":
				if(comando.length>2) //Suppongo che in posizione 1 vi sia l'username di chi invoca il metodo
					return "Operazione listprojects FALLITA: troppi argomenti";
				if(comando[1]==null) {
					return "Operazione listproject FALLITA: nessun utente ha fatto il login";
				}
				risposta=ListProjects(comando[1]);
				break;
			case	"createproject":
				if(comando.length>3)
					return "Operazione createproject FALLITA: troppi argomenti";
				if(comando.length<3)
					return "Operazione createproject FALLITA: argomenti mancanti";
				risposta=CreateProject(comando[1],comando[2]);
				break;
			case	"addmember":
				if (comando.length>4)
					return "Operazione addmember FALLITA: troppi argomenti";
				if (comando.length<4)
					return "Operazione addmember FALLITA: pochi argomenti";
				risposta=AddMember(comando[1],comando[2],comando[3]); //Nell'ultima posizione avro' l'username di chi invoca il metodo
				break;
			case	"showmembers":
				if (comando.length>3)
					return "Operazione showmembers FALLITA: troppi argomenti";
				if (comando.length<3)
					return "Operazione showmembers FALLITA: pochi argomenti";
				risposta=ShowMembers(comando[1],comando[2]); //Nell'ultima posizione avro' l'username di chi invoca il metodo
				break;
			case	"showcards":
				if (comando.length>3)
					return "Operazione showcards FALLITA: troppi argomenti";
				if (comando.length<3)
					return "Operazione showcards FALLITA: pochi argomenti";
				risposta=ShowCards(comando[1],comando[2]); //Nell'ultima posizione avro' l'username di chi invoca il metodo
				break;
			case	"addcard":
				//Non posso sapere se ci sono troppi argomenti perche' non so quando e' grande la descrizione della card
				if (comando.length<5)
					return "Operazione addcard FALLITA: pochi argomenti";
				risposta=AddCard(comando); //Nell'ultima posizione avro' l'username di chi invoca il metodo
				break;
			case	"showcard":
				if (comando.length>4)
					return "Operazione showcard  FALLITA: troppi argomenti";
				if (comando.length<4)
					return "Operazione showcard FALLITA: pochi argomenti";
				risposta=ShowCard(comando[1],comando[2],comando[3]);
				break;
			case	"movecard":
				if (comando.length>6)
					return "Operazione movecard FALLITA: troppi argomenti";
				if (comando.length<6)
					return "Operazione movecard FALLITA: pochi argomenti";
				risposta=MoveCard(comando[1],comando[2],comando[3],comando[4],comando[5]);
				break;
			case	"getcardhistory":
				if (comando.length>4)
					return "Operazione movecard FALLITA: troppi argomenti";
				if (comando.length<4)
					return "Operazione movecard FALLITA: pochi argomenti";
				risposta=GetCardHistory(comando[1],comando[2],comando[3]);
				break;	
			case	"cancelproject":
				if(comando.length>3)
					return "Operazione cancelproject FALLITA: troppi argomenti";
				if(comando.length<3)
					return "Operazione cancelproject FALLITA: pochi argomenti";
				risposta=CancelProject(comando[1],comando[2]);
				break;
			default:
				risposta= "Comando sconosciuto. Comando 'help' per le operazioni disponibili e la loro sintassi";
				break;			
		}
		return risposta;
	}

	

	private void StartServer() {
		InetSocketAddress isa=new InetSocketAddress(portaTCP);
		
		try (Selector selector=Selector.open();
				ServerSocketChannel SSChannel=ServerSocketChannel.open();
					ServerSocket SS = SSChannel.socket();){
				
			SS.bind(isa);
			SSChannel.configureBlocking(false);
			//Configuro il ServerSocketChannel in modalita' non bloccante
			
			SSChannel.register(selector, SelectionKey.OP_ACCEPT);
			
			System.out.println("ServerWORTH: Server in attesa di connessioni");
			
			while(true) {
				int num=selector.select();
				//il metodo select seleziona tra i canali registrati al selector quelli pronti
				//per almeno una delle operazioni di I/O(restituisce il nr di canali pronti)
				if(num==0) //se non ci sono canali pronti proseguo alla prossima iterazione
					continue;
				
				Set<SelectionKey> keyset=selector.selectedKeys();
				Iterator<SelectionKey> iteratore=keyset.iterator();
				
				while(iteratore.hasNext()) {
					SelectionKey keyestratta=iteratore.next();
					iteratore.remove();
						
					try {	
						if(keyestratta.isAcceptable()) {
							ServerSocketChannel server=(ServerSocketChannel)keyestratta.channel();
							SocketChannel client=server.accept();
							System.out.println("ServerWORTH: connessione accettata verso "+client);
							client.configureBlocking(false);
							client.register(selector, SelectionKey.OP_READ);
						}
						if(keyestratta.isReadable()) {
							SocketChannel client=(SocketChannel)keyestratta.channel();
							client.configureBlocking(false);
							
							boolean comandocompleto=false;
							StringBuilder sb=new StringBuilder();
							int byteletti=1;
							while(!comandocompleto) {
								ByteBuffer input=ByteBuffer.allocate(BufferDIM);
								byteletti=client.read(input);
								//System.out.println("Ho letto "+byteletti+" bytes");
								
								if(byteletti==-1){
									String failed=this.useraddress.get(client.getRemoteAddress());
									if(failed==null) {
										//client non autenticato
										System.out.println("ServerWORTH: Chiusura connessione con "+client.socket().getRemoteSocketAddress());
									}else {
										//client autenticato
										Utente u=this.GetUserByName(failed);
										System.out.println("ServerWORTH: Chiusura connessione con "+failed+client.socket().getRemoteSocketAddress());
										u.SetOffline();
										UpdateStatus(failed,"offline");
									}
									keyestratta.cancel();
									keyestratta.channel().close();
									continue;
								}					
				
								String msgricevuto=new String(input.array());
								sb.append(msgricevuto);
								if(msgricevuto.contains(terminatore)) {
									//Ho letto tutto il messaggio
									comandocompleto=true;
								}				
							}
							String a=sb.toString().trim();
							if(a.length()>0){	
								//rimuovo dalla stringa ricevuta il carattere terminatore che si trova in fondo
								String comandoricevuto=a.substring(0, a.length()-1);
								String aux=comandoricevuto.trim();
								String[] comando=aux.split(" ");
								
								System.out.println("ServerWORTH: Ho ricevuto il comando -> "+aux);
								keyestratta.attach(comando);
								keyestratta.interestOps(SelectionKey.OP_WRITE);
							}
						}
						if(keyestratta.isWritable()) {
							SocketChannel client=(SocketChannel) keyestratta.channel();
							//recupero il comando letto
							String[] comando=(String[]) keyestratta.attachment();
							
							if(comando[0].equals("exitclient")) {
								System.out.println("ServerWORTH: Chiusura connessione con "+client.socket().getRemoteSocketAddress());
								keyestratta.cancel();
								client.close();
								break;
							}else {
								//passiamo la stringa letta al metodo InterpretaComando che invochera' il metodo
								//che rispondera' alla richiesta effettuata dall'utente
								StringBuilder risposta=new StringBuilder(InterpretaComando (comando,client.getRemoteAddress()));
								risposta.append(terminatore);
								//come da protocollo di comunicazione, appendo come ultimo carattere della stringa il terminatore
								System.out.println("ServerWORTH: Invio la risposta -> "+risposta.toString());
								
								//invio della risposta
								ByteBuffer bufferout=ByteBuffer.wrap(risposta.toString().getBytes());
								client.write(bufferout);
								
								keyestratta.interestOps(SelectionKey.OP_READ);
							}
						}
					}catch(IOException e) {
						keyestratta.cancel();
						keyestratta.channel().close();
					}
				}
			}
		}catch(IOException e) {
			e.printStackTrace();
		}
		
		
				
	}
	
	private Progetto GetProjectByName(String projectname) {
		//Metodo per recuperare il progetto dato il suo nome
		for (Progetto p : listaprogetti) {
			if(p.GetName().equals(projectname)) {
				return p;
				
			}
				
		}
		return null;
	}
	
	private Utente GetUserByName(String username) {
		//Metodo per recuperare l'utente dato il suo nome
		for(Utente e : utentiregistrati) {
			if(e.GetUsername().equals(username))
				return e;
		}
		return null;
	}
	
	private void StampaStato() {
		System.out.println("ServerWORTH: Informazioni recuperate dalla cartella di backup:");
		for(Progetto p : listaprogetti) {
			System.out.println("--- PROGETTO "+p.GetName()+"---");
			System.out.println("\tMembri:");
			for(Utente e : p.GetMembri())
				System.out.println("\t\t"+e.GetUsername());
			System.out.println("\tCards:");
			for(Card c : p.GetCards()) {
				System.out.println("\t\t"+c.GetName()+"   "+c.GetStatus()+"   "+c.GetDescrizione());
			}
			System.out.println("\tIP Multicast :"+p.GetIpChat().toString());
		}
		System.out.println("---LISTA UTENTI REGISTRATI---");
		for(Utente e : utentiregistrati) {
			System.out.println(e.GetUsername());
		}
		System.out.println("--------------------");
	}
	
	public synchronized void Subscribe(ClientInterfacciaRMI client,String username) {
		//Metodo per la registrazione di un client al servizio di notifica
		if(!callbackinfo.containsKey(client)) {
			callbackinfo.put(client,username);
			
			for(Utente u : utentiregistrati) {
				//Quando un nuovo utente effettua il login, egli viene notificato sullo stato di tutti gli utenti
				//registrati per mezzo di invocazioni ripetute del metodo AggiornaUtente
				String status = u.isOnline() ? "Online" : "Offline";
				try {
					client.AggiornaUtente(u.GetUsername(), status);
				} catch (RemoteException e) {
					System.err.println("REMOTE EXCEPTION mentre aggiorno la lista dell'utente appena registrato");
					e.printStackTrace();
				}
			}
			System.out.println("ServerWORTH: "+username+" registrato al servizio notifica");
			
			//Quando un nuovo utente effettua il login, bisogna far partire dei thread associati al suo client
			//che stanno in ascolto sugli indirizzi multicast delle chat dei progetti a cui l'utente partecipa
			int counter=0;
			for(Progetto p : listaprogetti) {
				if(p.isMember(username)) {
					counter++;
					try {
						client.AvviaRicezione(p.GetIpChat(), p.GetName());
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
			}	
			System.out.println("ServerWORTH: "+username+" e' in ascolto sulle chat di "+counter+" progetti/o a cui partecipa");
		}else {
			System.out.println("ServerWORTH: "+username+" gia' registato al servizio di notifica");
		}
	}
	
	public synchronized void Unsubscribe(ClientInterfacciaRMI client, String username) {
		//Metodo per la disiscrizione al servizio di notifica
		if(callbackinfo.containsKey(client)) {
			callbackinfo.remove(client);
			System.out.println("ServerWORTH: "+username+" rimosso dal servizio di notifica");
		}else {
			System.out.println("ServerWORTH: "+username+" non registrato al servizio di notifica");
		}
	}
	
	private void UpdateStatus(String username, String status) {
		doCallBacks(username,status);
	}
	
	private synchronized void doCallBacks(String username, String status) {
		//Metodo per aggiornare tutti i client iscritti al servizio di notifica del cambiamento
		//di stato di un utente
		System.out.println("ServerWORTH: Inizio callbacks");
		Iterator<ClientInterfacciaRMI> i=callbackinfo.keySet().iterator();
		ArrayList<ClientInterfacciaRMI> failed=new ArrayList<ClientInterfacciaRMI>();
		while(i.hasNext()) {
			ClientInterfacciaRMI client=i.next();
			try {
				client.AggiornaUtente(username,status);
				System.out.println("Notificato "+callbackinfo.get(client));
			} catch (RemoteException e) {
				//Rimuovo dal servizio di notifica i client che hanno provocato una eccezione
				//callbackinfo.remove(client);
				failed.add(client);
			}
		}
		
		//Se ci sono stati client che hanno causato eccezione li rimuovo dal servizio di notifica
		for(ClientInterfacciaRMI c : failed){
			callbackinfo.remove(c);
		}
		System.out.println("ServerWORTH: Termine callbacks");
	}
	
	private InetAddress GeneraMulticastAddress(){
		//metodo per la generazione di indirizzi ip di multicast
		if(listaprogetti.size()==(256^3*16)) {
			System.out.println("ServerWORTH: non ho piu' indirizzi multicast da assegnare");
			return null;
		}
		
		//Genero casualmente i 4 interi che comporranno le parti dell'indirizzo ip di multicast
		int a,b,c,d;
		a=(int)(Math.random()*16);
		a+=224;
		b=(int)(Math.random()*256);
		c=(int)(Math.random()*256);
		d=(int)(Math.random()*256);
		String address=a+"."+b+"."+c+"."+d;
		InetAddress ia=null;
		try {
			ia = InetAddress.getByName(address);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		
		//Se esiste gia' un progetto che ha per indirizzo di chat l'ip che abbiamo
		//generato chiamiamo nuovamente la funzione affinche' ne generi un altro
		for(Progetto p : listaprogetti) {
			if(p.GetIpChat().equals(ia)) {
				return GeneraMulticastAddress();
			}
		}
		System.out.println("ServerWORTH: L'indirizzo generato per il nuovo progetto e'-> "+address);
		return ia;
	}
	
	private ClientInterfacciaRMI GetClientByName(String user) {
		//Metodo per reperire lo stub del client associato ad un certo username
		Iterator<ClientInterfacciaRMI> iterator=callbackinfo.keySet().iterator();
		while(iterator.hasNext()) {
			ClientInterfacciaRMI corrente=iterator.next();
			if(callbackinfo.get(corrente).equals(user))
				return corrente;
		}
		return null;
	}
	
	public static void main(String[] args) {
		System.out.println("__          ______  _____ _______ _    _ ");
		System.out.println("\\ \\        / / __ \\|  __ \\__   __| |  | |");
		System.out.println(" \\ \\  /\\  / / |  | | |__) | | |  | |__| |");
		System.out.println("  \\ \\/  \\/ /| |  | |  _  /  | |  |  __  |");
		System.out.println("   \\  /\\  / | |__| | | \\ \\  | |  | |  | |");
		System.out.println("    \\/  \\/   \\____/|_|  \\_\\ |_|  |_|  |_|");
		
		//Creazione di una istanza della classe Server
		Server myserver=new Server();
		
		//Ricostruzione arraylist utenti registrati
		myserver.RipristinoUtenti();
		
		//Ricostruzione arraylist progetti e relative cards, membri ecc
		myserver.RipristinoProgetti();
		
		//Stampa delle informazioni recuperate dalla cartella di backup;
		myserver.StampaStato();
		
		//Setup RMI
		myserver.PubblicazioneStubRMI();
		
		//Avvio server
		myserver.StartServer();
		
	}
}
