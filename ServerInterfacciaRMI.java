import java.rmi.Remote;
import java.rmi.RemoteException;

/*
 * @author Simone Schiavone MAT: 582418
 */

public interface ServerInterfacciaRMI extends Remote {
	
	//Metodo per la registrazione di un nuovo utente sulla piattaforma WORTH. Il metodo
	//restituisce il codice 0 se la registrazione e' andata a buon fine, il codice 1 se 
	//il nickUtente e' nullo, il codice 2 se la stringa password e' nulla oppure il codice
	//3 se esiste gia' un utente registrato con quel nickUtente.
	public int Register(String nickUtente, String password) throws RemoteException;
	
	//Metodo per la registrazione di un client al servizio di notifica del server
	public void Subscribe(ClientInterfacciaRMI client,String username) throws RemoteException;
	//Metodo per la disiscrizione di un client dal servizio di notifica del server
	public void Unsubscribe(ClientInterfacciaRMI client, String username) throws RemoteException;
}
