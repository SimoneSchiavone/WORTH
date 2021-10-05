import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

/*
 * @author Simone Schiavone MAT: 582418
 */

public interface ClientInterfacciaRMI extends Remote{
	//Metodo per aggiornare l'hashmap del client che contiene le associazioni username-status (online/offline).
	//Se nell'hashmap non esiste ancora una entry con chiave "username" allora viene aggiunta con valore "status"
	//altrimenti si sostituisce il vecchio valore associato con il nuovo status;
	public void AggiornaUtente(String username,String status) throws RemoteException;
	
	//Metodo per far partire un thread della classe ChatReaderWriter che resta in ascolto su un certo
	//indirizzo ip di multicast. Questo thread sara' memorizzato in una struttura dati del client in modo da 
	//poterlo reperire durante le operazioni di lettura chat, invio messaggio o interruzione.
	public void AvviaRicezione(InetAddress address,String nomeproj) throws RemoteException;
	
	//Metodo per interrompere un thread memorizzato in una struttura dati del client che e' in ascolto
	//sull'indirizzo di multicast "address".
	public boolean StopRicezione(InetAddress address) throws RemoteException;
}
