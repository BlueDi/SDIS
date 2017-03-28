package handlers;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.Queue;

import interfaces.Chunk;

public class MdrHandler implements Runnable {
	private int PEER_ID;
	private Queue<String> msgQueue = new LinkedList<String>();
	private Queue<Chunk> chunksRequests = new LinkedList<Chunk>();
	private boolean endOfFile = false;

	public MdrHandler(Queue<String> msgQueue, int id) {
		this.msgQueue = msgQueue;
		PEER_ID = id;
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()){
			analyseMessages();
		}
	}

	/**
	 * @return the chunksReceived
	 */
	public Queue<Chunk> getRequests() {
		return chunksRequests;
	}
	
	/**
	 * @return the endOfFile
	 */
	public boolean isEndOfFile() {
		return endOfFile;
	}

	/**
	 * @param endOfFile the endOfFile to set
	 */
	public void setEndOfFile(boolean endOfFile) {
		this.endOfFile = endOfFile;
	}

	/**
	 * Analisa todas as mensagens armazenadas e cria o ficheiro se recebeu o �ltimo chunk do ficheiro.
	 */
	private void analyseMessages(){
		if (!msgQueue.isEmpty()) {
			String[] msg = msgQueue.poll().split("\\s",9);

			if(checkValidMessageType(msg[0])){
				print(msg);

				analyseHeader(msg);

				byte[] body = analyseBody(msg[7]);
				Chunk chunk = new Chunk(msg[3], Integer.parseInt(msg[4]), body);

				chunksRequests.add(chunk);
				
				if(chunk.isEndOfFile()){
					setEndOfFile(true);
					createFile(msg[3]);
				}
			}
		}
	}

	/**
	 * Verifica se a mensagem recebida � v�lida.
	 * A primeira verifica��o � se o MessageType est� correto.
	 * @param messageType MessageType recebido
	 * @return True se tem o MessageType correto
	 */
	private boolean checkValidMessageType(String messageType){
		return "CHUNK".equals(messageType);
	}

	/**
	 * Analisa o cabe�alho da mensagem.
	 * TODO: Encripta��o do FileId
	 * @param msg Mensagem recebida
	 * @return true se o cabe�alho � v�lido
	 */
	private boolean analyseHeader(String[] msg){
		return "1.0".equals(msg[1]) && 
				"0xD0xA".equals(msg[5]) &&
				"0xD0xA".equals(msg[6]);
	}

	/**
	 * Analisa o body da mensagem.
	 * Converte o body de string para byte[].
	 * TODO: N�o sei se � �til.
	 * @param msg em string
	 * @return body como byte[]
	 */
	private byte[] analyseBody(String msg){
		byte[] destination = null;
		destination = msg.getBytes();
		return destination;
	}

	/**
	 * Junta dois byte arrays.
	 * @param first array para colocar no inicio do novo array
	 * @param second array para colocar no fim do novo array
	 * @return Array = first + second
	 */
	private byte[] joinArrays(byte[] first, byte[] second){
		byte[] destination = new byte[first.length + second.length];

		System.arraycopy(first, 0, destination, 0, first.length);
		System.arraycopy(second, 0, destination, first.length, second.length);

		return destination;
	}

	private void createFile(String fileId) {
		byte[] data = new byte[0];
		Path path = Paths.get(("./files/" + fileId));

		for(Chunk c: chunksRequests)
			if(c.getFileId().equals(fileId))
				data = joinArrays(data, c.getContent());

		try {
			Files.createDirectories(path.getParent());
			Files.createFile(path);
			Files.write(path, data, StandardOpenOption.APPEND);
		} catch (FileAlreadyExistsException e) {
			System.err.println("File already exists: " + e.getMessage());
		} catch (IOException e) {
			System.err.println("I/O error in mdrHandler createFile.");
		}
	}

	/**
	 * Imprime a mensagem no ecr�.
	 * @param msg Array de strings a ser imprimido
	 */
	protected void print(String[] msg) {
		System.out.println("\nReceived on MDR: ");
		for(int i = 0; i < msg.length; i++)
			System.out.print(msg[i] + "; ");
		System.out.println();
	}
}