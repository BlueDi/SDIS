package peer;

import java.io.IOException;

public class Client {
	private static String peer_ap;
	private static String operation;
	private static String operand_1;
	private static String operand_2;

	/**
	 * Peer initiator function
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException{
		if (args.length < 4) {
			System.out.println("Usage: java TestApp <peer_ap> <operation> <opnd_1> <opnd_2> ");
			return;
		}
		if(processInput(args))
			execute();
	}

	/**
	 * Verifica se os argumentos passados s�o v�lidos.
	 * TODO: resto das verifica��es dos argumentos. Ainda s� est� feito para a operation.
	 * @param args Argumentos passados na consola
	 */
	private static boolean processInput(String[] args){
		peer_ap = args[0];
		operation = args[1];
		operand_1 = args[2];
		operand_2 = args[3];

		return ("BACKUP".equals(operation)
				|| "RESTORE".equals(operation) 
				|| "DELETE".equals(operation) 
				|| "RECLAIM".equals(operation) 
				|| "STATE".equals(operation));
	}

	private static void execute(){
		Peer.execute(peer_ap, operation, operand_1, operand_2);
	}
}
