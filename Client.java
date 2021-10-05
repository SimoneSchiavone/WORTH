import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteObject;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
/*
 * @author Simone Schiavone MAT: 582418
 */

public class Client extends RemoteObject implements ClientInterfacciaRMI{

	private static final long serialVersionUID = 12345678987654321L;
	private final static int portaRMI=6789;
	private final static int portaUDP=5023;
	private final static int portaTCP=9876 ;
	private final static int BufferDIM = 128;
	private final static String terminatore="%";
	private static Gson gson=new GsonBuilder().setPrettyPrinting().create();
	private String utenteassociato; //utente che ha fatto login da questo client
	private HashMap<String,String> utenti; //hash map che contiene la coppia username-online/offline
	
	//Lista dei thread ascoltatori sulle chat di progetto a cui partecipa l'utente
	//attualmente loggato
	private ArrayList<ChatReaderWriter> listaascoltatori;
	
	
	public Client() {
		super();
		this.utenteassociato=null;
		this.utenti=new HashMap<String,String>();
		this.listaascoltatori=new ArrayList<ChatReaderWriter>();
	}
	
	public static void main(String[] args) {
		System.out.println("__          ______  _____ _______ _    _ ");
		System.out.println("\\ \\        / / __ \\|  __ \\__   __| |  | |");
		System.out.println(" \\ \\  /\\  / / |  | | |__) | | |  | |__| |");
		System.out.println("  \\ \\/  \\/ /| |  | |  _  /  | |  |  __  |");
		System.out.println("   \\  /\\  / | |__| | | \\ \\  | |  | |  | |");
		System.out.println("    \\/  \\/   \\____/|_|  \\_\\ |_|  |_|  |_|");
		
		Client thisclient=new Client();
		try {
			UnicastRemoteObject.exportObject(thisclient, 0);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		Scanner scanner=new Scanner(System.in);
		
		//Setup connessione TCP
		InetSocketAddress address=new InetSocketAddress(portaTCP);
		SocketChannel SChannel=null;
		try {
			SChannel=SocketChannel.open();
			SChannel.connect(address);
			SChannel.configureBlocking(true); 
		}catch(ConnectException e) {
			System.out.println("CONNESSIONE RIFIUTATA, SERVER CHIUSO!");
			scanner.close();
			return;
		}catch (IOException e) {
			e.printStackTrace();
		}
		
		Boolean run=true;
		System.out.println("ClientWorth: Inserire l'operazione da eseguire...");
		
		while(run) {
			//lettura comando
			System.out.println("Inserire metodo...");
			String comandoletto=scanner.nextLine();
			
			//Controlliamo che l'utente non abbia inserito nella stringa di input
			//il carattere terminatore che abbiamo deciso
			if(comandoletto.contains(terminatore)) {
				System.out.println("ERRORE: non e' possibile utilizzare il carattere "+terminatore+"!");
				continue;
			}
			
			//Verifichiamo se il comando richiesto dall'utente puo' essere risolto dal client
			//senza dover contattare il server
			
			//Se si tratta del comando register si usa il meccanismo della RMI
			if(comandoletto.startsWith("register ")) {
				register(comandoletto);
				continue;
			}
			
			if(comandoletto.startsWith("readchat ")) {
				thisclient.readchat(comandoletto);
				continue;
			}
			
			if(comandoletto.startsWith("sendchatmsg ")) {
				thisclient.sendmessage(comandoletto);
				continue;
			}
			
			if(comandoletto.equals("whoisonlinehere")) {
				if(thisclient.utenteassociato==null)
					System.out.println("Su questo client nessun utente ha fatto login!");
				else
					System.out.println("Su questo client e' online "+thisclient.utenteassociato);
				continue;
			}
			
			if(comandoletto.equals("listusers")&&(!(thisclient.utenteassociato==null))) {
				//Se l'utente e' loggato, consultiamo l'hash map locale aggiornata dal 
				//server con le callbacks altrimenti inviamo la richiesta al server 
				//che inviera' come risposta una stringa json rappresentante una lista
				//di username
				System.out.println("--LISTA UTENTI REGISTRATI--");
				for(String s : thisclient.utenti.keySet()) {
					System.out.println("\t"+s+" "+thisclient.utenti.get(s));
				}
				System.out.println("--fine lista--");
				continue; 
			}
			
			if(comandoletto.equals("listonlineusers")&&(!(thisclient.utenteassociato==null))) {
				//Se l'utente e' loggato, consultiamo l'hash map locale aggiornata dal 
				//server con le callbacks altrimenti inviamo la richiesta al server 
				//che inviera' come risposta una stringa json rappresentante una lista
				//di username
				System.out.println("--LISTA UTENTI ONLINE--");
				for(String s : thisclient.utenti.keySet()) {
					if(thisclient.utenti.get(s).equals("Online"))
						System.out.println("\t"+s);
				}
				System.out.println("--fine lista--");
				continue; 
			}
			
			if(comandoletto.equals("help")) {
				Help();
				continue; 
			}
			
			//verifica e completamento del comando letto
			String comandodainviare=VerificaLogin(comandoletto,thisclient);
			if(comandodainviare.equals("error")) {
				continue; 
				//Se il comando prevede che il client si sia autenticato e questo non e' avvenuto,
				//allora non invio neanche la stringa al server (notificando al client l'errore)
				//ed aspetto il prossimo comando. Stessa cosa se tento di fare il login di 
				//un utente quando sullo stesso client ce n'e' gia' un altro loggato.
			}
			
			//Per chiudere il client voglio che l'utente attualmente loggato si scolleghi
			if(comandodainviare.equals("exitclient")) {
				if(thisclient.utenteassociato!=null) {
					System.out.println("ERRORE: Prima di chiudere il client devi fare il logout dell'utente autenticato su esso");
					continue;
				}
			}
			
			StringBuilder sb=new StringBuilder(comandodainviare);
			sb.append(terminatore);
			//come da protocollo appendo alla fine del messaggio da inviare il carattere terminatore
			
			//Invio sul canale del comando letto
			ByteBuffer bufferout=ByteBuffer.wrap(sb.toString().getBytes());
			bufferout.clear();
			try {
				SChannel.write(bufferout);
			} catch (IOException e) {
				e.printStackTrace();
			}
			bufferout.flip();
			
			if(comandodainviare.equals("exitclient")) {
				try {
					SChannel.close();
					System.out.println("ClientWORTH: Terminato");
				} catch (IOException e) {
					e.printStackTrace();
				}
				run=false;
				break;
			}
			
			
			//Ricezione della risposta da parte del server
			boolean comandocompleto=false;
			sb=new StringBuilder();
			while(!comandocompleto) {
				ByteBuffer bufferin=ByteBuffer.allocate(BufferDIM);
				try {
					SChannel.read(bufferin);
				} catch (IOException e) {
					e.printStackTrace();
					System.out.println("ERRORE:IO Exception durante la lettura");
					break;
				}
				
				String msgricevuto=new String(bufferin.array());
				sb.append(msgricevuto);
				if(msgricevuto.contains(terminatore)) {
					//Ho letto tutto il messaggio
					comandocompleto=true;
				}						
			}
			
			String a=sb.toString().trim();
			String risposta=a.substring(0, a.length()-1);
			
			//Interpretazione della risposta ricevuta dal server
			if(comandodainviare.startsWith("login ")) {
				System.out.println(risposta);
				if(risposta.startsWith("Login EFFETTUATO; Benvenuto/a")) {
					//Se il login e' avvenuto con successo ricaviamo il nome dell'
					//utente loggato in modo da associarlo al client.
					//Sappiamo che e' la quarta parola della risposta.
					thisclient.utenteassociato=risposta.split(" ")[3];
					//Iscriviamoci al servizio di notifica
					thisclient.Subscribe();
				}
				continue;
			}
			
			if(comandodainviare.startsWith("listusers")) {
				if(risposta.startsWith("[")) {
					//deserializziamo la stringa json ricevuta
					System.out.println("--LISTA UTENTI REGISTRATI--");
					JsonArray array=JsonParser.parseString(risposta).getAsJsonArray();
					for(int i=0;i<array.size();i++) {
						System.out.println("\t"+gson.fromJson(array.get(i),String.class));
					}
					System.out.println("--fine lista--");
				}else
					System.out.println(risposta);
				continue;
			}
			
			if(comandodainviare.startsWith("listonlineusers")) {
				if(risposta.startsWith("[")) {
					//deserializziamo la stringa json ricevuta
					System.out.println("--LISTA UTENTI ONLINE--");
					JsonArray array=JsonParser.parseString(risposta).getAsJsonArray();
					for(int i=0;i<array.size();i++) {
						System.out.println("\t"+gson.fromJson(array.get(i),String.class));
					}
					System.out.println("--fine lista--");
				}else
					System.out.println(risposta);
				continue;
			}
			
			if(comandodainviare.startsWith("listprojects ")) {
				if(risposta.startsWith("[")) {
					//deserializziamo la stringa json ricevuta
					System.out.println("--LISTA PROGETTI A CUI PARTECIPA "+thisclient.utenteassociato.trim()+"--");
					JsonArray array=JsonParser.parseString(risposta).getAsJsonArray();
					for(int i=0;i<array.size();i++) {
						System.out.println("\t"+gson.fromJson(array.get(i),String.class));
					}
					System.out.println("--fine lista--");
				}else
					System.out.println(risposta);
				continue;
			}
			
			if(comandodainviare.startsWith("logout ")) {
				if(risposta.startsWith("Logout EFFETTUATO;")) {
					thisclient.Unsubscribe();
					thisclient.StopRicezione(); 
					//Interrompo tutti i thread dell'utente che sono in ascolto sulle chat
					//dei progetti a cui partecipa
					thisclient.utenteassociato=null;
				}
				System.out.println(risposta);
				continue;
			}
			
			if(comandodainviare.startsWith("showmembers ")) {
				String nomeprogetto=comandodainviare.split(" ")[1];
				if(risposta.startsWith("[")) {
					//deserializziamo la stringa json ricevuta
					System.out.println("--LISTA PARTECIPANTI AL PROGETTO  "+nomeprogetto+"--");
					JsonArray array=JsonParser.parseString(risposta).getAsJsonArray();
					for(int i=0;i<array.size();i++) {
						System.out.println("\t"+gson.fromJson(array.get(i),String.class));
					}
					System.out.println("--fine lista--");
				}else
					System.out.println(risposta);
				continue;
			}
			
			if(comandodainviare.startsWith("showcards ")) {
				String nomeprogetto=comandodainviare.split(" ")[1];
				if(risposta.startsWith("[")) {
					//deserializziamo la stringa json ricevuta
					System.out.println("--LISTA CARDS DEL PROGETTO  "+nomeprogetto+"--");
					JsonArray array=JsonParser.parseString(risposta).getAsJsonArray();
					for(int i=0;i<array.size();i++) {
						System.out.println("\t"+gson.fromJson(array.get(i),String.class));
					}
					System.out.println("--fine lista--");
				}else
					System.out.println(risposta);
				continue;
			}
			
			if(comandodainviare.startsWith("addcard ")) {
				if(risposta.startsWith("Operazione addcard EFFETTUATA:")) {
					String[] str=comandodainviare.split(" ");
					StringBuilder comando=new StringBuilder();
					comando.append("sendchatmsg "+str[1]+" ho creato la carta "+str[2]);
					thisclient.sendmessage(comando.toString());
				}
				
			}
			
			if(comandodainviare.startsWith("getcardhistory ")) {
				String nomecard=comandodainviare.split(" ")[2];
				if(risposta.startsWith("[")) {
					//deserializziamo la stringa json ricevuta
					System.out.println("--MOVIMENTI CARD "+nomecard+"--");
					JsonArray array=JsonParser.parseString(risposta).getAsJsonArray();
					for(int i=0;i<array.size();i++) {
						System.out.println("\t"+gson.fromJson(array.get(i),String.class));
					}
					System.out.println("--fine lista--");
				}else
					System.out.println(risposta);
				continue;
			}
			
			if(comandodainviare.startsWith("movecard ")) {
				//Gli spostamenti di card di un certo progetto sono notificati sulla
				//chat di gruppo. Se il server mi comunica che l'operazione e' andata 
				//a buon fine sfrutto il metodo sendmessage del client
				//stesso costruendomi il comando che mi permette di inviare la notifica
				if(risposta.startsWith("Operazione movecard EFFETTUATA:")){
					String[] str=comandodainviare.split(" ");
					StringBuilder comando=new StringBuilder();
					comando.append("sendchatmsg "+str[1]+" ho spostato la carta "+str[2]+" dallo stato "+str[3]+" allo stato "+str[4]);
					thisclient.sendmessage(comando.toString());
				}
			}
			
			if(comandodainviare.startsWith("addmember ")) {
				//Le aggiunte di membri al progetto sono notificati sulla chat di gruppo.
				// Se il server mi comunica che l'operazione e' andata a buon fine sfrutto
				// il metodo sendmessage del client stesso costruendomi il comando che mi 
				//permette di inviare la notifica
				if(risposta.startsWith("Operazione addmember EFFETTUATA:")){
					String[] str=comandodainviare.split(" ");
					String comando="sendchatmsg "+str[1]+" ho aggiunto "+str[2]+" ai membri del progetto "+str[1];
					thisclient.sendmessage(comando);
				}
			}
			
			//Se non ha fatto match con gli if sopra scritti significa
			//che la stringa che ricevo non deve subire trattamento particolare
			System.out.println(risposta);
		}
		scanner.close();		
	}
	
	private void sendmessage(String comandoletto) {
		//Verifichiamo che l'utente sia loggato
		if(utenteassociato==null) {
			System.out.println("ERRORE: Per questa operazione e' necessario aver fatto il login!");
		}
		
		//Verifichiamo la lunghezza del comando inserito
		String[] comandosplittato=comandoletto.trim().split(" ");
		if(comandosplittato.length<2) {
			System.out.println("Operazione sendmessage FALLITA: pochi argomenti");
			return;
		}
		
		//Ricostruzione del messaggio da inviare
		StringBuilder msg=new StringBuilder(utenteassociato+" ha detto:");
		for(int i=2;i<comandosplittato.length;i++) {
			if(i==2)
				msg.append(comandosplittato[i]);
			else 
				msg.append(" "+comandosplittato[i]);
		}
		String message=msg.toString();
		
		for(ChatReaderWriter c : listaascoltatori) {
			if(c.GetProject().equals(comandosplittato[1])) { 
				//cerco il thread ascoltatore del progetto sulla cui chat voglio inviare un messaggio
				c.InviaMessaggio(message);
				return;				
			}
		}
		
		System.out.println("Operazione sendmessage FALLITA: il progetto "+comandosplittato[1]+" non e' stato trovato");
	}

	private void readchat(String comandoletto) {
		//Verifichiamo che l'utente sia loggato
		if(utenteassociato==null) {
			System.out.println("ERRORE: Per questa operazione e' necessario aver fatto il login!");
		}
		
		//Verifichiamo la lunghezza del comando inserito
		String[] comandosplittato=comandoletto.trim().split(" ");
		if(comandosplittato.length>2) {
			System.out.println("Operazione readchat FALLITA:: troppi argomenti");
			return;
		}
		if(comandosplittato.length<2) {
			System.out.println("Operazione readchat FALLITA: pochi argomenti");
			return;
		}
		
		//Cerco il thread associato al progetto di cui voglio leggere la chat
		for(ChatReaderWriter c : listaascoltatori) {
			if(c.GetProject().equals(comandosplittato[1])) {
				c.LeggiChat();
				return;
			}
		}
		System.out.println("Operazione readchat FALLITA: il progetto "+comandosplittato[1]+" non e' stato trovato");
	}

	private static String VerificaLogin(String comandoletto,Client c) {
		//Verifichiamo se si tratta di un metodo che necessita di aver fatto prima il login
		String[] comandosplittato=comandoletto.split(" ");
		String[] operazioniriservate= {"listprojects","createproject","addmember","showmembers","showcards","showcard","addcard","movecard","getcardhistory","cancelproject"};
		boolean loginrequired=false;
		for(String op : operazioniriservate) {
			if(op.equals(comandosplittato[0]))
				loginrequired=true;
		}
				
		//In caso sia necessaria l'autenticazione ma questa non e' stata ancora fatta notifica l'errore
		if(c.utenteassociato==null && loginrequired) {
			System.out.println("ERRORE: Per questa operazione e' necessario aver fatto il login!");
			return "error";	
		}
		
		//Aggiungiamo alla stringa del comando letto le informazioni necessarie al riconoscimento da parte del server
		if(loginrequired) {
			comandoletto=comandoletto.concat(" ");
			comandoletto=comandoletto.concat(c.utenteassociato);
		}
		
		//Se il comando era "login" verifichiamo che su questo client non vi siano altri utenti online!
		if(comandosplittato[0].equals("login")) {
			if(!(c.utenteassociato==null)) {
				System.out.println("ERRORE: C'e' gia' un utente collegato, deve essere prima scollegato");
				return "error";
			}
		}
		
		//Se il comando era "logout" verifichiamo di fare il logout dell'utente associato a questo client e non di altri
		if(comandosplittato[0].equals("logout")) {
			if(c.utenteassociato==null) {
				System.out.println("ERRORE: non ti sei autenticato");
				return "error";
			}
			
			if(!(c.utenteassociato==null)&&(comandosplittato.length>1)&&!(c.utenteassociato.equals(comandosplittato[1].trim()))) {
				System.out.println("ERRORE: Puoi fare solamente il logout di "+c.utenteassociato);
				return "error";
			}
		}
		return comandoletto.trim();
	}

	public static void register(String input) {
		String[] splittedinput=input.split(" ");
		if(splittedinput.length<3) {
			System.out.println("Operazione register FALLITA: pochi argomenti");
			return;
		}
		if(splittedinput.length>3) {
			System.out.println("Operazione register FALLITA: troppi argomenti");
			return;
		}
		//Invochiamo il metodo remoto
		try {
			Registry registry=LocateRegistry.getRegistry(portaRMI);
			Remote remoteobject=registry.lookup("SERVERWORTH");
			ServerInterfacciaRMI server=(ServerInterfacciaRMI) remoteobject;
			int result=server.Register(splittedinput[1], splittedinput[2]);
			switch(result) {
				case 0: System.out.println("Registrazione di "+splittedinput[1]+" avvenuta correttamente");
						break;
				case 1: System.out.println("Nome nullo");
						break;
				case 2: System.out.println("Password nulla");
						break;
				case 3: System.out.println(splittedinput[1]+" risulta gia' registrato alla piattaforma!");
						break;
				default:System.out.println("Errore Sconosciuto");
			}
		}catch(RemoteException e) {
			e.printStackTrace();
		} catch (NotBoundException e) {
			e.printStackTrace();
		}		
	}
	
	public synchronized void AggiornaUtente(String username, String status) throws RemoteException{
		//Metodo per l'inserimento/aggiornamento di nuovi utenti nell'hashmap locale
		if(utenti.containsKey(username))
			utenti.replace(username, status);
		else
			utenti.put(username, status);
	}
	
	public void Subscribe() {
		//Metodo per l'iscrizione al servizio di notifica del server
		try {		
			Registry registry=LocateRegistry.getRegistry(portaRMI);
			Remote remoteobject=registry.lookup("SERVERWORTH");
			ServerInterfacciaRMI server=(ServerInterfacciaRMI) remoteobject;
			server.Subscribe((ClientInterfacciaRMI)this, this.utenteassociato);
		}catch(RemoteException e) {
			e.printStackTrace();
		} catch (NotBoundException e) {
			e.printStackTrace();
		}	
	}
	
	public void Unsubscribe() {
		//Metodo per la disiscrizione al servizio di notifica del server
		try {
			Registry registry=LocateRegistry.getRegistry(portaRMI);
			Remote remoteobject=registry.lookup("SERVERWORTH");
			ServerInterfacciaRMI server=(ServerInterfacciaRMI) remoteobject;
			server.Unsubscribe((ClientInterfacciaRMI)this, this.utenteassociato);
		}catch(RemoteException e) {
			e.printStackTrace();
		} catch (NotBoundException e) {
			e.printStackTrace();
		}	
		this.utenti.clear(); //Cancello tutta la lista degli utenti registrati
	}
	
	public synchronized void AvviaRicezione(InetAddress address,String nomeproj) throws RemoteException{
		ChatReaderWriter newreader=new ChatReaderWriter(address,portaUDP,nomeproj);
		this.listaascoltatori.add(newreader);
		newreader.start();
	}
	
	public synchronized boolean StopRicezione(InetAddress address) throws RemoteException {
		//Metodo invocato per terminare un client
		for(ChatReaderWriter c : listaascoltatori) {
			if(c.GetAddress().equals(address)) {
				c.interrupt();
				c.InviaMessaggio("MESSAGGIO FITTIZIO");
				//Messaggio fittizio da inviare per "sbloccare" i thread ascoltatori
				//bloccati sull'operazione di receive;
				listaascoltatori.remove(c);
				return true;
			}
		}
		return false;
	}
	
	public synchronized void StopRicezione() {
		for(ChatReaderWriter c : listaascoltatori) {
			c.interrupt();
			c.InviaMessaggio("WorthChatService: "+this.utenteassociato+" si e' disconnesso");
			//Messaggio da inviare per "sbloccare" il thread ascoltatore
			//bloccato sull'operazione di receive;
		}
		listaascoltatori.clear(); //cancello anche la lista degli ascoltatori
	}	
	
	private static void Help() {
		System.out.println("-----Lista comandi disponibili-----");
		System.out.println("Attenzione: i comandi sono case-sensitive, digitare i comandi in minuscolo, non e' consentito l'utilizzo del carattere "+terminatore+".");
		System.out.println("Gli username, nomi card, nomi progetto non possono contenere spazi.\n");
		System.out.println("register [username] [password]: registrazione dell'utente in Worth (non sono consentiti nomi con spazi o con carattere "+terminatore+")");
		System.out.println("login [username] [password]: login dell'utente in Worth");
		System.out.println("logout [username]: logout dell'utente da Worth");
		System.out.println("listusers: visualizzazione degli utenti registrati a Worth");
		System.out.println("listonlineusers: visualizzazione degli utenti attualmente online su Worth");
		System.out.println("listprojects: visualizzazione dei progetti a cui partecipa l'utente attualmente autenticato");
		System.out.println("createproject [projectname]: creazione di un nuovo progetto in Worth con il nome 'projectname'");
		System.out.println("addmember [projectname] [nickutente]: operazione per aggiungere l'utente 'nickutente' ai partecipanti del progetto 'projectname'");
		System.out.println("showmembers [projectname]: visualizzazione dei membri del progetto 'projectname'");
		System.out.println("showcards [projectname] : visualizzazione lista delle cards associate al 'projectname'");
		System.out.println("showcard [projectname] [cardname]: visualizzazione delle informazioni (nome, stato, \n\t"
				+ "descrizione) della card di nome 'cardname' del progetto 'projectname'");
		System.out.println("addcard [projectname] [cardname] [descr_1] ..... [descr_n]: aggiunge al progetto 'projectname'\n\t"
				+ " la card di nome 'cardname' con la descrizione composta da una parola singola o da piu' parole separate da uno spazio.\n\t"
				+ "Es: 'addcard progettoreti servizio_chat implementazione del servizio chat associato ad ogni progetto'");
		System.out.println("movecard [projectname] [cardname] [listapartenza] [listadestinazione]: operazione per richiede lo\n\t"
				+ "spostamento della card di nome 'cardname' nel progetto 'projectname' dalla lista 'listapartenza' alla lista 'listadestinazione'.\n\t"
				+ "sono ammesse le seguenti transazioni: {todo->inprogress, inprogress->toberevised, inprogress->done, toberevised->done, toberevised->inprogress}");
		System.out.println("getcardhistory [projectname] [cardname]: operazione per richiedere la storia della card cioe' la sequenza di eventi di spostamento");
		System.out.println("readchat [projectname]: operazione per visualizzare i messaggi ricevuti sulla chat del progetto 'projectname'\n\t"
				+ "a partire dall'ultima invocazione dello stesso comando.");
		System.out.println("sendchatmsg [projectname] [parola-1] ..... [parola_n]: operazione per l'invio di un messaggio sulla chat del \n\t"
				+ "progetto 'projectname'. Il messaggio puo' essere composto da una singola parola o da una sequenza di piu' parole separate da uno spazio.\n\t"
				+ "Es: 'sendchatmsg progettoreti Ciao ragazzi! A che ora ci ritroviamo per lavorare insieme?'.");
		System.out.println("cancelproject [projectname]: comando per la cancellazione del progetto 'projectname'. \n\t"
				+ "l'operazione andra' a buon fine solamente se tutte le card del progetto saranno nella lista done");
		System.out.println("whoisonlinehere: comando che stampa, se il login e' stato effettuato, il nome dell'utente autenticato su questo client");
		System.out.println("exitclient: comando per far terminare le ripetute richieste di comando ('Inserire metodo...')");
	}
}
