import java.net.InetAddress;
import java.util.ArrayList;

/*
 * @author Simone Schiavone MAT: 582418
 */


public class Progetto{
	
	private String nome; //Nome del progetto 
	private ArrayList<Card> cards; //Cards che compongono il progetto
	private ArrayList<Utente> membri; //Utenti che hanno i permessi di modificare le card e accedere ai servizi associati al progetto
	
	//Liste per la gestione degli stati in cui si puo' trovare una card durante la sua "vita"
	private ArrayList<Card> todo;
	private ArrayList<Card> inprogress;
	private ArrayList<Card> toberevised;
	private ArrayList<Card> done;
	
	//Indirizzo di multicast per la chat associata al progetto
	private InetAddress ipchat;
	
	//Metodo costruttore (Usato quando viene creato un nuovo progetto da parte di un utente)
	public Progetto(String n, Utente creatore) {
		if(n==null) 
			throw new NullPointerException("Il nome del progetto non puo' essere nullo");
		else
			this.nome=n;
		
		//Costruzione delle liste della classe
		this.cards=new ArrayList<Card>();
		this.membri=new ArrayList<Utente>();
		this.membri.add(creatore); //il creatore del progetto ne diventa subito membro
		this.todo=new ArrayList<Card>();
		this.inprogress=new ArrayList<Card>();
		this.toberevised=new ArrayList<Card>();
		this.done=new ArrayList<Card>();
		this.ipchat=null;
	}
	
	//Metodo costruttore (Usato in fase di ripristino dello stato dei progetti)
	public Progetto(String n) {
		if(n==null) 
			throw new NullPointerException("Il nome del progetto non puo' essere nullo");
		else
			this.nome=n;
		
		//Costruzione delle liste della classe
		this.cards=new ArrayList<Card>();
		this.membri=new ArrayList<Utente>();
		
		this.todo=new ArrayList<Card>();
		this.inprogress=new ArrayList<Card>();
		this.toberevised=new ArrayList<Card>();
		this.done=new ArrayList<Card>();
	}
	
	//Metodi modificatori
	
	public String MoveCard(Card card,String start,String end) {
		//Verifichiamo la correttezza degli stati
		if(!start.equals("todo") && !start.equals("inprogress") && !start.equals("toberevised") && !start.equals("done"))
			return "Operazione movecard FALLITA: lo stato "+start+" non e' supportato";
		if(!end.equals("todo") && !end.equals("inprogress") && !end.equals("toberevised") && !end.equals("done"))
			return "Operazione movecard FALLITA: lo stato "+end+" non e' supportato";
		
		//verifichiamo che effettivamente la carta si trovi nello stato indicato come quello di partenza
		if(!card.GetStatus().equals(start))
			return "Operazione movecard FALLITA: lo stato della carta "+card.GetName()+" e' "+card.GetStatus()+" e non "+start;
		
		//verifica delle possibili transizioni
		switch(start) {
			case	"todo":
				if(end.equals("inprogress")) {
					todo.remove(card);
					inprogress.add(card);
					card.SetStato("inprogress");
				}else{
					return "Operazione movecard FALLITA: non e' possibile muovere la carta nello stato "+end+" perche' e' nello stato "+start;
				}
				break;
			case	"inprogress":
				if(end.equals("toberevised")) {
					inprogress.remove(card);
					toberevised.add(card);
					card.SetStato("toberevised");
				}else {
					if(end.equals("done")) {
						inprogress.remove(card);
						done.add(card);
						card.SetStato("done");
					}else
						return "Operazione movecard FALLITA: non e' possibile muovere la carta nello stato "+end+" perche' e' nello stato "+start;
				}
				break;
			case	"toberevised":
				if(end.equals("inprogress")) {
					toberevised.remove(card);
					inprogress.add(card);
					card.SetStato("inprogress");
				}else {
					if(end.equals("done")) {
						toberevised.remove(card);
						done.add(card);
						card.SetStato("done");
					}else
						return "Operazione movecard FALLITA: non e' possibile muovere la carta nello stato "+end+" perche' e' nello stato "+start;
				}
				break;
			case	"done":
				return "Operazione movecard FALLITA: non e' possibile muovere la carta nello stato "+end+" perche' e' nello stato "+start;
		}
		
		return "Operazione movecard EFFETTUATA: la carta "+card.GetName()+" e' ora nello stato "+end;
	}
	
	public boolean AssegnaIpChat(InetAddress ip) {
		//Verifico che l'indirizzo che voglio assegnare sia indirizzo di multicast
		if(!ip.isMulticastAddress()) {
			System.out.println("ServerWORTH: l'ip "+ip+" non e' un indirizzo di multicast");
			return false;
		}
		//Verifico che il progetto non abbia gia' un indirizzo ip multicast assegnato
		if(!(ipchat==null)) {
			System.out.println("ServerWORTH: il progetto"+this.nome+" ha gia' un indirizzo di multicast per il servizio chat: "+this.ipchat.toString());
			return false;
		}
		ipchat=ip;
		return true;
	}
	
	//Metodi osservatori
	public String GetName() {
		return this.nome;
	}
	
	public ArrayList<Utente> GetMembri(){
		return this.membri;
	}
	
	public ArrayList<Card> GetCards(){
		return this.cards;
	}
	
	public boolean isMember(String username) {
		for(Utente e : membri) {
			if(e.GetUsername().equals(username))
				return true;
		}
		return false;
	}
	
	public Card existsCard(String nome) {
		for (Card c : cards) {
			if(c.GetName().equals(nome))
				return c;
		}
		return null;
	}
	
	public ArrayList<Card> GetTODOCards(){
		return this.todo;
	}
	
	public ArrayList<Card> GetINPROGRESSCards(){
		return this.inprogress;
	}
	
	public ArrayList<Card> GetTOBEREVISEDCards(){
		return this.toberevised;
	}
	
	public ArrayList<Card> GetDONECards(){
		return this.done;
	}
	
	public InetAddress GetIpChat() {
		return this.ipchat;
	}
}
