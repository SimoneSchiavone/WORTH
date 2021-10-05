import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/*
 * @author Simone Schiavone MAT: 582418
 */

public class Utente {
	private String username; //nome utente (si assume che nel sistema non ci possano essere utenti con lo stesso nome)
	private String password; //password associata all'utente
	private transient boolean online;
	
	public Utente(String n, String psw) {
		if(n==null)
			throw new NullPointerException("Non si puo' creare un utente con username nullo!");
		else
			this.username=n;
		if(psw==null)
			throw new NullPointerException("Non si puo' impostare una password nulla!");
		else
			this.password=hash(psw);
	}
	
	public String GetUsername() {
		return this.username;
	}
	
/*	public String GetPassword() {
		return this.password;
	}*/
	
	public boolean VerificaPassword(String pswimmessa) {
		String input=hash(pswimmessa);
		if(input.equals(this.password))
			return true;
		return false;
	}
	
	public boolean isOnline() {
		return this.online;
	}
	
	public String hash(String input) {
		StringBuilder sb=new StringBuilder();
		try {
			MessageDigest messagedigest=MessageDigest.getInstance("SHA-256");
			
			messagedigest.update(input.getBytes());
			
			byte[] array=messagedigest.digest();
						
			
			for(byte b : array) {
				sb.append(String.format("%02x", b));
			}
			
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return sb.toString();
	}
	
	public int SetOnline() {
		if(online==false) {
			online=true;
			return 1; //operazione conclusa correttamente
		}
		return 0; //Se l'utente era gia' online restituisco false per segnalare anomalia	
	}
	
	public int SetOffline() {
		if(online==true) {
			online=false;
			return 1;
		}
		return 0;
	}
	
	public void Offline(){
		online=false;
	}
}


