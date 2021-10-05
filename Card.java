
import java.util.ArrayList;

/*
 * @author Simone Schiavone MAT: 582418
 */

public class Card {
	private String nome; //nome della card
	private String descrizione; //breve descrizione testuale della card
	private String stato;
	private ArrayList<String> movimenticarta;
	
	public Card(String n, String descr) {
		if(n==null)
			throw new NullPointerException("Non si puo' creare una card con nome nullo!");
		this.nome=n;
		
		if(descr==null)
			throw new NullPointerException("Non si puo' creare una card con descrizione nulla");
		this.descrizione=descr;
		this.movimenticarta=new ArrayList<String>();
		this.stato="todo"; //settato di default nello stato todo alla creazione
	}
	
	public boolean SetStato(String s) {
		if(s.equals("todo")||s.equals("inprogress")||s.equals("toberevised")||s.equals("done")) {
			stato=s;
			return true;
		}
		return false;
	}
	
	public void NuovoMovimento(String descrizionemovimento) {
		movimenticarta.add(descrizionemovimento);
	}
	
	public String GetName() {
		return this.nome;
	}
	
	public String GetDescrizione() {
		return this.descrizione;
	}
	
	public String GetStatus() {
		return stato;
	}
	
	public ArrayList<String> GetMovimenti(){
		return movimenticarta;
	}
}
